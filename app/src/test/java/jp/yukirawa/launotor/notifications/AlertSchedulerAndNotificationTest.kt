package jp.yukirawa.launotor.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import jp.yukirawa.launotor.LaunotorApplication
import jp.yukirawa.launotor.data.repository.LocalMachineRepository
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.UpdateSource
import jp.yukirawa.launotor.sync.SyncEntry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AlertSchedulerAndNotificationTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Before
    fun resetAppRepository() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<LaunotorApplication>()
        LaundryRoomCatalog.allMachines.forEach { spec ->
            app.repository.clearTimer(spec.id)
            AlertScheduler.cancelMachineAlarms(app, spec.id)
        }
    }

    @Test
    fun alertSchedulerSchedulesAndCancelsAlarms() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = tempFolder.newFile("alarms.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { file.absolutePath.toPath() },
        )
        val repo = LocalMachineRepository(dataStore)

        val now = 1_700_000_000_000L
        repo.startOrUpdateTimer("washer_1", 20, now)
        val (records, settings) = repo.getSnapshot()

        AlertScheduler.syncAlarms(context, records, settings, now)
        val shadowAlarmManager = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))
        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)

        repo.clearTimer("washer_1", now + 1000L)
        val (clearedRecords, _) = repo.getSnapshot()
        AlertScheduler.syncAlarms(context, clearedRecords, settings, now + 1000L)
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun notificationHelperPostsAndDeduplicatesAlmostDoneAndFinishedAlerts() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowApp = Shadows.shadowOf(context.applicationContext as android.app.Application)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val file = tempFolder.newFile("notify.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { file.absolutePath.toPath() },
        )
        val repo = LocalMachineRepository(dataStore)

        val t0 = 1_700_000_000_000L
        val record = repo.startOrUpdateTimer("dryer_4", 20, t0)
        val (records1, settings) = repo.getSnapshot()

        val manager = context.getSystemService(NotificationManager::class.java)
        val shadowNm = Shadows.shadowOf(manager)

        // At 4 minutes remaining: almost-done alert should fire once
        val almostDoneNow = record.endTimeEpochMillis - 4 * 60_000L
        NotificationHelper.dispatchDueNotifications(context, repo, records1, settings, almostDoneNow)
        assertEquals(1, shadowNm.allNotifications.size)
        val almostNotif = shadowNm.getNotification(NotificationHelper.notificationIdFor("dryer_4", AlarmKind.ALMOST_DONE))
        assertNotNull(almostNotif)

        // Repeating dispatch at 3 minutes remaining must NOT post another alert
        val (records2, _) = repo.getSnapshot()
        NotificationHelper.dispatchDueNotifications(context, repo, records2, settings, almostDoneNow + 60_000L)
        assertEquals(1, shadowNm.allNotifications.size)

        // At finish time: finished notification replaces almost-done and includes "空きにする" action
        NotificationHelper.dispatchDueNotifications(context, repo, records2, settings, record.endTimeEpochMillis)
        assertNull(shadowNm.getNotification(NotificationHelper.notificationIdFor("dryer_4", AlarmKind.ALMOST_DONE)))
        val finishedNotif = shadowNm.getNotification(NotificationHelper.notificationIdFor("dryer_4", AlarmKind.FINISHED))
        assertNotNull(finishedNotif)
        assertTrue(finishedNotif.actions.any { it.title == "空きにする" })
    }

    @Test
    fun bootCompletedReceiverReschedulesPersistedAlarms() {
        val app = ApplicationProvider.getApplicationContext<LaunotorApplication>()
        val now = System.currentTimeMillis()
        runBlocking {
            app.repository.startOrUpdateTimer("washer_5", 25, now)
        }

        val shadowAlarmManager = Shadows.shadowOf(app.getSystemService(AlarmManager::class.java))
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)

        BootCompletedReceiver().onReceive(app, Intent(Intent.ACTION_BOOT_COMPLETED))

        waitUntil { shadowAlarmManager.scheduledAlarms.size == 2 }
        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun timerAlarmReceiverMarkFreeClearsMachineAndIgnoresUnknownId() {
        val app = ApplicationProvider.getApplicationContext<LaunotorApplication>()
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        val now = System.currentTimeMillis()
        runBlocking {
            app.repository.startOrUpdateTimer("dryer_2", 20, now)
            val (records, settings) = app.repository.getSnapshot()
            AlertScheduler.syncAlarms(app, records, settings, now)
        }

        val shadowAlarmManager = Shadows.shadowOf(app.getSystemService(AlarmManager::class.java))
        assertEquals(2, shadowAlarmManager.scheduledAlarms.size)

        // Unknown machineId must be ignored safely
        TimerAlarmReceiver().onReceive(
            app,
            Intent(NotificationHelper.ACTION_MARK_FREE).apply {
                putExtra(NotificationHelper.EXTRA_MACHINE_ID, "unknown_machine_99")
            },
        )
        waitUntil {
            val (records, _) = runBlocking { app.repository.getSnapshot() }
            records["dryer_2"]?.hasActiveSchedule == true
        }

        // Valid machineId clears timer & cancels alarms
        TimerAlarmReceiver().onReceive(
            app,
            Intent(NotificationHelper.ACTION_MARK_FREE).apply {
                putExtra(NotificationHelper.EXTRA_MACHINE_ID, "dryer_2")
            },
        )
        waitUntil {
            val (records, _) = runBlocking { app.repository.getSnapshot() }
            records["dryer_2"]?.hasActiveSchedule == false && shadowAlarmManager.scheduledAlarms.isEmpty()
        }
        val (afterRecords, _) = runBlocking { app.repository.getSnapshot() }
        assertFalse(afterRecords["dryer_2"]!!.hasActiveSchedule)
        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
    }

    @Test
    fun peerSharedMachineTriggersAlertsOnlyAfterUserTogglesWatchOn() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val shadowApp = Shadows.shadowOf(context.applicationContext as android.app.Application)
        shadowApp.grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)

        val file = tempFolder.newFile("p2p_notify.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.createWithPath(
            scope = backgroundScope,
            produceFile = { file.absolutePath.toPath() },
        )
        val repo = LocalMachineRepository(dataStore)

        val now = 1_700_000_000_000L
        // Peer-shared dryer_1 (index 4) with 4 minutes remaining -> almost done window, but unwatched!
        val dryer1Index = LaundryRoomCatalog.allMachines.indexOfFirst { it.id == "dryer_1" }
        repo.mergeRemoteRecords(
            entries = listOf(
                SyncEntry(
                    machineIndex = dryer1Index,
                    isActive = true,
                    isClearedTombstone = false,
                    remainingSeconds = 4 * 60,
                    initialDurationMinutes = 30,
                    ageSeconds = 5,
                    originHash = 88,
                ),
            ),
            source = UpdateSource.P2P_LAN,
            rxNowEpochMillis = now,
        )

        val manager = context.getSystemService(NotificationManager::class.java)
        val shadowNm = Shadows.shadowOf(manager)
        val shadowAlarmManager = Shadows.shadowOf(context.getSystemService(AlarmManager::class.java))

        val (unwatchedRecords, settings) = repo.getSnapshot()
        AlertScheduler.syncAlarms(context, unwatchedRecords, settings, now)
        NotificationHelper.dispatchDueNotifications(context, repo, unwatchedRecords, settings, now)

        assertEquals(0, shadowAlarmManager.scheduledAlarms.size)
        assertEquals(0, shadowNm.allNotifications.size)
        assertNull(TimerNotificationPlanner.buildLiveUpdateModel(unwatchedRecords, settings, now))

        // User enables "通知を受け取る"
        repo.setWatchedByMe("dryer_1", true)
        val (watchedRecords, _) = repo.getSnapshot()
        AlertScheduler.syncAlarms(context, watchedRecords, settings, now)
        NotificationHelper.dispatchDueNotifications(context, repo, watchedRecords, settings, now)

        assertEquals(1, shadowAlarmManager.scheduledAlarms.size)
        assertEquals(1, shadowNm.allNotifications.size)
        assertNotNull(TimerNotificationPlanner.buildLiveUpdateModel(watchedRecords, settings, now))
    }

    private fun waitUntil(timeoutMs: Long = 3_000L, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(25L)
        }
        assertTrue("Timed out waiting for condition", predicate())
    }
}
