package jp.yukirawa.launotor.notifications

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import jp.yukirawa.launotor.LaunotorApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MonitoringForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var monitorJob: Job? = null
    private var tickerJob: Job? = null
    private var foregroundStarted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelfSafely()
            return START_NOT_STICKY
        }

        if (monitorJob == null) {
            startMonitoring()
        } else {
            serviceScope.launch { refreshOnce() }
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        val repository = (application as LaunotorApplication).repository

        monitorJob = serviceScope.launch {
            combine(repository.machinesFlow, repository.settingsFlow) { records, settings ->
                records to settings
            }.collect { (records, settings) ->
                updateMonitoringState(records, settings)
            }
        }

        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(TICK_INTERVAL_MS)
                refreshOnce()
            }
        }
    }

    private suspend fun refreshOnce() {
        val repository = (application as LaunotorApplication).repository
        val (records, settings) = repository.getSnapshot()
        updateMonitoringState(records, settings)
    }

    private suspend fun updateMonitoringState(
        records: Map<String, jp.yukirawa.launotor.domain.model.MachineTimerRecord>,
        settings: jp.yukirawa.launotor.domain.model.AppSettings,
    ) {
        val repository = (application as LaunotorApplication).repository
        val now = System.currentTimeMillis()

        NotificationHelper.dispatchDueNotifications(
            context = this,
            repository = repository,
            records = records,
            settings = settings,
            nowEpochMillis = now,
        )

        val model = TimerNotificationPlanner.buildLiveUpdateModel(
            records = records,
            settings = settings,
            nowEpochMillis = now,
        )

        if (model == null) {
            if (!foregroundStarted) {
                // Satisfy startForegroundService() contract before stopping so Android never throws
                // ForegroundServiceDidNotStartInTimeException when a timer expires mid-launch.
                val placeholder = NotificationHelper.buildServiceIdleNotification(this)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForeground(
                        LiveUpdateNotificationBuilder.LIVE_NOTIFICATION_ID,
                        placeholder,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                    )
                } else {
                    startForeground(LiveUpdateNotificationBuilder.LIVE_NOTIFICATION_ID, placeholder)
                }
                foregroundStarted = true
            }
            stopSelfSafely()
            return
        }

        val notification = LiveUpdateNotificationBuilder.build(
            context = this,
            model = model,
            requestPromotedOngoing = settings.liveUpdatesEnabled,
        )

        if (!foregroundStarted) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    LiveUpdateNotificationBuilder.LIVE_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(LiveUpdateNotificationBuilder.LIVE_NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        } else if (NotificationHelper.hasPostNotificationPermission(this)) {
            getSystemService(NotificationManager::class.java)
                ?.notify(LiveUpdateNotificationBuilder.LIVE_NOTIFICATION_ID, notification)
        }
    }

    private fun stopSelfSafely() {
        foregroundStarted = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        monitorJob?.cancel()
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_START_OR_UPDATE = "jp.yukirawa.launotor.ACTION_MONITOR_START"
        private const val ACTION_STOP = "jp.yukirawa.launotor.ACTION_MONITOR_STOP"
        private const val TICK_INTERVAL_MS = 15_000L

        fun syncWithTimers(context: Context, hasRunningTimers: Boolean) {
            if (hasRunningTimers) {
                val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                    action = ACTION_START_OR_UPDATE
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (_: IllegalStateException) {
                    // On Android 12+ background FGS restrictions (e.g. if called when not allowed),
                    // AlarmManager alarms remain scheduled and will still fire alerts reliably.
                }
            } else {
                val intent = Intent(context, MonitoringForegroundService::class.java)
                context.stopService(intent)
            }
        }
    }
}
