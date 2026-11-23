package jp.yukirawa.launotor.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineTimerRecord

object AlertScheduler {
    const val EXTRA_MACHINE_ID = "extra_machine_id"
    const val EXTRA_ALARM_KIND = "extra_alarm_kind"
    const val EXTRA_END_TIME_MS = "extra_end_time_ms"

    fun canScheduleExactAlarms(context: Context): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    fun syncAlarms(
        context: Context,
        records: Map<String, MachineTimerRecord>,
        settings: AppSettings,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        val desired = TimerNotificationPlanner.computeScheduledAlarms(
            records = records,
            settings = settings,
            nowEpochMillis = nowEpochMillis,
        ).associateBy { it.machineId to it.kind }

        val allowExact = canScheduleExactAlarms(context)

        for (spec in LaundryRoomCatalog.allMachines) {
            for (kind in AlarmKind.entries) {
                val alarmSpec = desired[spec.id to kind]
                if (alarmSpec == null) {
                    cancelAlarm(context, alarmManager, spec.id, kind)
                } else {
                    scheduleAlarm(context, alarmManager, alarmSpec, allowExact)
                }
            }
        }
    }

    fun cancelMachineAlarms(context: Context, machineId: String) {
        val alarmManager = context.getSystemService(AlarmManager::class.java) ?: return
        for (kind in AlarmKind.entries) {
            cancelAlarm(context, alarmManager, machineId, kind)
        }
    }

    private fun scheduleAlarm(
        context: Context,
        alarmManager: AlarmManager,
        spec: ScheduledAlarmSpec,
        allowExact: Boolean,
    ) {
        val pendingIntent = createPendingIntent(
            context = context,
            machineId = spec.machineId,
            kind = spec.kind,
            expectedEndTimeMillis = spec.expectedEndTimeEpochMillis,
        )
        if (allowExact) {
            try {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    spec.triggerAtEpochMillis,
                    pendingIntent,
                )
                return
            } catch (_: SecurityException) {
                // Fall through to inexact scheduling if permission was revoked concurrently.
            }
        }
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            spec.triggerAtEpochMillis,
            pendingIntent,
        )
    }

    private fun cancelAlarm(
        context: Context,
        alarmManager: AlarmManager,
        machineId: String,
        kind: AlarmKind,
    ) {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = actionFor(machineId, kind)
        }
        val existing = PendingIntent.getBroadcast(
            context,
            requestCodeFor(machineId, kind),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        )
        if (existing != null) {
            alarmManager.cancel(existing)
            existing.cancel()
        }
    }

    private fun createPendingIntent(
        context: Context,
        machineId: String,
        kind: AlarmKind,
        expectedEndTimeMillis: Long,
    ): PendingIntent {
        val intent = Intent(context, TimerAlarmReceiver::class.java).apply {
            action = actionFor(machineId, kind)
            putExtra(EXTRA_MACHINE_ID, machineId)
            putExtra(EXTRA_ALARM_KIND, kind.name)
            putExtra(EXTRA_END_TIME_MS, expectedEndTimeMillis)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCodeFor(machineId, kind),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun actionFor(machineId: String, kind: AlarmKind): String =
        "jp.yukirawa.launotor.ALARM_${machineId}_${kind.name}"

    fun requestCodeFor(machineId: String, kind: AlarmKind): Int {
        val index = LaundryRoomCatalog.allMachines.indexOfFirst { it.id == machineId }.coerceAtLeast(0)
        return 2000 + index * 10 + kind.ordinal
    }
}
