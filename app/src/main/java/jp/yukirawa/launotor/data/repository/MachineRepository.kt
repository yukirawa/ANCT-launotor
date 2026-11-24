package jp.yukirawa.launotor.data.repository

import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.domain.model.UpdateSource
import jp.yukirawa.launotor.sync.MergeResult
import jp.yukirawa.launotor.sync.SyncEntry
import kotlinx.coroutines.flow.Flow

/**
 * Repository abstraction for machine timer states and monitoring settings.
 * Each record carries [MachineTimerRecord.updatedAtEpochMillis] and [MachineTimerRecord.updateSource]
 * so future synchronization backends can merge cleanly without changing UI or timer logic.
 */
interface MachineRepository {
    val machinesFlow: Flow<Map<String, MachineTimerRecord>>
    val settingsFlow: Flow<AppSettings>

    suspend fun getSnapshot(): Pair<Map<String, MachineTimerRecord>, AppSettings>

    suspend fun startOrUpdateTimer(
        machineId: String,
        durationMinutes: Int,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): MachineTimerRecord

    suspend fun clearTimer(
        machineId: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): MachineTimerRecord

    suspend fun clearAllFinished(
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): List<String>

    suspend fun claimAlmostDoneNotification(
        machineId: String,
        endTimeEpochMillis: Long,
    ): Boolean

    suspend fun claimFinishedNotification(
        machineId: String,
        endTimeEpochMillis: Long,
    ): Boolean

    suspend fun updateAlmostDoneThreshold(minutes: Int)

    suspend fun updateHapticsEnabled(enabled: Boolean)

    suspend fun updateLiveUpdatesEnabled(enabled: Boolean)

    suspend fun updateP2pSyncEnabled(enabled: Boolean)

    suspend fun updateBackgroundP2pEnabled(enabled: Boolean)

    suspend fun setWatchedByMe(
        machineId: String,
        watched: Boolean,
    ): MachineTimerRecord?

    suspend fun mergeRemoteRecords(
        entries: List<SyncEntry>,
        source: UpdateSource,
        rxNowEpochMillis: Long = System.currentTimeMillis(),
    ): MergeResult
}
