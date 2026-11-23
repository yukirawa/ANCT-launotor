package jp.yukirawa.launotor.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import jp.yukirawa.launotor.data.repository.LocalMachineRepository
import kotlinx.coroutines.test.runTest
import okio.Path.Companion.toPath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
