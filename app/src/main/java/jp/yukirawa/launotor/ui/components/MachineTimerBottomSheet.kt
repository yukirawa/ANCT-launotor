package jp.yukirawa.launotor.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.TonalToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineState
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineTimerCalculator
import jp.yukirawa.launotor.domain.model.MachineType
import jp.yukirawa.launotor.domain.model.RoomZone
import jp.yukirawa.launotor.notifications.TimerNotificationPlanner
import jp.yukirawa.launotor.ui.theme.LocalLaundrySemanticColors
import jp.yukirawa.launotor.ui.theme.TabularCountdownStyle
import kotlin.math.roundToInt

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalLayoutApi::class,
)
@Composable
fun MachineTimerBottomSheet(
    state: MachineState,
    hapticsEnabled: Boolean,
    nowEpochMillis: Long,
    onDismiss: () -> Unit,
    onStartOrUpdate: (durationMinutes: Int) -> Unit,
    onClearTimer: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    val semantic = LocalLaundrySemanticColors.current

    val defaultMinutes = remember(state.spec.id, state.status, state.remainingMillis) {
        when (state.status) {
            MachineStatus.RUNNING, MachineStatus.ALMOST_DONE ->
                state.remainingWholeMinutesCeil.coerceIn(
                    LaundryRoomCatalog.MIN_DURATION_MINUTES,
                    LaundryRoomCatalog.MAX_DURATION_MINUTES,
                )
            else -> if (state.spec.type == MachineType.WASHER) 30 else 30
        }
    }

    var selectedMinutes by remember(state.spec.id) { mutableIntStateOf(defaultMinutes) }
    var textFieldValue by remember(state.spec.id) { mutableStateOf(defaultMinutes.toString()) }

    fun setMinutes(newMinutes: Int, performTickHaptic: Boolean = true) {
        val clamped = newMinutes.coerceIn(
            LaundryRoomCatalog.MIN_DURATION_MINUTES,
            LaundryRoomCatalog.MAX_DURATION_MINUTES,
        )
        selectedMinutes = clamped
        textFieldValue = clamped.toString()
        if (performTickHaptic && hapticsEnabled) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    val zoneLabel = when (state.spec.zone) {
        RoomZone.LEFT_WALL -> "左側ウォール"
        RoomZone.CENTER_LEFT -> "中央アイランド左"
        RoomZone.CENTER_RIGHT -> "中央アイランド右"
        RoomZone.RIGHT_WALL -> "右側ウォール"
    }

    val animatedProgress by animateFloatAsState(
        targetValue = state.elapsedFraction,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "sheetCircularProgress",
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Top machine identity & current status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = state.spec.fullName,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = colorScheme.onSurface,
                    )
                    Text(
                        text = "$zoneLabel • ${if (state.spec.type == MachineType.WASHER) "洗濯機" else "乾燥機"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }

                val (badgeBg, badgeFg, badgeText) = when (state.status) {
                    MachineStatus.IDLE -> Triple(
                        semantic.availableContainer,
                        semantic.onAvailableContainer,
                        "空き",
                    )
                    MachineStatus.RUNNING -> Triple(
                        colorScheme.primaryContainer,
                        colorScheme.onPrimaryContainer,
                        "稼働中",
                    )
                    MachineStatus.ALMOST_DONE -> Triple(
                        semantic.almostDoneContainer,
                        semantic.onAlmostDoneContainer,
                        "まもなく空き",
                    )
                    MachineStatus.FINISHED -> Triple(
                        semantic.finishedContainer,
                        semantic.onFinishedContainer,
                        "完了（確認待ち）",
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = badgeBg,
                    contentColor = badgeFg,
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }

            // Active progress visual with CircularWavyProgressIndicator
            if (state.status == MachineStatus.RUNNING || state.status == MachineStatus.ALMOST_DONE) {
                val density = LocalDensity.current
                val stroke = with(density) { Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round) }
                val trackStroke = with(density) { Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round) }
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(84.dp),
                        ) {
                            CircularWavyProgressIndicator(
                                progress = { animatedProgress },
                                modifier = Modifier.size(84.dp),
                                color = if (state.status == MachineStatus.ALMOST_DONE) {
                                    semantic.almostDoneBorder
                                } else {
                                    colorScheme.primary
                                },
                                trackColor = colorScheme.surfaceVariant,
                                stroke = stroke,
                                trackStroke = trackStroke,
                            )
                            Text(
                                text = MachineTimerCalculator.formatCountdown(state.remainingMillis),
                                style = TabularCountdownStyle.copy(
                                    fontSize = 15.sp,
                                    color = colorScheme.onSurface,
                                ),
                            )
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "現在の残り時間",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "約 ${state.remainingWholeMinutesCeil}分",
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                color = colorScheme.onSurface,
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Schedule,
                                    contentDescription = null,
                                    modifier = Modifier.size(15.dp),
                                    tint = colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = "終了予定 ${TimerNotificationPlanner.formatEpochTime(state.record.endTimeEpochMillis)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            if (state.status == MachineStatus.FINISHED) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = semantic.finishedContainer,
                    contentColor = semantic.onFinishedContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = "運転が完了しました",
                                style = MaterialTheme.typography.titleMediumEmphasized,
                            )
                            Text(
                                text = "洗濯物を取り出したら「空きにする」を押してください。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        FilledTonalButton(
                            onClick = {
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onClearTimer()
                                onDismiss()
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("空きにする")
                        }
                    }
                }
            }

            // Quick duration presets (M3 Expressive ToggleButtons)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "残り時間プリセット（分）",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    state.spec.type.presetMinutes.forEach { preset ->
                        val isSelected = selectedMinutes == preset
                        TonalToggleButton(
                            checked = isSelected,
                            onCheckedChange = { setMinutes(preset) },
                        ) {
                            Text(
                                text = "${preset}分",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            // Expressive ButtonGroup for fast -5 / -1 / +1 / +5 adjustments
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "微調整・直接入力",
                    style = MaterialTheme.typography.titleSmall,
                    color = colorScheme.onSurface,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = textFieldValue,
                        onValueChange = { raw ->
                            val digits = raw.filter { it.isDigit() }.take(3)
                            textFieldValue = digits
                            val parsed = digits.toIntOrNull()
                            if (parsed != null) {
                                selectedMinutes = parsed.coerceIn(
                                    LaundryRoomCatalog.MIN_DURATION_MINUTES,
                                    LaundryRoomCatalog.MAX_DURATION_MINUTES,
                                )
                            }
                        },
                        label = { Text("残り(分)") },
                        suffix = { Text("分") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.width(116.dp),
                        shape = MaterialTheme.shapes.medium,
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Slider(
                            value = selectedMinutes.toFloat(),
                            onValueChange = { value ->
                                val rounded = value.roundToInt()
                                if (rounded != selectedMinutes) {
                                    setMinutes(rounded, performTickHaptic = false)
                                }
                            },
                            valueRange = LaundryRoomCatalog.MIN_DURATION_MINUTES.toFloat()..LaundryRoomCatalog.MAX_DURATION_MINUTES.toFloat(),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "1分",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "終了予定 ${
                                    TimerNotificationPlanner.formatEpochTime(
                                        nowEpochMillis + selectedMinutes * 60_000L,
                                    )
                                }",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.primary,
                            )
                            Text(
                                text = "180分",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                ButtonGroup(
                    overflowIndicator = { menuState ->
                        FilledTonalIconButton(
                            onClick = { menuState.show() },
                        ) {
                            Icon(Icons.Rounded.MoreHoriz, contentDescription = "その他")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = ButtonGroupDefaults.HorizontalArrangement,
                ) {
                    val deltas = listOf(-5, -1, 1, 5)
                    deltas.forEach { delta ->
                        val label = if (delta > 0) "+${delta}分" else "${delta}分"
                        clickableItem(
                            onClick = { setMinutes(selectedMinutes + delta) },
                            label = label,
                            weight = 1f,
                        )
                    }
                }
            }

            // Primary actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.status != MachineStatus.IDLE) {
                    OutlinedButton(
                        onClick = {
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onClearTimer()
                            onDismiss()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("空きにする")
                    }
                }

                val isUpdating = state.status == MachineStatus.RUNNING || state.status == MachineStatus.ALMOST_DONE
                Button(
                    onClick = {
                        if (hapticsEnabled) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                        onStartOrUpdate(selectedMinutes)
                        onDismiss()
                    },
                    modifier = Modifier
                        .weight(1.3f)
                        .height(52.dp),
                    shape = MaterialTheme.shapes.large,
                    contentPadding = ButtonDefaults.ContentPadding,
                ) {
                    Icon(
                        imageVector = if (isUpdating) Icons.Rounded.Refresh else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isUpdating) {
                            "${selectedMinutes}分で更新"
                        } else {
                            "${selectedMinutes}分で監視開始"
                        },
                        style = MaterialTheme.typography.titleMediumEmphasized,
                    )
                }
            }
        }
    }
}
