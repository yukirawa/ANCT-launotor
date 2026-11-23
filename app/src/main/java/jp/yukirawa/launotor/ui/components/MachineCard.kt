package jp.yukirawa.launotor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WavyProgressIndicatorDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yukirawa.launotor.domain.model.MachineState
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineTimerCalculator
import jp.yukirawa.launotor.domain.model.MachineType
import jp.yukirawa.launotor.ui.theme.LocalLaundrySemanticColors
import jp.yukirawa.launotor.ui.theme.TabularCountdownStyle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MachineCard(
    state: MachineState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val semantic = LocalLaundrySemanticColors.current
    val colorScheme = MaterialTheme.colorScheme
    val motionScheme = MaterialTheme.motionScheme

    val isWasher = state.spec.type == MachineType.WASHER

    val targetContainerColor = when (state.status) {
        MachineStatus.IDLE -> colorScheme.surfaceContainerLowest
        MachineStatus.RUNNING -> if (isWasher) {
            colorScheme.primaryContainer
        } else {
            colorScheme.tertiaryContainer
        }
        MachineStatus.ALMOST_DONE -> semantic.almostDoneContainer
        MachineStatus.FINISHED -> semantic.finishedContainer
    }

    val targetContentColor = when (state.status) {
        MachineStatus.IDLE -> colorScheme.onSurface
        MachineStatus.RUNNING -> if (isWasher) {
            colorScheme.onPrimaryContainer
        } else {
            colorScheme.onTertiaryContainer
        }
        MachineStatus.ALMOST_DONE -> semantic.onAlmostDoneContainer
        MachineStatus.FINISHED -> semantic.onFinishedContainer
    }

    val targetBorderColor = when (state.status) {
        MachineStatus.IDLE -> colorScheme.outlineVariant
        MachineStatus.RUNNING -> if (isWasher) colorScheme.primary else colorScheme.tertiary
        MachineStatus.ALMOST_DONE -> semantic.almostDoneBorder
        MachineStatus.FINISHED -> semantic.finishedBorder
    }

    val targetCornerRadius = when (state.status) {
        MachineStatus.IDLE -> 16.dp
        MachineStatus.RUNNING -> 22.dp
        MachineStatus.ALMOST_DONE -> 14.dp
        MachineStatus.FINISHED -> 24.dp
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cardContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cardContent",
    )
    val borderColor by animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "cardBorder",
    )
    val cornerRadius by animateDpAsState(
        targetValue = targetCornerRadius,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "cardCorner",
    )

    val animatedProgress by animateFloatAsState(
        targetValue = state.elapsedFraction,
        animationSpec = motionScheme.slowEffectsSpec(),
        label = "cardProgress",
    )

    val borderWidth = when (state.status) {
        MachineStatus.ALMOST_DONE, MachineStatus.FINISHED -> 2.dp
        MachineStatus.RUNNING -> 1.5.dp
        MachineStatus.IDLE -> 1.dp
    }

    val statusTextForA11y = when (state.status) {
        MachineStatus.IDLE -> "空き"
        MachineStatus.RUNNING -> "稼働中 残り${MachineTimerCalculator.formatCountdown(state.remainingMillis)}"
        MachineStatus.ALMOST_DONE -> "まもなく空き 残り${MachineTimerCalculator.formatCountdown(state.remainingMillis)}"
        MachineStatus.FINISHED -> "完了 確認待ち"
    }

    Surface(
        onClick = onClick,
        modifier = modifier.semantics {
            role = Role.Button
            contentDescription = "${state.spec.fullName} $statusTextForA11y"
        },
        shape = RoundedCornerShape(cornerRadius),
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(borderWidth, borderColor),
        tonalElevation = if (state.status == MachineStatus.IDLE) 0.dp else 2.dp,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 6.dp),
        ) {
            val density = LocalDensity.current
            val compactWidth = maxWidth < 84.dp
            val fontScaleGuard = density.fontScale.coerceIn(1f, 1.35f)
            val titleFontSize = (if (compactWidth) 10.5f else 11.5f) / fontScaleGuard
            val countdownText = MachineTimerCalculator.formatCountdown(state.remainingMillis)
            val isLongCountdown = countdownText.length > 5
            val countdownFontSize = when {
                isLongCountdown && compactWidth -> 12.5f / fontScaleGuard
                isLongCountdown -> 14f / fontScaleGuard
                compactWidth -> 15f / fontScaleGuard
                else -> 17f / fontScaleGuard
            }
            val waveStroke = with(density) {
                Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            }
            val trackStroke = with(density) {
                Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            }

            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Header: Machine name + type/status icon
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = state.spec.fullNameNoSpace,
                        fontSize = titleFontSize.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Clip,
                        color = contentColor,
                    )

                    val badgeIcon = when (state.status) {
                        MachineStatus.ALMOST_DONE -> Icons.Rounded.NotificationsActive
                        MachineStatus.FINISHED -> Icons.Rounded.CheckCircle
                        else -> if (isWasher) Icons.Rounded.LocalLaundryService else Icons.Rounded.WbSunny
                    }
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        modifier = Modifier.size(if (compactWidth) 12.dp else 13.dp),
                        tint = when (state.status) {
                            MachineStatus.IDLE -> colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            else -> contentColor
                        },
                    )
                }

                // Center: Countdown or Idle/Finished state
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    when (state.status) {
                        MachineStatus.IDLE -> {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(semantic.availableContainer)
                                    .padding(horizontal = 8.dp, vertical = 3.dp),
                            ) {
                                Text(
                                    text = "空き",
                                    fontSize = (if (compactWidth) 11f else 12f).sp,
                                    fontWeight = FontWeight.Bold,
                                    color = semantic.onAvailableContainer,
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }

                        MachineStatus.RUNNING, MachineStatus.ALMOST_DONE -> {
                            Text(
                                text = countdownText,
                                style = TabularCountdownStyle.copy(
                                    fontSize = countdownFontSize.sp,
                                    color = contentColor,
                                ),
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = if (state.status == MachineStatus.ALMOST_DONE) "まもなく" else "稼働中",
                                fontSize = (9.5f / fontScaleGuard.coerceAtMost(1.2f)).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor.copy(alpha = 0.85f),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }

                        MachineStatus.FINISHED -> {
                            Text(
                                text = "完了！",
                                fontSize = (if (compactWidth) 14f else 16f).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor,
                                maxLines = 1,
                                softWrap = false,
                            )
                            Text(
                                text = if (compactWidth) "空きへ" else "空きにする",
                                fontSize = (9.5f / fontScaleGuard.coerceAtMost(1.2f)).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = contentColor.copy(alpha = 0.9f),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }

                // Footer: M3 Expressive LinearWavyProgressIndicator when active, or concise prompt
                when (state.status) {
                    MachineStatus.RUNNING, MachineStatus.ALMOST_DONE -> {
                        LinearWavyProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = if (state.status == MachineStatus.ALMOST_DONE) {
                                semantic.almostDoneBorder
                            } else if (isWasher) {
                                colorScheme.primary
                            } else {
                                colorScheme.tertiary
                            },
                            trackColor = contentColor.copy(alpha = 0.16f),
                            stroke = waveStroke,
                            trackStroke = trackStroke,
                            wavelength = WavyProgressIndicatorDefaults.LinearDeterminateWavelength,
                        )
                    }

                    MachineStatus.IDLE -> {
                        Text(
                            text = if (compactWidth) "開始" else "タップで開始",
                            fontSize = (9f / fontScaleGuard.coerceAtMost(1.15f)).sp,
                            color = colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }

                    MachineStatus.FINISHED -> {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(CircleShape)
                                .background(semantic.finishedBorder),
                        )
                    }
                }
            }
        }
    }
}
