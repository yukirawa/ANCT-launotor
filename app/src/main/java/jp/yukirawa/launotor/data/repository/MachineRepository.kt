package jp.yukirawa.launotor.data.repository

import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
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

    suspend fun markAlmostDoneNotified(
        machineId: String,
        endTimeEpochMillis: Long,
    )

    suspend fun markFinishedNotified(
        machineId: String,
        endTimeEpochMillis: Long,
    )

    suspend fun updateAlmostDoneThreshold(minutes: Int)

    suspend fun updateHapticsEnabled(enabled: Boolean)

    suspend fun updateLiveUpdatesEnabled(enabled: Boolean)
}
