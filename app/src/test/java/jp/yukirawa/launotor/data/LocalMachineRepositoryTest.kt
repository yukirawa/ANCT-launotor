package jp.yukirawa.launotor.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import jp.yukirawa.launotor.data.repository.LocalMachineRepository
import jp.yukirawa.launotor.domain.model.UpdateSource
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
    fun timersAndSettingsPersistAcrossRepositoryInstances() = runTest {
        val file = tempFolder.newFile("test_launotor.preferences_pb")
        val dataStore1 = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { file.absolutePath.toPath() },
        )
        val repo1 = LocalMachineRepository(dataStore1)

        val now = 1_700_000_000_000L
        val started = repo1.startOrUpdateTimer("washer_3", 25, now)
        assertEquals(25 * 60_000L, started.initialDurationMillis)
        assertEquals(now + 25 * 60_000L, started.endTimeEpochMillis)
        assertEquals(UpdateSource.LOCAL, started.updateSource)

        repo1.markAlmostDoneNotified("washer_3", started.endTimeEpochMillis)
        repo1.updateAlmostDoneThreshold(10)
        repo1.updateHapticsEnabled(false)
        repo1.updateLiveUpdatesEnabled(false)

        val (records, settings) = repo1.getSnapshot()
        assertEquals(14, records.size)
        val w3 = records["washer_3"]!!
        assertEquals(started.endTimeEpochMillis, w3.endTimeEpochMillis)
        assertEquals(started.endTimeEpochMillis, w3.almostDoneNotifiedForEndTime)
        assertEquals(10, settings.almostDoneThresholdMinutes)
        assertFalse(settings.hapticsEnabled)
        assertFalse(settings.liveUpdatesEnabled)
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
