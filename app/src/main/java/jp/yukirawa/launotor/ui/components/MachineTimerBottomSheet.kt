package jp.yukirawa.launotor.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberSliderState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yukirawa.launotor.domain.model.LaundryRoomCatalog
import jp.yukirawa.launotor.domain.model.MachineState
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineType
import jp.yukirawa.launotor.ui.theme.LocalLaundryExpressiveColors
import jp.yukirawa.launotor.ui.theme.TabularCountdownStyle
import kotlin.math.roundToInt

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun MachineTimerBottomSheet(
    state: MachineState,
    hapticsEnabled: Boolean,
    onDismiss: () -> Unit,
    onStartOrUpdate: (durationMinutes: Int) -> Unit,
    onClearTimer: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    val expressive = LocalLaundryExpressiveColors.current
    val isWasher = state.spec.type == MachineType.WASHER

    val accentColor = if (isWasher) expressive.washerAccent else expressive.dryerAccent
    val onAccentColor = if (isWasher) expressive.washerOnAccent else expressive.dryerOnAccent
    val accentSoftColor = if (isWasher) expressive.washerAccentSoft else expressive.dryerAccentSoft
    val onAccentSoftColor = if (isWasher) expressive.washerOnAccentSoft else expressive.dryerOnAccentSoft

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
        containerColor = colorScheme.surfaceContainerLow,
    ) {
        key(state.spec.id) {
            val defaultMinutes = remember(state.spec.id) {
                when (state.status) {
                    MachineStatus.RUNNING, MachineStatus.ALMOST_DONE ->
                        state.remainingWholeMinutesCeil.coerceIn(
                            LaundryRoomCatalog.MIN_DURATION_MINUTES,
                            LaundryRoomCatalog.SLIDER_MAX_DURATION_MINUTES,
                        )
                    else -> if (isWasher) 35 else 30
                }
            }

            var selectedMinutes by remember { mutableIntStateOf(defaultMinutes) }
            val sliderState = rememberSliderState(
                value = defaultMinutes.toFloat(),
                trackRange = LaundryRoomCatalog.MIN_DURATION_MINUTES.toFloat()..
                    LaundryRoomCatalog.SLIDER_MAX_DURATION_MINUTES.toFloat(),
            )
            val interactionSource = remember { MutableInteractionSource() }
            val sliderColors = SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = accentSoftColor,
                activeTickColor = onAccentColor,
                inactiveTickColor = onAccentSoftColor,
            )

            val badgeShape = (if (isWasher) MaterialShapes.Square else MaterialShapes.Circle).toShape()
            val badgeIcon = if (isWasher) Icons.Rounded.LocalLaundryService else Icons.Rounded.WbSunny

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // Header: colorful shape badge + single-line machine name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Surface(
                        shape = badgeShape,
                        color = accentColor,
                        contentColor = onAccentColor,
                        modifier = Modifier.size(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = badgeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    Text(
                        text = state.spec.fullName,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Hero readout: Giant tabular numeral + 分
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Text(
                        text = selectedMinutes.toString(),
                        style = TabularCountdownStyle.copy(
                            fontSize = 72.sp,
                            lineHeight = 74.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = accentColor,
                        ),
                        modifier = Modifier.alignByBaseline(),
                        maxLines = 1,
                        softWrap = false,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "分",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor.copy(alpha = 0.85f),
                        ),
                        modifier = Modifier.alignByBaseline(),
                        maxLines = 1,
                        softWrap = false,
                    )
                }

                // Giant tactile M3 Expressive Slider (1..90 min)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Slider(
                        state = sliderState,
                        onValueChange = { rawValue ->
                            sliderState.value = rawValue
                            val rounded = rawValue.roundToInt().coerceIn(
                                LaundryRoomCatalog.MIN_DURATION_MINUTES,
                                LaundryRoomCatalog.SLIDER_MAX_DURATION_MINUTES,
                            )
                            if (rounded != selectedMinutes) {
                                selectedMinutes = rounded
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 56.dp),
                        colors = sliderColors,
                        interactionSource = interactionSource,
                        thumb = {
                            SliderDefaults.Thumb(
                                interactionSource = interactionSource,
                                colors = sliderColors,
                                thumbSize = DpSize(12.dp, 48.dp),
                            )
                        },
                        track = { trackState ->
                            SliderDefaults.Track(
                                sliderState = trackState,
                                modifier = Modifier.height(32.dp),
                                colors = sliderColors,
                                drawStopIndicator = null,
                                thumbTrackGapSize = 6.dp,
                                trackInsideCornerSize = 16.dp,
                            )
                        },
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "${LaundryRoomCatalog.MIN_DURATION_MINUTES}分",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                        Text(
                            text = "${LaundryRoomCatalog.SLIDER_MAX_DURATION_MINUTES}分",
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                // Bottom Action(s): スタート (idle) or 空きにする + 更新 (running / finished)
                if (state.status == MachineStatus.IDLE) {
                    Button(
                        onClick = {
                            if (hapticsEnabled) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            }
                            onStartOrUpdate(selectedMinutes)
                            onDismiss()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accentColor,
                            contentColor = onAccentColor,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "スタート",
                            style = MaterialTheme.typography.titleMediumEmphasized,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FilledTonalButton(
                            onClick = {
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onClearTimer()
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = accentSoftColor,
                                contentColor = onAccentSoftColor,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "空きにする",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        Button(
                            onClick = {
                                if (hapticsEnabled) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                onStartOrUpdate(selectedMinutes)
                                onDismiss()
                            },
                            modifier = Modifier
                                .weight(1.25f)
                                .height(56.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor,
                                contentColor = onAccentColor,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Check,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "更新",
                                style = MaterialTheme.typography.titleMediumEmphasized,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }
}
