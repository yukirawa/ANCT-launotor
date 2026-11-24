package jp.yukirawa.launotor.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import jp.yukirawa.launotor.LaunotorApplication
import jp.yukirawa.launotor.data.repository.MachineRepository
import jp.yukirawa.launotor.domain.model.AppSettings
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineState
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineTimerCalculator
import jp.yukirawa.launotor.domain.model.MachineType
import jp.yukirawa.launotor.notifications.AlertScheduler
import jp.yukirawa.launotor.notifications.MonitoringForegroundService
import jp.yukirawa.launotor.notifications.NotificationHelper
import jp.yukirawa.launotor.notifications.TimerNotificationPlanner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LaundryUiState(
    val machineStates: Map<String, MachineState> = emptyMap(),
    val availableWashers: Int = LaundryRoomCatalog.TOTAL_WASHERS,
    val availableDryers: Int = LaundryRoomCatalog.TOTAL_DRYERS,
    val activeCount: Int = 0,
    val finishedCount: Int = 0,
    val settings: AppSettings = AppSettings(),
    val nowEpochMillis: Long = System.currentTimeMillis(),
)

class LaundryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: MachineRepository =
        (application as LaunotorApplication).repository

    private val tickerFlow = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000L)
        }
    }

    private val _selectedMachineId = MutableStateFlow<String?>(null)
    val selectedMachineId: StateFlow<String?> = _selectedMachineId.asStateFlow()

    private val _showSettings = MutableStateFlow(false)
    val showSettings: StateFlow<Boolean> = _showSettings.asStateFlow()

    val uiState: StateFlow<LaundryUiState> = combine(
        repository.machinesFlow,
        repository.settingsFlow,
        tickerFlow,
    ) { records, settings, now ->
        val computed = LaundryRoomCatalog.allMachines.associate { spec ->
            spec.id to MachineTimerCalculator.computeState(
                spec = spec,
                record = records[spec.id],
                nowEpochMillis = now,
                almostDoneThresholdMinutes = settings.almostDoneThresholdMinutes,
            )
        }

        val availWashers = computed.values.count {
            it.spec.type == MachineType.WASHER && it.status == MachineStatus.IDLE
        }
        val availDryers = computed.values.count {
            it.spec.type == MachineType.DRYER && it.status == MachineStatus.IDLE
        }
        val active = computed.values.count {
            it.status == MachineStatus.RUNNING || it.status == MachineStatus.ALMOST_DONE
        }
        val finished = computed.values.count { it.status == MachineStatus.FINISHED }

        LaundryUiState(
            machineStates = computed,
            availableWashers = availWashers,
            availableDryers = availDryers,
            activeCount = active,
            finishedCount = finished,
            settings = settings,
            nowEpochMillis = now,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = LaundryUiState(
            machineStates = LaundryRoomCatalog.allMachines.associate { spec ->
                spec.id to MachineTimerCalculator.computeState(
                    spec = spec,
                    record = null,
                    nowEpochMillis = System.currentTimeMillis(),
                    almostDoneThresholdMinutes = AppSettings.DEFAULT_ALMOST_DONE_THRESHOLD_MINUTES,
                )
            },
        ),
    )

    init {
        viewModelScope.launch {
            syncBackgroundMonitors()
        }
    }

    fun selectMachine(machineId: String?) {
        _selectedMachineId.value = machineId
    }

    fun setSettingsVisible(visible: Boolean) {
        _showSettings.value = visible
    }

    fun startOrUpdateTimer(machineId: String, durationMinutes: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.startOrUpdateTimer(machineId, durationMinutes, now)
            NotificationHelper.cancelMachineNotifications(getApplication(), machineId)
            syncBackgroundMonitors()
        }
    }

    fun clearTimer(machineId: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            repository.clearTimer(machineId, now)
            AlertScheduler.cancelMachineAlarms(getApplication(), machineId)
            NotificationHelper.cancelMachineNotifications(getApplication(), machineId)
            syncBackgroundMonitors()
        }
    }

    fun setWatchedByMe(machineId: String, watched: Boolean) {
        viewModelScope.launch {
            repository.setWatchedByMe(machineId, watched)
            if (!watched) {
                AlertScheduler.cancelMachineAlarms(getApplication(), machineId)
                NotificationHelper.cancelMachineNotifications(getApplication(), machineId)
            }
            syncBackgroundMonitors()
        }
    }

    fun clearAllFinished() {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val cleared = repository.clearAllFinished(now)
            cleared.forEach { machineId ->
                AlertScheduler.cancelMachineAlarms(getApplication(), machineId)
                NotificationHelper.cancelMachineNotifications(getApplication(), machineId)
            }
            syncBackgroundMonitors()
        }
    }

    fun updateAlmostDoneThreshold(minutes: Int) {
        viewModelScope.launch {
            repository.updateAlmostDoneThreshold(minutes)
            syncBackgroundMonitors()
        }
    }

    fun updateHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateHapticsEnabled(enabled)
        }
    }

    fun updateLiveUpdatesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateLiveUpdatesEnabled(enabled)
            syncBackgroundMonitors()
        }
    }

    fun updateP2pSyncEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateP2pSyncEnabled(enabled)
            syncBackgroundMonitors()
        }
    }

    fun updateBackgroundP2pEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateBackgroundP2pEnabled(enabled)
            syncBackgroundMonitors()
        }
    }

    fun refreshP2pSync() {
        getApplication<LaunotorApplication>().p2pSyncCoordinator.refreshCapabilities()
    }

    fun refreshMonitors() {
        viewModelScope.launch {
            syncBackgroundMonitors()
        }
    }

    private suspend fun syncBackgroundMonitors() {
        val context = getApplication<LaunotorApplication>()
        val now = System.currentTimeMillis()
        val (records, settings) = repository.getSnapshot()
        NotificationHelper.dispatchDueNotifications(
            context = context,
            repository = repository,
            records = records,
            settings = settings,
            nowEpochMillis = now,
        )
        val (latestRecords, latestSettings) = repository.getSnapshot()
        AlertScheduler.syncAlarms(context, latestRecords, latestSettings, now)
        MonitoringForegroundService.syncWithTimers(
            context = context,
            hasRunningTimers = TimerNotificationPlanner.shouldKeepServiceRunning(
                records = latestRecords,
                settings = latestSettings,
                nowEpochMillis = now,
            ),
        )
    }
}
