package jp.yukirawa.launotor.sync

import android.content.Context
import jp.yukirawa.launotor.data.repository.MachineRepository
import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.MachineTimerRecord
import jp.yukirawa.launotor.notifications.AlertScheduler
import jp.yukirawa.launotor.notifications.MonitoringForegroundService
import jp.yukirawa.launotor.notifications.NotificationHelper
import jp.yukirawa.launotor.notifications.TimerNotificationPlanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Coordinates hybrid P2P sync ([LanSyncTransport] + [BleGossipTransport]) with
 * [MachineRepository], debouncing outbound broadcasts and re-gossiping merged
 * remote records across transports (multi-hop).
 */
class P2pSyncCoordinator(
    context: Context,
    private val repository: MachineRepository,
    private val deviceIdProvider: SyncDeviceIdProvider,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val lanTransport = LanSyncTransport(appContext, deviceIdProvider)
    private val bleTransport = BleGossipTransport(appContext, deviceIdProvider)

    private val foregroundActive = MutableStateFlow(false)
    private val serviceActive = MutableStateFlow(false)

    @Volatile
    private var transportsRunning = false

    @Volatile
    private var latestRecords: Map<String, MachineTimerRecord> = emptyMap()

    @Volatile
    private var latestSettings: AppSettings = AppSettings()

    private var broadcastDebounceJob: Job? = null
    private var lastMachineDigest: Int = 0
    private var hasSeenInitialMachines = false

    init {
        scope.launch {
            combine(
                repository.settingsFlow,
                foregroundActive,
                serviceActive,
            ) { settings, fg, svc ->
                Triple(settings, fg, svc)
            }.collect { (settings, fg, svc) ->
                latestSettings = settings
                val shouldRun = settings.p2pSyncEnabled && (fg || svc)
                updateTransportLifecycle(shouldRun = shouldRun, isForegroundMode = fg)
            }
        }

        scope.launch {
            repository.machinesFlow.collect { records ->
                latestRecords = records
                val now = System.currentTimeMillis()
                val digest = computeSyncDigest(records)
                val changed = !hasSeenInitialMachines || digest != lastMachineDigest
                hasSeenInitialMachines = true
                lastMachineDigest = digest

                if (transportsRunning) {
                    lanTransport.updatePayload(records, now)
                    bleTransport.updatePayload(records, now)
                    if (changed) {
                        scheduleDebouncedBroadcast()
                    }
                }
            }
        }
    }

    fun setForegroundActive(active: Boolean) {
        foregroundActive.value = active
    }

    fun setServiceActive(active: Boolean) {
        serviceActive.value = active
    }

    fun refreshCapabilities() {
        bleTransport.refreshCapabilities()
    }

    @Synchronized
    private fun updateTransportLifecycle(shouldRun: Boolean, isForegroundMode: Boolean) {
        if (shouldRun) {
            if (!transportsRunning) {
                transportsRunning = true
                val now = System.currentTimeMillis()
                lanTransport.updatePayload(latestRecords, now)
                bleTransport.updatePayload(latestRecords, now)
                lanTransport.setPowerMode(isForegroundMode)
                bleTransport.setPowerMode(isForegroundMode)
                lanTransport.start(::onPacketReceived)
                bleTransport.start(::onPacketReceived)
            } else {
                lanTransport.setPowerMode(isForegroundMode)
                bleTransport.setPowerMode(isForegroundMode)
            }
        } else if (transportsRunning) {
            transportsRunning = false
            broadcastDebounceJob?.cancel()
            broadcastDebounceJob = null
            lanTransport.stop()
            bleTransport.stop()
        }
    }

    private fun scheduleDebouncedBroadcast() {
        broadcastDebounceJob?.cancel()
        broadcastDebounceJob = scope.launch {
            delay(OUTBOUND_DEBOUNCE_MS)
            if (!transportsRunning) return@launch
            val now = System.currentTimeMillis()
            val snapshot = latestRecords
            lanTransport.broadcastNow(snapshot, now)
            bleTransport.broadcastNow(snapshot, now)
        }
    }

    private fun onPacketReceived(received: ReceivedSyncPacket) {
        if (!latestSettings.p2pSyncEnabled) return
        val localShortId = deviceIdProvider.senderShortId and 0xFFFF
        if (received.packet.senderShortId == localShortId) return

        scope.launch {
            val rxNow = System.currentTimeMillis()
            val mergeResult = repository.mergeRemoteRecords(
                entries = received.packet.entries,
                source = received.source,
                rxNowEpochMillis = rxNow,
            )

            if (mergeResult.mergedCount > 0) {
                for (clearedId in mergeResult.clearedWatchedMachineIds) {
                    AlertScheduler.cancelMachineAlarms(appContext, clearedId)
                    NotificationHelper.cancelMachineNotifications(appContext, clearedId)
                }

                val (updatedRecords, updatedSettings) = repository.getSnapshot()
                NotificationHelper.dispatchDueNotifications(
                    context = appContext,
                    repository = repository,
                    records = updatedRecords,
                    settings = updatedSettings,
                    nowEpochMillis = rxNow,
                )
                val (postNotifyRecords, postNotifySettings) = repository.getSnapshot()
                AlertScheduler.syncAlarms(appContext, postNotifyRecords, postNotifySettings, rxNow)
                MonitoringForegroundService.syncWithTimers(
                    context = appContext,
                    hasRunningTimers = TimerNotificationPlanner.shouldKeepServiceRunning(
                        records = postNotifyRecords,
                        settings = postNotifySettings,
                        nowEpochMillis = rxNow,
                    ),
                )
            }
        }
    }

    private fun computeSyncDigest(records: Map<String, MachineTimerRecord>): Int {
        var acc = 17
        for ((_, rec) in records) {
            acc = 31 * acc + rec.machineId.hashCode()
            acc = 31 * acc + rec.initialDurationMillis.hashCode()
            acc = 31 * acc + rec.endTimeEpochMillis.hashCode()
            acc = 31 * acc + rec.updatedAtEpochMillis.hashCode()
            acc = 31 * acc + rec.originHash
        }
        return acc
    }

    companion object {
        private const val OUTBOUND_DEBOUNCE_MS = 350L
    }
}
