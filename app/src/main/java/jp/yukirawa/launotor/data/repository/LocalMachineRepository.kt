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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

val Context.launotorDataStore: DataStore<Preferences> by preferencesDataStore(name = "launotor_timers")

class LocalMachineRepository(
    private val dataStore: DataStore<Preferences>,
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
                        ),
                    )
                }
            }
        }
        return clearedIds
    }

    override suspend fun markAlmostDoneNotified(
        machineId: String,
        endTimeEpochMillis: Long,
    ) {
        dataStore.edit { prefs ->
            val currentEnd = prefs[endTimeKey(machineId)] ?: 0L
            if (currentEnd == endTimeEpochMillis) {
                prefs[almostDoneNotifiedKey(machineId)] = endTimeEpochMillis
            }
        }
    }

    override suspend fun markFinishedNotified(
        machineId: String,
        endTimeEpochMillis: Long,
    ) {
        dataStore.edit { prefs ->
            val currentEnd = prefs[endTimeKey(machineId)] ?: 0L
            if (currentEnd == endTimeEpochMillis) {
                prefs[finishedNotifiedKey(machineId)] = endTimeEpochMillis
            }
        }
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

    private fun readMachines(prefs: Preferences): Map<String, MachineTimerRecord> {
        return LaundryRoomCatalog.allMachines.associate { spec ->
            val id = spec.id
            val sourceRaw = prefs[sourceKey(id)]
            val source = sourceRaw?.let {
                runCatching { UpdateSource.valueOf(it) }.getOrNull()
            } ?: UpdateSource.LOCAL
            id to MachineTimerRecord(
                machineId = id,
                initialDurationMillis = prefs[initialDurationKey(id)] ?: 0L,
                endTimeEpochMillis = prefs[endTimeKey(id)] ?: 0L,
                updatedAtEpochMillis = prefs[updatedAtKey(id)] ?: 0L,
                updateSource = source,
                almostDoneNotifiedForEndTime = prefs[almostDoneNotifiedKey(id)] ?: 0L,
                finishedNotifiedForEndTime = prefs[finishedNotifiedKey(id)] ?: 0L,
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
    }

    companion object {
        private val KEY_ALMOST_DONE_THRESHOLD_MIN = intPreferencesKey("settings_almost_done_threshold_min")
        private val KEY_HAPTICS_ENABLED = booleanPreferencesKey("settings_haptics_enabled")
        private val KEY_LIVE_UPDATES_ENABLED = booleanPreferencesKey("settings_live_updates_enabled")

        private fun initialDurationKey(id: String) = longPreferencesKey("machine_${id}_initial_ms")
        private fun endTimeKey(id: String) = longPreferencesKey("machine_${id}_end_ms")
        private fun updatedAtKey(id: String) = longPreferencesKey("machine_${id}_updated_ms")
        private fun sourceKey(id: String) = stringPreferencesKey("machine_${id}_source")
        private fun almostDoneNotifiedKey(id: String) = longPreferencesKey("machine_${id}_almost_notified_end")
        private fun finishedNotifiedKey(id: String) = longPreferencesKey("machine_${id}_finished_notified_end")
    }
}
