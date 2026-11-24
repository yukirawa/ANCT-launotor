package jp.yukirawa.launotor.sync

import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.domain.model.UpdateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPayloadCodecTest {

    @Test
    fun full14MachineLanSnapshotEncodesToExact101BytesAndRoundTrips() {
        val now = 1_700_000_000_000L
        val records = mapOf(
            "washer_1" to MachineTimerRecord(
                machineId = "washer_1",
                initialDurationMillis = 35 * 60_000L,
                endTimeEpochMillis = now + 20 * 60_000L + 500L,
                updatedAtEpochMillis = now - 15 * 60_000L,
                originHash = 0x4A,
                isWatchedByMe = true,
            ),
            "dryer_4" to MachineTimerRecord(
                machineId = "dryer_4",
                initialDurationMillis = 30 * 60_000L,
                endTimeEpochMillis = now - 5_000L, // finished, not cleared
                updatedAtEpochMillis = now - 30 * 60_000L,
                originHash = 0x99,
            ),
            "washer_8" to MachineTimerRecord(
                machineId = "washer_8",
                initialDurationMillis = 0L,
                endTimeEpochMillis = 0L,
                updatedAtEpochMillis = now - 120_000L, // cleared tombstone
                originHash = 0xF1,
            ),
        )

        val packet = SyncPayloadCodec.buildLanSnapshotPacket(
            records = records,
            senderShortId = 0xABCD,
            nowEpochMillis = now,
        )
        val encoded = SyncPayloadCodec.encode(packet)
        assertEquals(SyncPayloadCodec.FULL_LAN_PACKET_SIZE, encoded.size)
        assertEquals(101, encoded.size)

        val decoded = SyncPayloadCodec.decode(encoded)
        assertNotNull(decoded)
        decoded!!
        assertEquals(SyncPayloadCodec.PROTOCOL_VERSION, decoded.version)
        assertEquals(0xABCD, decoded.senderShortId)
        assertEquals(14, decoded.entries.size)

        val w1 = decoded.entries[0] // washer_1 is index 0
        assertTrue(w1.isActive)
        assertFalse(w1.isClearedTombstone)
        assertEquals(20 * 60 + 1, w1.remainingSeconds)
        assertEquals(35, w1.initialDurationMinutes)
        assertEquals(15 * 60, w1.ageSeconds)
        assertEquals(0x4A, w1.originHash)

        val d4Index = LaundryRoomCatalog.allMachines.indexOfFirst { it.id == "dryer_4" }
        val d4 = decoded.entries[d4Index]
        assertTrue(d4.isActive)
        assertFalse(d4.isClearedTombstone)
        assertEquals(0, d4.remainingSeconds)
        assertEquals(30, d4.initialDurationMinutes)
        assertEquals(0x99, d4.originHash)

        val w8Index = LaundryRoomCatalog.allMachines.indexOfFirst { it.id == "washer_8" }
        val w8 = decoded.entries[w8Index]
        assertFalse(w8.isActive)
        assertTrue(w8.isClearedTombstone)
        assertEquals(0, w8.remainingSeconds)
        assertEquals(120, w8.ageSeconds)
        assertEquals(0xF1, w8.originHash)

        // Unused machine should remain non-active, non-tombstone
        val w2 = decoded.entries[1]
        assertFalse(w2.isActive)
        assertFalse(w2.isClearedTombstone)
    }

    @Test
    fun bleFramesFitWithin24BytesAndPrioritizeRunningAndRecentTombstones() {
        val now = 1_700_000_000_000L
        val records = mapOf(
            // Old tombstone (2 hours ago) -> lower priority
            "washer_1" to MachineTimerRecord(
                machineId = "washer_1",
                updatedAtEpochMillis = now - 2 * 3600_000L,
                originHash = 1,
            ),
            // Very old tombstone (7 hours ago) -> excluded (> 6h)
            "washer_2" to MachineTimerRecord(
                machineId = "washer_2",
                updatedAtEpochMillis = now - 7 * 3600_000L,
                originHash = 2,
            ),
            // Finished 20 minutes ago -> medium priority
            "dryer_1" to MachineTimerRecord(
                machineId = "dryer_1",
                initialDurationMillis = 30 * 60_000L,
                endTimeEpochMillis = now - 20 * 60_000L,
                updatedAtEpochMillis = now - 50 * 60_000L,
                originHash = 3,
            ),
            // Running, updated 10s ago -> top priority
            "washer_3" to MachineTimerRecord(
                machineId = "washer_3",
                initialDurationMillis = 30 * 60_000L,
                endTimeEpochMillis = now + 29 * 60_000L,
                updatedAtEpochMillis = now - 10_000L,
                originHash = 4,
            ),
            // Recent tombstone (30s ago) -> top priority
            "dryer_2" to MachineTimerRecord(
                machineId = "dryer_2",
                updatedAtEpochMillis = now - 30_000L,
                originHash = 5,
            ),
            // Running, updated 60s ago -> top priority
            "washer_5" to MachineTimerRecord(
                machineId = "washer_5",
                initialDurationMillis = 40 * 60_000L,
                endTimeEpochMillis = now + 39 * 60_000L,
                updatedAtEpochMillis = now - 60_000L,
                originHash = 6,
            ),
        )

        val frames = SyncPayloadCodec.buildBleFrames(
            records = records,
            senderShortId = 0x1234,
            nowEpochMillis = now,
        )

        // 5 eligible entries (washer_2 excluded >6h) -> 2 frames (3 entries + 2 entries)
        assertEquals(2, frames.size)
        frames.forEach { frame ->
            assertTrue("Frame ${frame.size}B exceeds 24B legacy BLE limit", frame.size <= 24)
        }

        val frame0 = SyncPayloadCodec.decode(frames[0])!!
        val frame1 = SyncPayloadCodec.decode(frames[1])!!
        assertEquals(3, frame0.entries.size)
        assertEquals(2, frame1.entries.size)

        val frame0Ids = frame0.entries.map { LaundryRoomCatalog.allMachines[it.machineIndex].id }
        assertEquals(listOf("washer_3", "dryer_2", "washer_5"), frame0Ids)

        val frame1Ids = frame1.entries.map { LaundryRoomCatalog.allMachines[it.machineIndex].id }
        assertEquals(listOf("dryer_1", "washer_1"), frame1Ids)
    }

    @Test
    fun bleFramesEmptyWhenAllMachinesAreIdleWithNoRecentTombstones() {
        val now = 1_700_000_000_000L
        val frames = SyncPayloadCodec.buildBleFrames(
            records = emptyMap(),
            senderShortId = 0x1111,
            nowEpochMillis = now,
        )
        assertTrue(frames.isEmpty())
    }

    @Test
    fun decodeRejectsMalformedAndCorruptedPackets() {
        assertNull(SyncPayloadCodec.decode(byteArrayOf()))
        assertNull(SyncPayloadCodec.decode(byteArrayOf(0x01, 0x02))) // too short

        // Wrong version
        val wrongVersion = byteArrayOf(0x02, 0x00, 0x01, 0x10, 0x00, 0x0A, 0x1E, 0x00, 0x01, 0x01)
        assertNull(SyncPayloadCodec.decode(wrongVersion))

        // Invalid length (not 3 + 7*k)
        val badLength = byteArrayOf(0x01, 0x00, 0x01, 0x10, 0x00)
        assertNull(SyncPayloadCodec.decode(badLength))

        // Invalid machine index 14 (0x0E)
        val invalidIndex = byteArrayOf(0x01, 0x00, 0x01, 0x1E, 0x00, 0x0A, 0x1E, 0x00, 0x01, 0x01)
        assertNull(SyncPayloadCodec.decode(invalidIndex))
    }

    @Test
    fun unsignedHighBitUint16AndUint8FieldsRoundTripWithoutSignExtension() {
        val packet = SyncPacket(
            version = SyncPayloadCodec.PROTOCOL_VERSION,
            senderShortId = 0xCAFE,
            entries = listOf(
                SyncEntry(
                    machineIndex = 13,
                    isActive = true,
                    isClearedTombstone = false,
                    remainingSeconds = 0x9ABC,
                    initialDurationMinutes = 180,
                    ageSeconds = 0xBEEF,
                    originHash = 0xFF,
                ),
            ),
        )
        val encoded = SyncPayloadCodec.encode(packet)
        val decoded = SyncPayloadCodec.decode(encoded)!!
        assertEquals(0xCAFE, decoded.senderShortId)
        val entry = decoded.entries.single()
        assertEquals(13, entry.machineIndex)
        assertEquals(0x9ABC, entry.remainingSeconds)
        assertEquals(180, entry.initialDurationMinutes)
        assertEquals(0xBEEF, entry.ageSeconds)
        assertEquals(0xFF, entry.originHash)
    }
}
