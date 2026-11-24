package jp.yukirawa.launotor

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.app.ActivityCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import jp.yukirawa.launotor.notifications.LiveUpdateSupport
import jp.yukirawa.launotor.notifications.NotificationHelper
import jp.yukirawa.launotor.sync.BleGossipTransport
import jp.yukirawa.launotor.ui.LaundryViewModel
import jp.yukirawa.launotor.ui.components.MachineTimerBottomSheet
import jp.yukirawa.launotor.ui.screen.LaundryRoomScreen
import jp.yukirawa.launotor.ui.screen.SettingsSheet
import jp.yukirawa.launotor.ui.theme.LaunotorTheme

class MainActivity : ComponentActivity() {

    private val viewModel: LaundryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleIntent(intent)

        setContent {
            LaunotorTheme {
                val context = LocalContext.current
                val lifecycleOwner = LocalLifecycleOwner.current
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()
                val selectedMachineId by viewModel.selectedMachineId.collectAsStateWithLifecycle()
                val showSettings by viewModel.showSettings.collectAsStateWithLifecycle()

                var hasNotificationPermission by remember {
                    mutableStateOf(NotificationHelper.hasPostNotificationPermission(context))
                }
                var canPostPromoted by remember {
                    mutableStateOf(LiveUpdateSupport.canPostPromotedNotifications(context))
                }
                var permissionDeniedPermanently by remember { mutableStateOf(false) }

                var hasBlePermissions by remember {
                    mutableStateOf(BleGossipTransport.hasAllBlePermissions(context))
                }
                var blePermissionDeniedPermanently by remember { mutableStateOf(false) }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission(),
                ) { granted ->
                    hasNotificationPermission = granted || NotificationHelper.hasPostNotificationPermission(context)
                    canPostPromoted = LiveUpdateSupport.canPostPromotedNotifications(context)
                    if (!granted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionDeniedPermanently = !ActivityCompat.shouldShowRequestPermissionRationale(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    }
                }

                val blePermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                ) { _ ->
                    val granted = BleGossipTransport.hasAllBlePermissions(context)
                    hasBlePermissions = granted
                    if (granted) {
                        blePermissionDeniedPermanently = false
                        viewModel.refreshP2pSync()
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val anyRationale = BleGossipTransport.requiredRuntimePermissions().any { perm ->
                            ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, perm)
                        }
                        blePermissionDeniedPermanently = !anyRationale
                    }
                }

                fun requestOrOpenNotificationSettings() {
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
                    val canPrompt = !permissionDeniedPermanently ||
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this@MainActivity,
                            Manifest.permission.POST_NOTIFICATIONS,
                        )
                    if (canPrompt) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        val settingsIntent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        runCatching { context.startActivity(settingsIntent) }
                    }
                }

                fun requestOrOpenBleSettings() {
                    val perms = BleGossipTransport.requiredRuntimePermissions()
                    if (perms.isEmpty()) return
                    val anyRationale = perms.any { perm ->
                        ActivityCompat.shouldShowRequestPermissionRationale(this@MainActivity, perm)
                    }
                    val canPrompt = !blePermissionDeniedPermanently || anyRationale
                    if (canPrompt) {
                        blePermissionLauncher.launch(perms)
                    } else {
                        val appSettingsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = Uri.fromParts("package", context.packageName, null)
                        }
                        runCatching { context.startActivity(appSettingsIntent) }
                    }
                }

                DisposableEffect(lifecycleOwner) {
                    val coordinator = (application as LaunotorApplication).p2pSyncCoordinator
                    val observer = LifecycleEventObserver { _, event ->
                        when (event) {
                            Lifecycle.Event.ON_START -> coordinator.setForegroundActive(true)
                            Lifecycle.Event.ON_STOP -> coordinator.setForegroundActive(false)
                            Lifecycle.Event.ON_RESUME -> {
                                hasNotificationPermission = NotificationHelper.hasPostNotificationPermission(context)
                                canPostPromoted = LiveUpdateSupport.canPostPromotedNotifications(context)
                                val bleNow = BleGossipTransport.hasAllBlePermissions(context)
                                if (bleNow && !hasBlePermissions) {
                                    viewModel.refreshP2pSync()
                                }
                                hasBlePermissions = bleNow
                                viewModel.refreshMonitors()
                            }
                            else -> Unit
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        coordinator.setForegroundActive(false)
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                LaunchedEffect(Unit) {
                    if (
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        !NotificationHelper.hasPostNotificationPermission(context)
                    ) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                LaundryRoomScreen(
                    uiState = uiState,
                    hasNotificationPermission = hasNotificationPermission,
                    onMachineClick = { spec -> viewModel.selectMachine(spec.id) },
                    onOpenSettings = { viewModel.setSettingsVisible(true) },
                    onClearAllFinished = { viewModel.clearAllFinished() },
                    onRequestNotificationPermission = ::requestOrOpenNotificationSettings,
                )

                val selectedState = selectedMachineId?.let { uiState.machineStates[it] }
                if (selectedState != null) {
                    MachineTimerBottomSheet(
                        state = selectedState,
                        hapticsEnabled = uiState.settings.hapticsEnabled,
                        onDismiss = { viewModel.selectMachine(null) },
                        onStartOrUpdate = { minutes ->
                            viewModel.startOrUpdateTimer(selectedState.spec.id, minutes)
                        },
                        onClearTimer = {
                            viewModel.clearTimer(selectedState.spec.id)
                        },
                        onToggleWatch = { watched ->
                            viewModel.setWatchedByMe(selectedState.spec.id, watched)
                        },
                    )
                }

                if (showSettings) {
                    SettingsSheet(
                        settings = uiState.settings,
                        canPostPromoted = canPostPromoted,
                        hasNotificationPermission = hasNotificationPermission,
                        hasBlePermissions = hasBlePermissions,
                        onRequestNotificationPermission = ::requestOrOpenNotificationSettings,
                        onRequestBlePermissions = ::requestOrOpenBleSettings,
                        onUpdateThresholdMinutes = { mins ->
                            viewModel.updateAlmostDoneThreshold(mins)
                        },
                        onUpdateHapticsEnabled = { enabled ->
                            viewModel.updateHapticsEnabled(enabled)
                        },
                        onUpdateLiveUpdatesEnabled = { enabled ->
                            viewModel.updateLiveUpdatesEnabled(enabled)
                        },
                        onUpdateP2pSyncEnabled = { enabled ->
                            viewModel.updateP2pSyncEnabled(enabled)
                        },
                        onUpdateBackgroundP2pEnabled = { enabled ->
                            viewModel.updateBackgroundP2pEnabled(enabled)
                        },
                        onDismiss = { viewModel.setSettingsVisible(false) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val machineId = intent?.getStringExtra(NotificationHelper.EXTRA_MACHINE_ID)
        if (!machineId.isNullOrBlank()) {
            viewModel.selectMachine(machineId)
        }
    }
}
