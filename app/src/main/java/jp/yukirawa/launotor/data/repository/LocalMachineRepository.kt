package jp.yukirawa.launotor.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.domain.model.UpdateSource
import jp.yukirawa.launotor.sync.MergeResult
import jp.yukirawa.launotor.sync.SyncEntry
import jp.yukirawa.launotor.sync.SyncMergeEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.launotorDataStore: DataStore<Preferences> by preferencesDataStore(name = "launotor_timers")

class LocalMachineRepository(
    private val dataStore: DataStore<Preferences>,
    private val localOriginHash: Int = 0,
) : MachineRepository {

    override val machinesFlow: Flow<Map<String, MachineTimerRecord>> =
        dataStore.data.map { prefs -> readMachines(prefs) }

    override val settingsFlow: Flow<AppSettings> =
        dataStore.data.map { prefs -> readSettings(prefs) }

    override suspend fun getSnapshot(): Pair<Map<String, MachineTimerRecord>, AppSettings> {
        val prefs = dataStore.data.first()
        return readMachines(prefs) to readSettings(prefs)
    }

    override suspend fun startOrUpdateTimer(
        machineId: String,
        durationMinutes: Int,
        nowEpochMillis: Long,
    ): MachineTimerRecord {
        require(LaundryRoomCatalog.findById(machineId) != null) {
            "Unknown machineId: $machineId"
        }
        val clampedMinutes = durationMinutes.coerceIn(
            LaundryRoomCatalog.MIN_DURATION_MINUTES,
            LaundryRoomCatalog.MAX_DURATION_MINUTES,
        )
        val durationMillis = clampedMinutes * 60_000L
        val record = MachineTimerRecord(
            machineId = machineId,
            initialDurationMillis = durationMillis,
            endTimeEpochMillis = nowEpochMillis + durationMillis,
            updatedAtEpochMillis = nowEpochMillis,
            updateSource = UpdateSource.LOCAL,
            almostDoneNotifiedForEndTime = 0L,
            finishedNotifiedForEndTime = 0L,
            isWatchedByMe = true,
            originHash = localOriginHash and 0xFF,
        )
        dataStore.edit { prefs ->
            writeRecord(prefs, record)
        }
        return record
    }

    override suspend fun clearTimer(
        machineId: String,
        nowEpochMillis: Long,
    ): MachineTimerRecord {
        require(LaundryRoomCatalog.findById(machineId) != null) {
            "Unknown machineId: $machineId"
        }
        val cleared = MachineTimerRecord(
            machineId = machineId,
            initialDurationMillis = 0L,
            endTimeEpochMillis = 0L,
            updatedAtEpochMillis = nowEpochMillis,
            updateSource = UpdateSource.LOCAL,
            almostDoneNotifiedForEndTime = 0L,
            finishedNotifiedForEndTime = 0L,
            isWatchedByMe = false,
            originHash = localOriginHash and 0xFF,
        )
        dataStore.edit { prefs ->
            writeRecord(prefs, cleared)
        }
        return cleared
    }

    override suspend fun clearAllFinished(nowEpochMillis: Long): List<String> {
        val clearedIds = mutableListOf<String>()
        dataStore.edit { prefs ->
            val current = readMachines(prefs)
            current.values.forEach { record ->
                if (record.hasActiveSchedule && record.endTimeEpochMillis <= nowEpochMillis) {
                    clearedIds += record.machineId
                    writeRecord(
                        prefs,
                        MachineTimerRecord(
                            machineId = record.machineId,
                            initialDurationMillis = 0L,
                            endTimeEpochMillis = 0L,
                            updatedAtEpochMillis = nowEpochMillis,
                            updateSource = UpdateSource.LOCAL,
                            almostDoneNotifiedForEndTime = 0L,
                            finishedNotifiedForEndTime = 0L,
                            isWatchedByMe = false,
                            originHash = localOriginHash and 0xFF,
                        ),
                    )
                }
            }
        }
        return clearedIds
    }

    override suspend fun claimAlmostDoneNotification(
        machineId: String,
        endTimeEpochMillis: Long,
    ): Boolean {
        var claimed = false
        dataStore.edit { prefs ->
            val currentEnd = prefs[endTimeKey(machineId)] ?: 0L
            val alreadyNotifiedEnd = prefs[almostDoneNotifiedKey(machineId)] ?: 0L
            if (currentEnd == endTimeEpochMillis && alreadyNotifiedEnd != endTimeEpochMillis) {
                prefs[almostDoneNotifiedKey(machineId)] = endTimeEpochMillis
                claimed = true
            }
        }
        return claimed
    }

    override suspend fun claimFinishedNotification(
        machineId: String,
        endTimeEpochMillis: Long,
    ): Boolean {
        var claimed = false
        dataStore.edit { prefs ->
            val currentEnd = prefs[endTimeKey(machineId)] ?: 0L
            val alreadyNotifiedEnd = prefs[finishedNotifiedKey(machineId)] ?: 0L
            if (currentEnd == endTimeEpochMillis && alreadyNotifiedEnd != endTimeEpochMillis) {
                prefs[finishedNotifiedKey(machineId)] = endTimeEpochMillis
                claimed = true
            }
        }
        return claimed
    }

    override suspend fun updateAlmostDoneThreshold(minutes: Int) {
        val sanitized = if (minutes in AppSettings.THRESHOLD_OPTIONS_MINUTES) {
            minutes
        } else {
            AppSettings.DEFAULT_ALMOST_DONE_THRESHOLD_MINUTES
        }
        dataStore.edit { prefs ->
            prefs[KEY_ALMOST_DONE_THRESHOLD_MIN] = sanitized
        }
    }

    override suspend fun updateHapticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_HAPTICS_ENABLED] = enabled
        }
    }

    override suspend fun updateLiveUpdatesEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_LIVE_UPDATES_ENABLED] = enabled
        }
    }

    override suspend fun updateP2pSyncEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_P2P_SYNC_ENABLED] = enabled
        }
    }

    override suspend fun updateBackgroundP2pEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_BACKGROUND_P2P_ENABLED] = enabled
        }
    }

    override suspend fun setWatchedByMe(
        machineId: String,
        watched: Boolean,
    ): MachineTimerRecord? {
        require(LaundryRoomCatalog.findById(machineId) != null) {
            "Unknown machineId: $machineId"
        }
        var updatedRecord: MachineTimerRecord? = null
        dataStore.edit { prefs ->
            val current = readMachines(prefs)[machineId] ?: return@edit
            if (!current.hasActiveSchedule && watched) {
                return@edit
            }
            val next = current.copy(isWatchedByMe = watched)
            prefs[watchedKey(machineId)] = watched
            updatedRecord = next
        }
        return updatedRecord
    }

    override suspend fun mergeRemoteRecords(
        entries: List<SyncEntry>,
        source: UpdateSource,
        rxNowEpochMillis: Long,
    ): MergeResult {
        if (entries.isEmpty()) return MergeResult()
        var result = MergeResult()
        dataStore.edit { prefs ->
            val current = readMachines(prefs)
            result = SyncMergeEngine.mergeEntries(
                existingRecords = current,
                entries = entries,
                source = source,
                rxNowEpochMillis = rxNowEpochMillis,
            )
            for (record in result.updatedRecords) {
                writeRecord(prefs, record)
            }
        }
        return result
    }

    private fun readMachines(prefs: Preferences): Map<String, MachineTimerRecord> {
        return LaundryRoomCatalog.allMachines.associate { spec ->
            val id = spec.id
            val sourceRaw = prefs[sourceKey(id)]
            val source = sourceRaw?.let {
                runCatching { UpdateSource.valueOf(it) }.getOrNull()
            } ?: UpdateSource.LOCAL
            val initialDuration = prefs[initialDurationKey(id)] ?: 0L
            val endTime = prefs[endTimeKey(id)] ?: 0L
            val hasActiveSchedule = initialDuration > 0L && endTime > 0L
            // Migration: existing active records saved before v0.4.0 have no stored watched key.
            val isWatchedByMe = prefs[watchedKey(id)] ?: hasActiveSchedule
            id to MachineTimerRecord(
                machineId = id,
                initialDurationMillis = initialDuration,
                endTimeEpochMillis = endTime,
                updatedAtEpochMillis = prefs[updatedAtKey(id)] ?: 0L,
                updateSource = source,
                almostDoneNotifiedForEndTime = prefs[almostDoneNotifiedKey(id)] ?: 0L,
                finishedNotifiedForEndTime = prefs[finishedNotifiedKey(id)] ?: 0L,
                isWatchedByMe = isWatchedByMe,
                originHash = prefs[originHashKey(id)] ?: 0,
            )
        }
    }

    private fun readSettings(prefs: Preferences): AppSettings {
        val threshold = prefs[KEY_ALMOST_DONE_THRESHOLD_MIN]
            ?: AppSettings.DEFAULT_ALMOST_DONE_THRESHOLD_MINUTES
        return AppSettings(
            almostDoneThresholdMinutes = if (threshold in AppSettings.THRESHOLD_OPTIONS_MINUTES) {
                threshold
            } else {
                AppSettings.DEFAULT_ALMOST_DONE_THRESHOLD_MINUTES
            },
            hapticsEnabled = prefs[KEY_HAPTICS_ENABLED] ?: true,
            liveUpdatesEnabled = prefs[KEY_LIVE_UPDATES_ENABLED] ?: true,
            p2pSyncEnabled = prefs[KEY_P2P_SYNC_ENABLED] ?: true,
            backgroundP2pEnabled = prefs[KEY_BACKGROUND_P2P_ENABLED] ?: false,
        )
    }

    private fun writeRecord(
        prefs: androidx.datastore.preferences.core.MutablePreferences,
        record: MachineTimerRecord,
    ) {
        val id = record.machineId
        prefs[initialDurationKey(id)] = record.initialDurationMillis
        prefs[endTimeKey(id)] = record.endTimeEpochMillis
        prefs[updatedAtKey(id)] = record.updatedAtEpochMillis
        prefs[sourceKey(id)] = record.updateSource.name
        prefs[almostDoneNotifiedKey(id)] = record.almostDoneNotifiedForEndTime
        prefs[finishedNotifiedKey(id)] = record.finishedNotifiedForEndTime
        prefs[watchedKey(id)] = record.isWatchedByMe
        prefs[originHashKey(id)] = record.originHash and 0xFF
    }

    companion object {
        private val KEY_ALMOST_DONE_THRESHOLD_MIN = intPreferencesKey("settings_almost_done_threshold_min")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("settings_haptics_enabled")
        private val KEY_LIVE_UPDATES_ENABLED = booleanPreferencesKey("settings_live_updates_enabled")
        private val KEY_P2P_SYNC_ENABLED = booleanPreferencesKey("settings_p2p_sync_enabled")
        private val KEY_BACKGROUND_P2P_ENABLED = booleanPreferencesKey("settings_background_p2p_enabled")

        private fun initialDurationKey(id: String) = longPreferencesKey("machine_${id}_initial_ms")
        private fun endTimeKey(id: String) = longPreferencesKey("machine_${id}_end_ms")
        private fun updatedAtKey(id: String) = longPreferencesKey("machine_${id}_updated_ms")
        private fun sourceKey(id: String) = stringPreferencesKey("machine_${id}_source")
        private fun almostDoneNotifiedKey(id: String) = longPreferencesKey("machine_${id}_almost_notified_end")
        private fun finishedNotifiedKey(id: String) = longPreferencesKey("machine_${id}_finished_notified_end")
        private fun watchedKey(id: String) = booleanPreferencesKey("machine_${id}_watched")
        private fun originHashKey(id: String) = intPreferencesKey("machine_${id}_origin_hash")
    }
}
