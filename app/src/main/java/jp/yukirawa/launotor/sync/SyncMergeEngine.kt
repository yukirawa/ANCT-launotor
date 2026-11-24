package jp.yukirawa.launotor.sync

import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.domain.model.UpdateSource
import kotlin.math.abs

/**
 * Result of merging an incoming P2P sync packet against local repository records.
 */
data class MergeResult(
    val updatedRecords: List<MachineTimerRecord> = emptyList(),
    val clearedWatchedMachineIds: List<String> = emptyList(),
) {
    val mergedCount: Int get() = updatedRecords.size
}

/**
 * Clock-skew-immune Relative-Time Last-Write-Wins (LWW) merge engine.
 *
 * Converts incoming relative (`ageSeconds`, `remainingSeconds`) into local epoch timestamps
 * using the receiver's clock `rxNowEpochMillis`, enforces a 3-second jitter guard with
 * deterministic `originHash` tie-breaking, and strictly protects local `isWatchedByMe` state.
 */
object SyncMergeEngine {
    const val JITTER_TOLERANCE_MS = 3_000L
    const val ALERT_RESET_DRIFT_THRESHOLD_MS = 60_000L

    fun merge(
        existingRecords: Map<String, MachineTimerRecord>,
        packet: SyncPacket,
        localSenderShortId: Int,
        source: UpdateSource,
        rxNowEpochMillis: Long,
    ): MergeResult {
        if (packet.senderShortId == (localSenderShortId and 0xFFFF)) {
            return MergeResult()
        }
        return mergeEntries(
            existingRecords = existingRecords,
            entries = packet.entries,
            source = source,
            rxNowEpochMillis = rxNowEpochMillis,
        )
    }

    fun mergeEntries(
        existingRecords: Map<String, MachineTimerRecord>,
        entries: List<SyncEntry>,
        source: UpdateSource,
        rxNowEpochMillis: Long,
    ): MergeResult {
        if (entries.isEmpty()) return MergeResult()

        val updated = mutableListOf<MachineTimerRecord>()
        val clearedWatched = mutableListOf<String>()

        for (entry in entries) {
            val spec = LaundryRoomCatalog.allMachines.getOrNull(entry.machineIndex) ?: continue
            if (!entry.isActive && !entry.isClearedTombstone) continue
            if (entry.ageSeconds >= 0xFFFF) continue

            if (entry.isClearedTombstone && entry.ageSeconds > SyncPayloadCodec.TOMBSTONE_MAX_AGE_SECONDS) {
                continue
            }
            if (
                entry.isActive &&
                entry.remainingSeconds == 0 &&
                entry.ageSeconds > SyncPayloadCodec.EXPIRED_FINISHED_MAX_AGE_SECONDS
            ) {
                continue
            }

            val incomingUpdatedAt = (rxNowEpochMillis - entry.ageSeconds * 1000L).coerceAtLeast(1L)
            val local = existingRecords[spec.id]
            val incomingOriginHash = entry.originHash and 0xFF

            if (local != null && local.updatedAtEpochMillis > 0L) {
                val delta = incomingUpdatedAt - local.updatedAtEpochMillis
                if (delta < -JITTER_TOLERANCE_MS) {
                    continue
                }
                if (abs(delta) <= JITTER_TOLERANCE_MS) {
                    val localOrigin = local.originHash and 0xFF
                    // Suppress multi-hop echoes and quantization jitter; break simultaneous ties deterministically.
                    if (incomingOriginHash <= localOrigin) {
                        continue
                    }
                }
            }

            if (entry.isClearedTombstone) {
                if (local?.isWatchedByMe == true) {
                    clearedWatched += spec.id
                }
                updated += MachineTimerRecord(
                    machineId = spec.id,
                    initialDurationMillis = 0L,
                    endTimeEpochMillis = 0L,
                    updatedAtEpochMillis = incomingUpdatedAt,
                    updateSource = source,
                    almostDoneNotifiedForEndTime = 0L,
                    finishedNotifiedForEndTime = 0L,
                    isWatchedByMe = false,
                    originHash = incomingOriginHash,
                )
            } else {
                val clampedMinutes = entry.initialDurationMinutes.coerceIn(
                    LaundryRoomCatalog.MIN_DURATION_MINUTES,
                    LaundryRoomCatalog.MAX_DURATION_MINUTES,
                )
                val incomingInitialDuration = clampedMinutes * 60_000L
                val incomingEndTime = rxNowEpochMillis + entry.remainingSeconds.coerceAtLeast(0) * 1000L

                val keepWatched = local?.isWatchedByMe == true
                val resetAlertDedup = !keepWatched ||
                    abs(incomingEndTime - local.endTimeEpochMillis) > ALERT_RESET_DRIFT_THRESHOLD_MS

                val nextAlmostNotified = when {
                    resetAlertDedup -> 0L
                    local.almostDoneNotifiedForEndTime > 0L &&
                        local.almostDoneNotifiedForEndTime == local.endTimeEpochMillis -> incomingEndTime
                    else -> 0L
                }
                val nextFinishedNotified = when {
                    resetAlertDedup -> 0L
                    local.finishedNotifiedForEndTime > 0L &&
                        local.finishedNotifiedForEndTime == local.endTimeEpochMillis -> incomingEndTime
                    else -> 0L
                }

                updated += MachineTimerRecord(
                    machineId = spec.id,
                    initialDurationMillis = incomingInitialDuration,
                    endTimeEpochMillis = incomingEndTime,
                    updatedAtEpochMillis = incomingUpdatedAt,
                    updateSource = source,
                    almostDoneNotifiedForEndTime = nextAlmostNotified,
                    finishedNotifiedForEndTime = nextFinishedNotified,
                    isWatchedByMe = keepWatched,
                    originHash = incomingOriginHash,
                )
            }
        }

        return MergeResult(
            updatedRecords = updated,
            clearedWatchedMachineIds = clearedWatched,
        )
    }
}
