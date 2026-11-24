package jp.yukirawa.launotor.notifications

import android.Manifest
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import jp.yukirawa.launotor.MainActivity
import jp.yukirawa.launotor.R
import jp.yukirawa.launotor.data.repository.MachineRepository
import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineTimerRecord

object NotificationHelper {
    const val ACTION_MARK_FREE = "jp.yukirawa.launotor.ACTION_MARK_FREE"
    const val EXTRA_MACHINE_ID = "extra_machine_id"

    fun hasPostNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    suspend fun dispatchDueNotifications(
        context: Context,
        repository: MachineRepository,
        records: Map<String, MachineTimerRecord>,
        settings: AppSettings,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        NotificationChannels.ensureCreated(context)
        val dueList = TimerNotificationPlanner.computeDueNotifications(
            records = records,
            settings = settings,
            nowEpochMillis = nowEpochMillis,
        )
        if (dueList.isEmpty()) return

        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val canNotify = hasPostNotificationPermission(context)

        for (due in dueList) {
            when (due.kind) {
                AlarmKind.ALMOST_DONE -> {
                    val claimed = repository.claimAlmostDoneNotification(
                        machineId = due.machine.id,
                        endTimeEpochMillis = due.endTimeEpochMillis,
                    )
                    if (claimed && canNotify) {
                        postAlmostDoneNotification(context, manager, due)
                    }
                }

                AlarmKind.FINISHED -> {
                    val claimed = repository.claimFinishedNotification(
                        machineId = due.machine.id,
                        endTimeEpochMillis = due.endTimeEpochMillis,
                    )
                    if (claimed) {
                        // Cancel prior almost-done notification for this machine when finished.
                        manager.cancel(notificationIdFor(due.machine.id, AlarmKind.ALMOST_DONE))
                        if (canNotify) {
                            postFinishedNotification(context, manager, due)
                        }
                    }
                }
            }
        }
    }

    fun cancelMachineNotifications(context: Context, machineId: String) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.cancel(notificationIdFor(machineId, AlarmKind.ALMOST_DONE))
        manager.cancel(notificationIdFor(machineId, AlarmKind.FINISHED))
    }

    private fun postAlmostDoneNotification(
        context: Context,
        manager: NotificationManager,
        due: DueNotificationSpec,
    ) {
        val contentIntent = createMainActivityPendingIntent(
            context = context,
            requestCode = notificationIdFor(due.machine.id, AlarmKind.ALMOST_DONE),
            machineId = due.machine.id,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_ALMOST_DONE)
            .setSmallIcon(R.drawable.ic_stat_laundry)
            .setContentTitle(due.title)
            .setContentText(due.body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        manager.notify(notificationIdFor(due.machine.id, AlarmKind.ALMOST_DONE), notification)
    }

    private fun postFinishedNotification(
        context: Context,
        manager: NotificationManager,
        due: DueNotificationSpec,
    ) {
        val notifId = notificationIdFor(due.machine.id, AlarmKind.FINISHED)
        val contentIntent = createMainActivityPendingIntent(
            context = context,
            requestCode = notifId,
            machineId = due.machine.id,
        )
        val markFreeIntent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = ACTION_MARK_FREE
            putExtra(EXTRA_MACHINE_ID, due.machine.id)
        }
        val markFreePendingIntent = PendingIntent.getBroadcast(
            context,
            notifId + 500,
            markFreeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, NotificationChannels.CHANNEL_FINISHED)
            .setSmallIcon(R.drawable.ic_stat_laundry)
            .setContentTitle(due.title)
            .setContentText(due.body)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                R.drawable.ic_stat_laundry,
                "空きにする",
                markFreePendingIntent,
            )
            .build()

        manager.notify(notifId, notification)
    }

    private fun createMainActivityPendingIntent(
        context: Context,
        requestCode: Int,
        machineId: String,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_MACHINE_ID, machineId)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun buildServiceIdleNotification(context: Context): android.app.Notification {
        return NotificationCompat.Builder(context, NotificationChannels.CHANNEL_LIVE_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_laundry)
            .setContentTitle("洗濯乾燥機時間監視")
            .setContentText("監視終了")
            .setSilent(true)
            .setOngoing(true)
            .build()
    }

    fun buildBackgroundP2pNotification(context: Context): android.app.Notification {
        val contentIntent = createMainActivityPendingIntent(
            context = context,
            requestCode = 1002,
            machineId = "",
        )
        return NotificationCompat.Builder(context, NotificationChannels.CHANNEL_LIVE_MONITOR)
            .setSmallIcon(R.drawable.ic_stat_laundry)
            .setContentTitle("近くの端末と自動共有中")
            .setContentText("空き状況をバックグラウンドで同期しています")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setRequestPromotedOngoing(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun notificationIdFor(machineId: String, kind: AlarmKind): Int {
        val index = LaundryRoomCatalog.allMachines.indexOfFirst { it.id == machineId }.coerceAtLeast(0)
        return 3000 + index * 10 + kind.ordinal
    }
}
