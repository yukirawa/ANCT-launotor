package jp.yukirawa.launotor.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import jp.yukirawa.launotor.R
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineSpec
import jp.yukirawa.launotor.domain.model.MachineState
import jp.yukirawa.launotor.domain.model.MachineTimerCalculator
import jp.yukirawa.launotor.ui.LaundryUiState
import jp.yukirawa.launotor.ui.components.MachineCard
import jp.yukirawa.launotor.ui.theme.LocalLaundryExpressiveColors

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LaundryRoomScreen(
    uiState: LaundryUiState,
    hasNotificationPermission: Boolean,
    onMachineClick: (MachineSpec) -> Unit,
    onOpenSettings: () -> Unit,
    onClearAllFinished: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val expressive = LocalLaundryExpressiveColors.current
    val haptic = LocalHapticFeedback.current

    fun stateFor(spec: MachineSpec): MachineState {
        return uiState.machineStates[spec.id] ?: MachineTimerCalculator.computeState(
            spec = spec,
            record = null,
            nowEpochMillis = uiState.nowEpochMillis,
            almostDoneThresholdMinutes = uiState.settings.almostDoneThresholdMinutes,
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colorScheme.surface,
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Ultra-clean single-line top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_sub_name),
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactCountPill(
                        icon = Icons.Rounded.LocalLaundryService,
                        count = uiState.availableWashers,
                        containerColor = expressive.washerAccentSoft,
                        contentColor = expressive.washerOnAccentSoft,
                        contentDescription = "洗濯機の空き ${uiState.availableWashers}台",
                    )
                    CompactCountPill(
                        icon = Icons.Rounded.WbSunny,
                        count = uiState.availableDryers,
                        containerColor = expressive.dryerAccentSoft,
                        contentColor = expressive.dryerOnAccentSoft,
                        contentDescription = "乾燥機の空き ${uiState.availableDryers}台",
                    )
                    FilledTonalIconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "設定",
                        )
                    }
                }
            }

            // Optional compact notification prompt (only when denied)
            AnimatedVisibility(visible = !hasNotificationPermission) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(
                        onClick = onRequestNotificationPermission,
                        shape = CircleShape,
                        color = colorScheme.errorContainer,
                        contentColor = colorScheme.onErrorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = "通知をオンにする",
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }

            // Spatial 4-column Material 3 Expressive Floorplan (borderless, uniform 1:1 shapes)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Left Wall: Washers 1..4
                    MachineColumn(
                        specs = LaundryRoomCatalog.leftWashers,
                        stateFor = ::stateFor,
                        onMachineClick = onMachineClick,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Center-Left: Dryers 1..3
                    MachineColumn(
                        specs = LaundryRoomCatalog.centerLeftDryers,
                        stateFor = ::stateFor,
                        onMachineClick = onMachineClick,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(6.dp))

                    // Center-Right: Dryers 4..6
                    MachineColumn(
                        specs = LaundryRoomCatalog.centerRightDryers,
                        stateFor = ::stateFor,
                        onMachineClick = onMachineClick,
                        modifier = Modifier.weight(1f),
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    // Right Wall: Washers 5..8
                    MachineColumn(
                        specs = LaundryRoomCatalog.rightWashers,
                        stateFor = ::stateFor,
                        onMachineClick = onMachineClick,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // Celebratory one-tap clear pill when any machine has finished
            AnimatedVisibility(visible = uiState.finishedCount > 0) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = {
                            if (uiState.settings.hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onClearAllFinished()
                        },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = expressive.finishedContainer,
                            contentColor = expressive.finishedContent,
                        ),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DoneAll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "完了 ${uiState.finishedCount}台を空きにする",
                            style = MaterialTheme.typography.labelLargeEmphasized,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MachineColumn(
    specs: List<MachineSpec>,
    stateFor: (MachineSpec) -> MachineState,
    onMachineClick: (MachineSpec) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        specs.forEach { spec ->
            MachineCard(
                state = stateFor(spec),
                onClick = { onMachineClick(spec) },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }
    }
}

@Composable
private fun CompactCountPill(
    icon: ImageVector,
    count: Int,
    containerColor: Color,
    contentColor: Color,
    contentDescription: String,
) {
    Surface(
        shape = CircleShape,
        color = containerColor,
        contentColor = contentColor,
        modifier = Modifier.semantics(mergeDescendants = true) {
            this.contentDescription = contentDescription
        },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
