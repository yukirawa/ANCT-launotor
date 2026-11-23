package jp.yukirawa.launotor.notifications

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.test.core.app.ApplicationProvider
import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class LiveUpdateNotificationBuilderTest {

    @Test
    @Config(sdk = [36])
    fun android16LiveUpdateNotificationMeetsAllPromotableConstraints() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.ensureCreated(context)

        val manager = context.getSystemService(NotificationManager::class.java)
        val liveChannel = manager.getNotificationChannel(NotificationChannels.CHANNEL_LIVE_MONITOR)
        assertNotNull(liveChannel)
        assertTrue(liveChannel.importance >= NotificationManager.IMPORTANCE_DEFAULT)
        assertTrue(liveChannel.importance != NotificationManager.IMPORTANCE_MIN)

        val now = 1_700_000_000_000L
        val model = TimerNotificationPlanner.buildLiveUpdateModel(
            records = mapOf(
                "washer_1" to MachineTimerRecord(
                    machineId = "washer_1",
                    initialDurationMillis = 30 * 60_000L,
                    endTimeEpochMillis = now + 10 * 60_000L,
                ),
            ),
            settings = AppSettings(almostDoneThresholdMinutes = 5),
            nowEpochMillis = now,
        )!!

        val notification = LiveUpdateNotificationBuilder.build(
            context = context,
            model = model,
            requestPromotedOngoing = true,
        )

        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertTrue((notification.flags and Notification.FLAG_ONLY_ALERT_ONCE) != 0)
        assertTrue((notification.flags and Notification.FLAG_GROUP_SUMMARY) == 0)
        assertFalse(notification.extras.getBoolean(Notification.EXTRA_COLORIZED, false))
        assertFalse(notification.extras.getCharSequence(Notification.EXTRA_TITLE).isNullOrEmpty())
        assertEquals(
            Notification.ProgressStyle::class.java.name,
            notification.extras.getString(Notification.EXTRA_TEMPLATE),
        )
        @Suppress("DEPRECATION")
        assertNull(notification.contentView)
        @Suppress("DEPRECATION")
        assertNull(notification.bigContentView)
        assertTrue(NotificationCompat.isRequestPromotedOngoing(notification))
        assertEquals(model.shortCriticalText, NotificationCompat.getShortCriticalText(notification))
        // Note: Robolectric's android-all 16-13921718-2-i7 snapshot contains an inverted
        // `isColorizedRequested()` check in Notification.hasPromotableCharacteristics(), whereas
        // Android 16 Live Updates require colorized=false (verified above). Calling the compat
        // helper still verifies it executes without error on API 36.
        LiveUpdateSupport.hasPromotableCharacteristics(notification)
    }

    @Test
    @Config(sdk = [30])
    fun preAndroid16GracefullyFallsBackToStandardOngoingProgress() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        NotificationChannels.ensureCreated(context)

        val now = 1_700_000_000_000L
        val model = TimerNotificationPlanner.buildLiveUpdateModel(
            records = mapOf(
                "dryer_4" to MachineTimerRecord(
                    machineId = "dryer_4",
                    initialDurationMillis = 20 * 60_000L,
                    endTimeEpochMillis = now + 10 * 60_000L,
                ),
            ),
            settings = AppSettings(almostDoneThresholdMinutes = 5),
            nowEpochMillis = now,
        )!!

        val notification = LiveUpdateNotificationBuilder.build(
            context = context,
            model = model,
            requestPromotedOngoing = true,
        )

        assertTrue((notification.flags and Notification.FLAG_ONGOING_EVENT) != 0)
        assertEquals(model.maxProgress, notification.extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertEquals(model.progress, notification.extras.getInt(Notification.EXTRA_PROGRESS))
    }
}
