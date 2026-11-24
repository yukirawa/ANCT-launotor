package jp.yukirawa.launotor.sync

import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.domain.model.UpdateSource
import jp.yukirawa.launotor.notifications.TimerNotificationPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncMergeEngineTest {

    @Test
    fun clockSkewedSenderReconstructsExactRemainingTimeOnReceiver() {
        val senderClock = 1_700_000_000_000L
        val receiverClock = senderClock + 10 * 60_000L // +10 minutes clock skew on receiver!

        val senderRecord = MachineTimerRecord(
            machineId = "washer_2",
            initialDurationMillis = 35 * 60_000L,
            endTimeEpochMillis = senderClock + 22 * 60_000L,
            updatedAtEpochMillis = senderClock - 3 * 60_000L,
            originHash = 42,
        )

        val packet = SyncPayloadCodec.decode(
            SyncPayloadCodec.encode(
                SyncPayloadCodec.buildLanSnapshotPacket(
                    records = mapOf("washer_2" to senderRecord),
                    senderShortId = 0x2000,
                    nowEpochMillis = senderClock,
                ),
            ),
        )!!

        val result = SyncMergeEngine.merge(
            existingRecords = emptyMap(),
            packet = packet,
            localSenderShortId = 0x1000,
            source = UpdateSource.P2P_LAN,
            rxNowEpochMillis = receiverClock,
        )

        assertEquals(1, result.mergedCount)
        val merged = result.updatedRecords.single()
        assertEquals("washer_2", merged.machineId)
        assertEquals(receiverClock + 22 * 60_000L, merged.endTimeEpochMillis)
        assertEquals(receiverClock - 3 * 60_000L, merged.updatedAtEpochMillis)
        assertEquals(35 * 60_000L, merged.initialDurationMillis)
        assertEquals(UpdateSource.P2P_LAN, merged.updateSource)
        assertFalse(merged.isWatchedByMe)
    }

    @Test
    fun multiHopPropagationPreservesRemainingTimeAndUpdatesAge() {
        val t0 = 1_700_000_000_000L
        // Device A starts dryer_3 for 30 min at t0
        val recordA = MachineTimerRecord(
            machineId = "dryer_3",
            initialDurationMillis = 30 * 60_000L,
            endTimeEpochMillis = t0 + 30 * 60_000L,
            updatedAtEpochMillis = t0,
            originHash = 77,
        )

        // At t0 + 5 min, Device B receives via BLE from A
        val tB = t0 + 5 * 60_000L
        val framesA = SyncPayloadCodec.buildBleFrames(mapOf("dryer_3" to recordA), 0xAAA1, tB)
        val packetFromA = SyncPayloadCodec.decode(framesA.single())!!
        val mergeB = SyncMergeEngine.merge(emptyMap(), packetFromA, 0xBBB2, UpdateSource.P2P_BLE, tB)
        val recordB = mergeB.updatedRecords.single()
        assertEquals(tB + 25 * 60_000L, recordB.endTimeEpochMillis)
        assertEquals(t0, recordB.updatedAtEpochMillis)

        // At t0 + 12 min (with -4 min skew on C!), Device C receives re-gossiped BLE frame from B
        val tC = t0 + 12 * 60_000L - 4 * 60_000L
        val bActualSendTime = t0 + 12 * 60_000L
        val framesB = SyncPayloadCodec.buildBleFrames(mapOf("dryer_3" to recordB), 0xBBB2, bActualSendTime)
        val packetFromB = SyncPayloadCodec.decode(framesB.single())!!
        val mergeC = SyncMergeEngine.merge(emptyMap(), packetFromB, 0xCCC3, UpdateSource.P2P_BLE, tC)
        val recordC = mergeC.updatedRecords.single()

        // Remaining time on C is exact 18 minutes from C's local clock!
        assertEquals(tC + 18 * 60_000L, recordC.endTimeEpochMillis)
        assertFalse(recordC.isWatchedByMe)
        assertEquals(77, recordC.originHash)
    }

    @Test
    fun ignoresSelfEchoAndAppliesJitterGuardWithDeterministicOriginTieBreak() {
        val now = 1_700_000_000_000L
        val dryer1Index = LaundryRoomCatalog.allMachines.indexOfFirst { it.id == "dryer_1" }

        val entrySameOrigin = SyncEntry(
            machineIndex = dryer1Index,
            isActive = true,
            isClearedTombstone = false,
            remainingSeconds = 1200,
            initialDurationMinutes = 20,
            ageSeconds = 10,
            originHash = 50,
        )

        // Self-echo must be ignored completely
        val selfEcho = SyncMergeEngine.merge(
            existingRecords = emptyMap(),
            packet = SyncPacket(senderShortId = 0x1234, entries = listOf(entrySameOrigin)),
            localSenderShortId = 0x1234,
            source = UpdateSource.P2P_BLE,
            rxNowEpochMillis = now,
        )
        assertEquals(0, selfEcho.mergedCount)

        val local = MachineTimerRecord(
            machineId = "dryer_1",
            initialDurationMillis = 20 * 60_000L,
            endTimeEpochMillis = now + 1200_000L,
            updatedAtEpochMillis = now - 10_000L,
            originHash = 50,
        )

        // Within 3s jitter window with same originHash -> ignored (prevents loops)
        val jitterSameOrigin = entrySameOrigin.copy(ageSeconds = 8) // delta = +2000ms
        assertEquals(
            0,
            SyncMergeEngine.mergeEntries(
                existingRecords = mapOf("dryer_1" to local),
                entries = listOf(jitterSameOrigin),
                source = UpdateSource.P2P_BLE,
                rxNowEpochMillis = now,
            ).mergedCount,
        )

        // Within 3s jitter window with LOWER originHash -> ignored
        val jitterLowerOrigin = entrySameOrigin.copy(ageSeconds = 8, originHash = 30)
        assertEquals(
            0,
            SyncMergeEngine.mergeEntries(
                existingRecords = mapOf("dryer_1" to local),
                entries = listOf(jitterLowerOrigin),
                source = UpdateSource.P2P_BLE,
                rxNowEpochMillis = now,
            ).mergedCount,
        )

        // Within 3s jitter window with HIGHER originHash -> deterministic winner
        val jitterHigherOrigin = entrySameOrigin.copy(ageSeconds = 9, originHash = 80, remainingSeconds = 900)
        val tieBreakWin = SyncMergeEngine.mergeEntries(
            existingRecords = mapOf("dryer_1" to local),
            entries = listOf(jitterHigherOrigin),
            source = UpdateSource.P2P_BLE,
            rxNowEpochMillis = now,
        )
        assertEquals(1, tieBreakWin.mergedCount)
        assertEquals(80, tieBreakWin.updatedRecords.single().originHash)
        assertEquals(now + 900_000L, tieBreakWin.updatedRecords.single().endTimeEpochMillis)

        // Strictly older (>3s older) even with higher originHash -> ignored
        val strictlyOlder = entrySameOrigin.copy(ageSeconds = 20, originHash = 200)
        assertEquals(
            0,
            SyncMergeEngine.mergeEntries(
                existingRecords = mapOf("dryer_1" to local),
                entries = listOf(strictlyOlder),
                source = UpdateSource.P2P_LAN,
                rxNowEpochMillis = now,
            ).mergedCount,
        )

        // Strictly newer (>3s newer) even with lower originHash -> accepted
        val strictlyNewer = entrySameOrigin.copy(ageSeconds = 4, originHash = 5)
        assertEquals(
            1,
            SyncMergeEngine.mergeEntries(
                existingRecords = mapOf("dryer_1" to local),
                entries = listOf(strictlyNewer),
                source = UpdateSource.P2P_LAN,
                rxNowEpochMillis = now,
            ).mergedCount,
        )
    }

    @Test
    fun protectsWatchedFlagPreservesDedupWithin60sAndClearsOnTombstone() {
        val now = 1_700_000_000_000L
        val w4Index = LaundryRoomCatalog.allMachines.indexOfFirst { it.id == "washer_4" }
        val endTime = now + 10 * 60_000L

        val watchedLocal = MachineTimerRecord(
            machineId = "washer_4",
            initialDurationMillis = 30 * 60_000L,
            endTimeEpochMillis = endTime,
            updatedAtEpochMillis = now - 60_000L,
            almostDoneNotifiedForEndTime = endTime,
            finishedNotifiedForEndTime = 0L,
            isWatchedByMe = true,
            originHash = 10,
        )

        // Minor BLE quantization shift (12s) -> preserves watch AND re-anchors almostDoneNotifiedForEndTime
        val smallShift = SyncEntry(
            machineIndex = w4Index,
            isActive = true,
            isClearedTombstone = false,
            remainingSeconds = 10 * 60 + 12,
            initialDurationMinutes = 30,
            ageSeconds = 5,
            originHash = 20,
        )
        val mergedSmall = SyncMergeEngine.mergeEntries(
            existingRecords = mapOf("washer_4" to watchedLocal),
            entries = listOf(smallShift),
            source = UpdateSource.P2P_BLE,
            rxNowEpochMillis = now,
        ).updatedRecords.single()
        assertTrue(mergedSmall.isWatchedByMe)
        assertEquals(mergedSmall.endTimeEpochMillis, mergedSmall.almostDoneNotifiedForEndTime)
        assertTrue(
            TimerNotificationPlanner.computeDueNotifications(
                records = mapOf("washer_4" to mergedSmall),
                settings = AppSettings(almostDoneThresholdMinutes = 15),
                nowEpochMillis = now,
            ).isEmpty(),
        )

        // Significant update (>60s) -> preserves watch, resets notified timestamps
        val largeShift = smallShift.copy(remainingSeconds = 25 * 60, ageSeconds = 2)
        val mergedLarge = SyncMergeEngine.mergeEntries(
            existingRecords = mapOf("washer_4" to watchedLocal),
            entries = listOf(largeShift),
            source = UpdateSource.P2P_LAN,
            rxNowEpochMillis = now,
        ).updatedRecords.single()
        assertTrue(mergedLarge.isWatchedByMe)
        assertEquals(0L, mergedLarge.almostDoneNotifiedForEndTime)
        assertEquals(0L, mergedLarge.finishedNotifiedForEndTime)

        // Remote cleared tombstone -> clears isWatchedByMe and reports clearedWatchedMachineIds
        val tombstone = SyncEntry(
            machineIndex = w4Index,
            isActive = false,
            isClearedTombstone = true,
            remainingSeconds = 0,
            initialDurationMinutes = 0,
            ageSeconds = 1,
            originHash = 30,
        )
        val tombstoneResult = SyncMergeEngine.mergeEntries(
            existingRecords = mapOf("washer_4" to mergedLarge),
            entries = listOf(tombstone),
            source = UpdateSource.P2P_BLE,
            rxNowEpochMillis = now,
        )
        assertEquals(listOf("washer_4"), tombstoneResult.clearedWatchedMachineIds)
        val cleared = tombstoneResult.updatedRecords.single()
        assertFalse(cleared.isWatchedByMe)
        assertFalse(cleared.hasActiveSchedule)
    }

    @Test
    fun ignoresStaleTombstonesAndExpiredNoise() {
        val now = 1_700_000_000_000L
        val staleTombstone = SyncEntry(
            machineIndex = 0,
            isActive = false,
            isClearedTombstone = true,
            remainingSeconds = 0,
            initialDurationMinutes = 0,
            ageSeconds = SyncPayloadCodec.TOMBSTONE_MAX_AGE_SECONDS + 1,
            originHash = 1,
        )
        val staleFinished = SyncEntry(
            machineIndex = 1,
            isActive = true,
            isClearedTombstone = false,
            remainingSeconds = 0,
            initialDurationMinutes = 30,
            ageSeconds = SyncPayloadCodec.EXPIRED_FINISHED_MAX_AGE_SECONDS + 1,
            originHash = 1,
        )

        val res = SyncMergeEngine.mergeEntries(
            existingRecords = emptyMap(),
            entries = listOf(staleTombstone, staleFinished),
            source = UpdateSource.P2P_LAN,
            rxNowEpochMillis = now,
        )
        assertEquals(0, res.mergedCount)
    }

    @Test
    fun fiveHopSubSecondRingDoesNotCauseEchoLoopOrRemainingTimeCreep() {
        val t0 = 1_700_000_000_000L
        val originRecord = MachineTimerRecord(
            machineId = "washer_1",
            initialDurationMillis = 30 * 60_000L,
            endTimeEpochMillis = t0 + 30 * 60_000L,
            updatedAtEpochMillis = t0,
            originHash = 0x42,
            isWatchedByMe = true,
        )

        var currentRecord = originRecord
        var currentTime = t0
        val senderIds = listOf(0x1001, 0x1002, 0x1003, 0x1004, 0x1005)

        // Relay across 5 hops at 800ms per hop (total 4000ms > 3000ms jitter window)
        for (i in 0 until 5) {
            val nextTime = currentTime + 800L
            val frame = SyncPayloadCodec.buildBleFrames(
                records = mapOf("washer_1" to currentRecord),
                senderShortId = senderIds[i],
                nowEpochMillis = nextTime,
            ).single()
            val packet = SyncPayloadCodec.decode(frame)!!
            val receiverSenderId = if (i < 4) senderIds[i + 1] else senderIds[0]

            if (i < 4) {
                val merge = SyncMergeEngine.merge(
                    existingRecords = emptyMap(),
                    packet = packet,
                    localSenderShortId = receiverSenderId,
                    source = UpdateSource.P2P_BLE,
                    rxNowEpochMillis = nextTime,
                )
                assertEquals(1, merge.mergedCount)
                currentRecord = merge.updatedRecords.single()
                assertTrue(
                    "Remaining time crept up on hop $i",
                    currentRecord.endTimeEpochMillis <= originRecord.endTimeEpochMillis + 1_000L,
                )
                assertTrue(
                    "Reconstructed updatedAt drifted forward on hop $i",
                    currentRecord.updatedAtEpochMillis <= t0,
                )
            } else {
                // 5th hop returns to origin (0x1001): origin MUST reject its own echo!
                val echoMerge = SyncMergeEngine.merge(
                    existingRecords = mapOf("washer_1" to originRecord),
                    packet = packet,
                    localSenderShortId = senderIds[0],
                    source = UpdateSource.P2P_BLE,
                    rxNowEpochMillis = nextTime,
                )
                assertEquals(0, echoMerge.mergedCount)

                // Even if relayed from a different senderShortId, origin rejects because updatedAt <= t0
                val relayedEchoFromPeer = packet.copy(senderShortId = 0x9999)
                val relayedMerge = SyncMergeEngine.merge(
                    existingRecords = mapOf("washer_1" to originRecord),
                    packet = relayedEchoFromPeer,
                    localSenderShortId = senderIds[0],
                    source = UpdateSource.P2P_BLE,
                    rxNowEpochMillis = nextTime,
                )
                assertEquals(0, relayedMerge.mergedCount)
            }
            currentTime = nextTime
        }
    }
}
