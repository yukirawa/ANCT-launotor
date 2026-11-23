package jp.yukirawa.launotor

import android.Manifest
import android.content.Intent
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

                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            hasNotificationPermission = NotificationHelper.hasPostNotificationPermission(context)
                            canPostPromoted = LiveUpdateSupport.canPostPromotedNotifications(context)
                            viewModel.refreshMonitors()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
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
                    )
                }

                if (showSettings) {
                    SettingsSheet(
                        settings = uiState.settings,
                        canPostPromoted = canPostPromoted,
                        hasNotificationPermission = hasNotificationPermission,
                        onRequestNotificationPermission = ::requestOrOpenNotificationSettings,
                        onUpdateThresholdMinutes = { mins ->
                            viewModel.updateAlmostDoneThreshold(mins)
                        },
                        onUpdateHapticsEnabled = { enabled ->
                            viewModel.updateHapticsEnabled(enabled)
                        },
                        onUpdateLiveUpdatesEnabled = { enabled ->
                            viewModel.updateLiveUpdatesEnabled(enabled)
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
