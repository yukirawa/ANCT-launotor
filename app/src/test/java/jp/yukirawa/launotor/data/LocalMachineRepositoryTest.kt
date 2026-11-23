package jp.yukirawa.launotor.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import jp.yukirawa.launotor.data.repository.LocalMachineRepository
import jp.yukirawa.launotor.domain.model.UpdateSource
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
    }
}
