package jp.yukirawa.launotor.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.yukirawa.launotor.LaunotorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        val app = context.applicationContext as LaunotorApplication
        val repository = app.repository

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                NotificationChannels.ensureCreated(context)
                val now = System.currentTimeMillis()
                val (records, settings) = repository.getSnapshot()
                NotificationHelper.dispatchDueNotifications(
                    context = context,
                    repository = repository,
                    records = records,
                    settings = settings,
                    nowEpochMillis = now,
                )
                val (updatedRecords, updatedSettings) = repository.getSnapshot()
                AlertScheduler.syncAlarms(context, updatedRecords, updatedSettings, now)
                MonitoringForegroundService.syncWithTimers(
                    context = context,
                    hasRunningTimers = TimerNotificationPlanner.hasRunningTimers(updatedRecords, now),
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
