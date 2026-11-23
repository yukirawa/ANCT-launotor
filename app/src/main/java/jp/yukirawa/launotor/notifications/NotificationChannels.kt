package jp.yukirawa.launotor.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import jp.yukirawa.launotor.R

object NotificationChannels {
    const val CHANNEL_LIVE_MONITOR = "launotor_live_monitor"
    const val CHANNEL_ALMOST_DONE = "launotor_almost_done"
    const val CHANNEL_FINISHED = "launotor_finished"

    fun ensureCreated(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        // Live Updates require channel importance != IMPORTANCE_MIN (>= IMPORTANCE_DEFAULT recommended).
        val liveChannel = NotificationChannel(
            CHANNEL_LIVE_MONITOR,
            context.getString(R.string.channel_live_monitor_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_live_monitor_desc)
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }

        val almostDoneChannel = NotificationChannel(
            CHANNEL_ALMOST_DONE,
            context.getString(R.string.channel_almost_done_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = context.getString(R.string.channel_almost_done_desc)
            enableVibration(true)
        }

        val finishedChannel = NotificationChannel(
            CHANNEL_FINISHED,
            context.getString(R.string.channel_finished_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_finished_desc)
            enableVibration(true)
            enableLights(true)
        }

        manager.createNotificationChannels(
            listOf(liveChannel, almostDoneChannel, finishedChannel),
        )
    }
}
