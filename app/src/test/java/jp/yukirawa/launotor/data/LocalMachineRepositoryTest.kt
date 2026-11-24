package jp.yukirawa.launotor.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import jp.yukirawa.launotor.data.repository.LocalMachineRepository
import jp.yukirawa.launotor.domain.model.UpdateSource
import jp.yukirawa.launotor.sync.SyncEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalMachineRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun timersAndSettingsPersistAcrossDistinctDataStoreInstances() = runTest {
        val file = tempFolder.newFile("test_launotor.preferences_pb")
        val scope1 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStore1 = PreferenceDataStoreFactory.createWithPath(
            scope = scope1,
            produceFile = { file.absolutePath.toPath() },
        )
        val repo1 = LocalMachineRepository(dataStore1)

        val now = 1_700_000_000_000L
        val started = repo1.startOrUpdateTimer("washer_3", 25, now)
        assertEquals(25 * 60_000L, started.initialDurationMillis)
        assertEquals(now + 25 * 60_000L, started.endTimeEpochMillis)
        assertEquals(UpdateSource.LOCAL, started.updateSource)

        assertTrue(repo1.claimAlmostDoneNotification("washer_3", started.endTimeEpochMillis))
        repo1.updateAlmostDoneThreshold(10)
        repo1.updateHapticsEnabled(false)
        repo1.updateLiveUpdatesEnabled(false)

        // Tear down first DataStore scope and reload from a brand new DataStore + repository instance.
        scope1.cancel()

        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val dataStore2 = PreferenceDataStoreFactory.createWithPath(
                scope = scope2,
                produceFile = { file.absolutePath.toPath() },
            )
            val repo2 = LocalMachineRepository(dataStore2)

            val (records, settings) = repo2.getSnapshot()
            assertEquals(14, records.size)
            val w3 = records["washer_3"]!!
            assertEquals(started.endTimeEpochMillis, w3.endTimeEpochMillis)
            assertEquals(started.endTimeEpochMillis, w3.almostDoneNotifiedForEndTime)
            assertEquals(10, settings.almostDoneThresholdMinutes)
            assertFalse(settings.hapticsEnabled)
            assertFalse(settings.liveUpdatesEnabled)
        } finally {
            scope2.cancel()
        }
    }

    @Test
    fun atomicClaimNotificationAllowsOnlySingleWinnerUnderConcurrency() = runTest {
        val file = tempFolder.newFile("test_atomic_claim.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { file.absolutePath.toPath() },
        )
        val repo = LocalMachineRepository(dataStore)

        val now = 1_700_000_000_000L
        val started = repo.startOrUpdateTimer("dryer_1", 20, now)

        val almostClaims = (1..10).map {
            async { repo.claimAlmostDoneNotification("dryer_1", started.endTimeEpochMillis) }
        }.awaitAll()
        assertEquals(1, almostClaims.count { it })

        val finishedClaims = (1..10).map {
            async { repo.claimFinishedNotification("dryer_1", started.endTimeEpochMillis) }
        }.awaitAll()
        assertEquals(1, finishedClaims.count { it })

        // Claiming with a stale endTime must return false.
        assertFalse(repo.claimAlmostDoneNotification("dryer_1", started.endTimeEpochMillis + 1000L))
    }

    @Test
    fun clearTimerAndClearAllFinishedResetOnlyTargetMachines() = runTest {
        val file = tempFolder.newFile("test_clear.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { file.absolutePath.toPath() },
        )
        val repo = LocalMachineRepository(dataStore)

        val t0 = 1_000_000L
        repo.startOrUpdateTimer("washer_1", 10, t0)
        repo.startOrUpdateTimer("dryer_2", 30, t0)

        val cleared = repo.clearAllFinished(nowEpochMillis = t0 + 15 * 60_000L)
        assertEquals(listOf("washer_1"), cleared)

        val (snapshot, _) = repo.getSnapshot()
        assertFalse(snapshot["washer_1"]!!.hasActiveSchedule)
        assertTrue(snapshot["dryer_2"]!!.hasActiveSchedule)

        repo.clearTimer("dryer_2", t0 + 16 * 60_000L)
        val (afterClear, _) = repo.getSnapshot()
        assertFalse(afterClear["dryer_2"]!!.hasActiveSchedule)
        assertFalse(afterClear["dryer_2"]!!.isWatchedByMe)
    }

    @Test
    fun p2pMergeWatchToggleAndSettingsPersistProperly() = runTest {
        val file = tempFolder.newFile("test_p2p_repo.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { file.absolutePath.toPath() },
        )
        val repo = LocalMachineRepository(dataStore, localOriginHash = 0x55)

        val now = 1_700_000_000_000L
        val localStarted = repo.startOrUpdateTimer("washer_1", 25, now)
        assertTrue(localStarted.isWatchedByMe)
        assertEquals(0x55, localStarted.originHash)

        // Merge remote update for washer_2 (index 1) -> must NOT auto-watch
        val mergeRes = repo.mergeRemoteRecords(
            entries = listOf(
                SyncEntry(
                    machineIndex = 1,
                    isActive = true,
                    isClearedTombstone = false,
                    remainingSeconds = 18 * 60,
                    initialDurationMinutes = 30,
                    ageSeconds = 5,
                    originHash = 0x99,
                ),
            ),
            source = UpdateSource.P2P_BLE,
            rxNowEpochMillis = now,
        )
        assertEquals(1, mergeRes.mergedCount)

        val (afterMerge, defaultSettings) = repo.getSnapshot()
        assertTrue(defaultSettings.p2pSyncEnabled)
        assertFalse(defaultSettings.backgroundP2pEnabled)
        val w2 = afterMerge["washer_2"]!!
        assertTrue(w2.hasActiveSchedule)
        assertFalse(w2.isWatchedByMe)
        assertEquals(UpdateSource.P2P_BLE, w2.updateSource)

        // User taps "通知を受け取る" on peer-shared washer_2
        val watchedW2 = repo.setWatchedByMe("washer_2", true)!!
        assertTrue(watchedW2.isWatchedByMe)

        // Idle machine cannot be watched
        assertEquals(null, repo.setWatchedByMe("washer_3", true))

        repo.updateP2pSyncEnabled(false)
        repo.updateBackgroundP2pEnabled(true)
        val (finalRecords, finalSettings) = repo.getSnapshot()
        assertTrue(finalRecords["washer_2"]!!.isWatchedByMe)
        assertFalse(finalSettings.p2pSyncEnabled)
        assertTrue(finalSettings.backgroundP2pEnabled)
    }

    @Test
    fun legacyActiveRecordsWithoutWatchedKeyMigrateToWatchedTrue() = runTest {
        val file = tempFolder.newFile("test_migration.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { file.absolutePath.toPath() },
        )
        val now = 1_700_000_000_000L
        // Simulate v0.3.0 DataStore keys without machine_*_watched
        dataStore.edit { prefs ->
            prefs[longPreferencesKey("machine_washer_4_initial_ms")] = 30 * 60_000L
            prefs[longPreferencesKey("machine_washer_4_end_ms")] = now + 20 * 60_000L
            prefs[longPreferencesKey("machine_washer_4_updated_ms")] = now - 10 * 60_000L
        }

        val repo = LocalMachineRepository(dataStore)
        val (records, _) = repo.getSnapshot()
        assertTrue(records["washer_4"]!!.isWatchedByMe)
        assertFalse(records["washer_1"]!!.isWatchedByMe)
    }
}
