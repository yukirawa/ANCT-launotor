package jp.yukirawa.launotor.notifications

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.graphics.toColorInt
import jp.yukirawa.launotor.MainActivity
import jp.yukirawa.launotor.R
import jp.yukirawa.launotor.domain.model.MachineStatus

object LiveUpdateSupport {
    fun isAndroid16OrHigher(): Boolean = Build.VERSION.SDK_INT >= 36

    fun canPostPromotedNotifications(context: Context): Boolean {
        if (!isAndroid16OrHigher()) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canPostPromotedNotifications()
    }

    fun createPromotionSettingsIntent(context: Context): Intent {
        return if (isAndroid16OrHigher()) {
            Intent(Settings.ACTION_APP_NOTIFICATION_PROMOTION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            }
        }
    }

    fun hasPromotableCharacteristics(notification: Notification): Boolean {
        return NotificationCompat.hasPromotableCharacteristics(notification)
    }
}

object LiveUpdateNotificationBuilder {
    const val LIVE_NOTIFICATION_ID = 1001

    private val COLOR_WASHER_ELAPSED = "#0D7A96".toColorInt()
    private val COLOR_DRYER_ELAPSED = "#D96B27".toColorInt()
    private val COLOR_ALMOST_DONE_ZONE = "#F59E0B".toColorInt()
    private val COLOR_POINT_THRESHOLD = "#F59E0B".toColorInt()

    fun build(
        context: Context,
        model: LiveUpdateRenderModel,
        requestPromotedOngoing: Boolean,
    ): Notification {
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val elapsedColor = when (model.primaryMachine.type) {
            jp.yukirawa.launotor.domain.model.MachineType.WASHER -> COLOR_WASHER_ELAPSED
            jp.yukirawa.launotor.domain.model.MachineType.DRYER -> COLOR_DRYER_ELAPSED
        }

        val progressStyle = NotificationCompat.ProgressStyle()
            .setStyledByProgress(true)
            .setProgress(model.progress)

        val pointPos = model.almostDonePointPosition
        if (pointPos != null && pointPos in 1 until model.maxProgress) {
            progressStyle.setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(pointPos).setColor(elapsedColor),
                    NotificationCompat.ProgressStyle.Segment(model.maxProgress - pointPos)
                        .setColor(COLOR_ALMOST_DONE_ZONE),
                ),
            )
            progressStyle.addProgressPoint(
                NotificationCompat.ProgressStyle.Point(pointPos).setColor(COLOR_POINT_THRESHOLD),
            )
        } else {
            progressStyle.setProgressSegments(
                listOf(
                    NotificationCompat.ProgressStyle.Segment(model.maxProgress).setColor(elapsedColor),
                ),
            )
        }

        // Strict Android 16 Live Updates compliance:
        // - setOngoing(true)
        // - setRequestPromotedOngoing(true)
        // - setShortCriticalText(...)
        // - setContentTitle(...)
        // - setOnlyAlertOnce(true)
        // - ProgressStyle (falls back to standard setProgress on API 26-35 inside NotificationCompat)
        // - No RemoteViews, no setColorized(true), no setGroupSummary(true)
        val builder = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_LIVE_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_laundry)
            .setContentTitle(model.contentTitle)
            .setContentText(model.contentText)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setRequestPromotedOngoing(requestPromotedOngoing)
            .setShortCriticalText(model.shortCriticalText)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setSilent(true)
            .setStyle(progressStyle)

        if (model.subText != null && model.primaryStatus == MachineStatus.ALMOST_DONE) {
            builder.setSubText("まもなく空き")
        } else if (model.subText != null) {
            builder.setSubText(model.subText)
        }

        return builder.build()
    }
}
