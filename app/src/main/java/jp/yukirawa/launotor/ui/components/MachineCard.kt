package jp.yukirawa.launotor.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.LocalLaundryService
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import jp.yukirawa.launotor.domain.model.MachineState
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineTimerCalculator
import jp.yukirawa.launotor.domain.model.MachineType
import jp.yukirawa.launotor.ui.theme.LocalLaundryExpressiveColors
import jp.yukirawa.launotor.ui.theme.TabularCountdownStyle

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MachineCard(
    state: MachineState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val expressive = LocalLaundryExpressiveColors.current
    val motionScheme = MaterialTheme.motionScheme
    val isWasher = state.spec.type == MachineType.WASHER

    val targetContainerColor = when (state.status) {
        MachineStatus.IDLE -> if (isWasher) expressive.washerIdleContainer else expressive.dryerIdleContainer
        MachineStatus.RUNNING -> if (isWasher) expressive.washerRunningContainer else expressive.dryerRunningContainer
        MachineStatus.ALMOST_DONE -> expressive.almostDoneContainer
        MachineStatus.FINISHED -> expressive.finishedContainer
    }

    val targetContentColor = when (state.status) {
        MachineStatus.IDLE -> if (isWasher) expressive.washerIdleContent else expressive.dryerIdleContent
        MachineStatus.RUNNING -> if (isWasher) expressive.washerRunningContent else expressive.dryerRunningContent
        MachineStatus.ALMOST_DONE -> expressive.almostDoneContent
        MachineStatus.FINISHED -> expressive.finishedContent
    }

    val targetProgressColor = when (state.status) {
        MachineStatus.ALMOST_DONE -> expressive.almostDoneTrack
        MachineStatus.RUNNING -> if (isWasher) expressive.washerRunningTrack else expressive.dryerRunningTrack
        else -> targetContentColor
    }

    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "shapeContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "shapeContent",
    )
    val progressColor by animateColorAsState(
        targetValue = targetProgressColor,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "shapeProgress",
    )
    val animatedProgress by animateFloatAsState(
        targetValue = state.elapsedFraction,
        animationSpec = motionScheme.slowEffectsSpec(),
        label = "shapeArcProgress",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.92f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "shapePressScale",
    )

    val shape = rememberAnimatedMachineShape(
        type = state.spec.type,
        status = state.status,
    )

    val statusTextForA11y = when (state.status) {
        MachineStatus.IDLE -> "空き"
        MachineStatus.RUNNING -> "稼働中 残り${MachineTimerCalculator.formatCompactRemaining(state.remainingMillis)}"
        MachineStatus.ALMOST_DONE -> "まもなく空き 残り${MachineTimerCalculator.formatCompactRemaining(state.remainingMillis)}"
        MachineStatus.FINISHED -> "完了"
    }

    val typeIcon = if (isWasher) Icons.Rounded.LocalLaundryService else Icons.Rounded.WbSunny

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .semantics {
                role = Role.Button
                contentDescription = "${state.spec.fullName} $statusTextForA11y"
            },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = null,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            val density = LocalDensity.current
            val tileSize = minOf(maxWidth, maxHeight)
            val fontScaleGuard = density.fontScale.coerceIn(1f, 1.35f)
            val isCompact = tileSize < 82.dp

            if (state.status == MachineStatus.RUNNING || state.status == MachineStatus.ALMOST_DONE) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = (if (isCompact) 3.dp else 3.5.dp).toPx()
                    val inset = size.minDimension * PROGRESS_ARC_INSET_FRACTION
                    val arcSize = Size(
                        width = size.width - inset * 2f,
                        height = size.height - inset * 2f,
                    )
                    val topLeft = Offset(inset, inset)
                    drawArc(
                        color = contentColor.copy(alpha = 0.18f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = 360f * (1f - animatedProgress).coerceIn(0.02f, 1f),
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round),
                    )
                }
            }

            when (state.status) {
                MachineStatus.IDLE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(6.dp),
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = null,
                            modifier = Modifier.size(if (isCompact) 20.dp else 22.dp),
                            tint = contentColor,
                        )
                        Text(
                            text = state.spec.number.toString(),
                            style = TabularCountdownStyle.copy(
                                fontSize = ((if (isCompact) 17f else 19f) / fontScaleGuard).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor,
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                MachineStatus.RUNNING, MachineStatus.ALMOST_DONE -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.padding(8.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = typeIcon,
                                contentDescription = null,
                                modifier = Modifier.size(if (isCompact) 11.dp else 13.dp),
                                tint = contentColor.copy(alpha = 0.88f),
                            )
                            Text(
                                text = state.spec.number.toString(),
                                fontSize = ((if (isCompact) 11f else 12.5f) / fontScaleGuard).sp,
                                fontWeight = FontWeight.Bold,
                                color = contentColor.copy(alpha = 0.9f),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Text(
                            text = MachineTimerCalculator.formatCompactRemaining(state.remainingMillis),
                            style = TabularCountdownStyle.copy(
                                fontSize = ((if (isCompact) 16f else 18f) / fontScaleGuard).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor,
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }

                MachineStatus.FINISHED -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                        modifier = Modifier.padding(6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            modifier = Modifier.size(if (isCompact) 22.dp else 26.dp),
                            tint = contentColor,
                        )
                        Text(
                            text = state.spec.number.toString(),
                            style = TabularCountdownStyle.copy(
                                fontSize = ((if (isCompact) 15f else 17f) / fontScaleGuard).sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = contentColor,
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}
