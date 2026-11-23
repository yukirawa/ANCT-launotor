package jp.yukirawa.launotor.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import jp.yukirawa.launotor.LaunotorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class TimerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as LaunotorApplication
        val repository = app.repository

        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (intent.action == NotificationHelper.ACTION_MARK_FREE) {
                    val machineId = intent.getStringExtra(NotificationHelper.EXTRA_MACHINE_ID)
                    if (machineId != null) {
                        repository.clearTimer(machineId)
                        AlertScheduler.cancelMachineAlarms(context, machineId)
                        NotificationHelper.cancelMachineNotifications(context, machineId)
                    }
                }

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
