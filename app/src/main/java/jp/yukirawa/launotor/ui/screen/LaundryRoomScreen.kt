package jp.yukirawa.launotor.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yukirawa.launotor.R
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineSpec
import jp.yukirawa.launotor.domain.model.MachineState
import jp.yukirawa.launotor.domain.model.MachineTimerCalculator
import jp.yukirawa.launotor.notifications.LiveUpdateSupport
import jp.yukirawa.launotor.ui.LaundryUiState
import jp.yukirawa.launotor.ui.components.MachineCard
import jp.yukirawa.launotor.ui.theme.LocalLaundrySemanticColors

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LaundryRoomScreen(
    uiState: LaundryUiState,
    hasNotificationPermission: Boolean,
    canPostPromotedNotifications: Boolean,
    onMachineClick: (MachineSpec) -> Unit,
    onOpenSettings: () -> Unit,
    onClearAllFinished: () -> Unit,
    onRequestNotificationPermission: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val semantic = LocalLaundrySemanticColors.current
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
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Expressive Header
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleLargeEmphasized,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = colorScheme.primaryContainer,
                                    contentColor = colorScheme.onPrimaryContainer,
                                ) {
                                    Text(
                                        text = stringResource(R.string.app_sub_name),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            Text(
                                text = "各マシンの配置に合わせてタップで残り時間を設定",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        FilledTonalIconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Rounded.Settings,
                                contentDescription = "設定を開く",
                            )
                        }
                    }

                    // Summary availability & active counters
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        SummaryStatChip(
                            icon = Icons.Rounded.LocalLaundryService,
                            label = "洗濯機 空き",
                            value = "${uiState.availableWashers}/${LaundryRoomCatalog.TOTAL_WASHERS}",
                            containerColor = semantic.availableContainer,
                            contentColor = semantic.onAvailableContainer,
                        )
                        SummaryStatChip(
                            icon = Icons.Rounded.WbSunny,
                            label = "乾燥機 空き",
                            value = "${uiState.availableDryers}/${LaundryRoomCatalog.TOTAL_DRYERS}",
                            containerColor = semantic.availableContainer,
                            contentColor = semantic.onAvailableContainer,
                        )
                        SummaryStatChip(
                            icon = Icons.Rounded.Timer,
                            label = "稼働監視中",
                            value = "${uiState.activeCount}台",
                            containerColor = if (uiState.activeCount > 0) {
                                colorScheme.primaryContainer
                            } else {
                                colorScheme.surfaceContainerHigh
                            },
                            contentColor = if (uiState.activeCount > 0) {
                                colorScheme.onPrimaryContainer
                            } else {
                                colorScheme.onSurfaceVariant
                            },
                        )

                        if (LiveUpdateSupport.isAndroid16OrHigher()) {
                            val liveEnabled = uiState.settings.liveUpdatesEnabled
                            val (badgeBg, badgeFg, badgeLabel) = when {
                                !liveEnabled -> Triple(
                                    colorScheme.surfaceContainerHigh,
                                    colorScheme.onSurfaceVariant,
                                    "Live Updates OFF",
                                )
                                canPostPromotedNotifications -> Triple(
                                    colorScheme.secondaryContainer,
                                    colorScheme.onSecondaryContainer,
                                    "Live Updates ON",
                                )
                                else -> Triple(
                                    colorScheme.tertiaryContainer,
                                    colorScheme.onTertiaryContainer,
                                    "Live Updates 要許可",
                                )
                            }
                            Surface(
                                onClick = onOpenSettings,
                                shape = MaterialTheme.shapes.small,
                                color = badgeBg,
                                contentColor = badgeFg,
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = if (liveEnabled) {
                                            Icons.Rounded.NotificationsActive
                                        } else {
                                            Icons.Rounded.NotificationsOff
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Text(
                                        text = badgeLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }

                    AnimatedVisibility(visible = !hasNotificationPermission) {
                        Surface(
                            onClick = onRequestNotificationPermission,
                            shape = MaterialTheme.shapes.medium,
                            color = colorScheme.errorContainer,
                            contentColor = colorScheme.onErrorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.NotificationsOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "通知権限を許可すると、空き予定・完了アラートが届きます（タップして許可）",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }

                    AnimatedVisibility(visible = uiState.finishedCount > 0) {
                        FilledTonalButton(
                            onClick = {
                                if (uiState.settings.hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onClearAllFinished()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 14.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DoneAll,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "完了済み ${uiState.finishedCount}台 をまとめて空きにする",
                                style = MaterialTheme.typography.labelLargeEmphasized,
                            )
                        }
                    }
                }
            }

            // Spatial Laundry Room Map:
            // Left Wall (Washers 1..4) | Center Island (Dryers 1..3 + 4..6) | Right Wall (Washers 5..8)
            Surface(
                shape = MaterialTheme.shapes.large,
                color = colorScheme.surfaceContainerLowest,
                border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.6f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Zone guide strip
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ZoneHeaderLabel(
                            text = "洗濯機 1-4",
                            modifier = Modifier.weight(1f),
                        )
                        ZoneHeaderLabel(
                            text = "中央 乾燥機 (1-6)",
                            modifier = Modifier.weight(2.25f),
                        )
                        ZoneHeaderLabel(
                            text = "洗濯機 5-8",
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // 3-zone spatial floor plan
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Left Wall: Washers 1..4
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LaundryRoomCatalog.leftWashers.forEach { spec ->
                                MachineCard(
                                    state = stateFor(spec),
                                    onClick = { onMachineClick(spec) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp),
                                )
                            }
                        }

                        // Center Island: Dryers 1..3 (left) & 4..6 (right)
                        Surface(
                            modifier = Modifier.weight(2.25f),
                            shape = MaterialTheme.shapes.large,
                            color = colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, colorScheme.outlineVariant.copy(alpha = 0.7f)),
                        ) {
                            Column(
                                modifier = Modifier.padding(4.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = "◀ アイランド ▶",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    // Island Left: Dryers 1..3
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        LaundryRoomCatalog.centerLeftDryers.forEach { spec ->
                                            MachineCard(
                                                state = stateFor(spec),
                                                onClick = { onMachineClick(spec) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(114.dp),
                                            )
                                        }
                                    }

                                    // Island Right: Dryers 4..6
                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        LaundryRoomCatalog.centerRightDryers.forEach { spec ->
                                            MachineCard(
                                                state = stateFor(spec),
                                                onClick = { onMachineClick(spec) },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(114.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Right Wall: Washers 5..8
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LaundryRoomCatalog.rightWashers.forEach { spec ->
                                MachineCard(
                                    state = stateFor(spec),
                                    onClick = { onMachineClick(spec) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(104.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoneHeaderLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SummaryStatChip(
    icon: ImageVector,
    label: String,
    value: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "$label $value",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
