package jp.yukirawa.launotor.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.center
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineType

internal const val PROGRESS_ARC_INSET_FRACTION = 0.155f

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val path = Path()
        morph.toPath(progress = progress.coerceIn(0f, 1f), path = path)
        val matrix = Matrix().apply {
            scale(x = size.width, y = size.height)
        }
        path.transform(matrix)
        val centerOffset = size.center - path.getBounds().center
        path.translate(centerOffset)
        return Outline.Generic(path)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun machinePolygon(
    type: MachineType,
    status: MachineStatus,
): RoundedPolygon {
    return when (status) {
        MachineStatus.IDLE -> when (type) {
            MachineType.WASHER -> MaterialShapes.Square
            MachineType.DRYER -> MaterialShapes.Circle
        }
        MachineStatus.RUNNING -> when (type) {
            MachineType.WASHER -> MaterialShapes.Cookie9Sided
            MachineType.DRYER -> MaterialShapes.Cookie6Sided
        }
        MachineStatus.ALMOST_DONE -> MaterialShapes.Cookie12Sided
        MachineStatus.FINISHED -> MaterialShapes.Sunny
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun rememberAnimatedMachineShape(
    type: MachineType,
    status: MachineStatus,
): Shape {
    val targetPolygon = machinePolygon(type = type, status = status)
    val motionScheme = MaterialTheme.motionScheme

    var fromPolygon by remember(type) { mutableStateOf(targetPolygon) }
    var toPolygon by remember(type) { mutableStateOf(targetPolygon) }
    val progress = remember(type) { Animatable(1f) }

    LaunchedEffect(targetPolygon) {
        if (targetPolygon !== toPolygon) {
            fromPolygon = toPolygon
            toPolygon = targetPolygon
            progress.snapTo(0f)
            progress.animateTo(
                targetValue = 1f,
                animationSpec = motionScheme.defaultSpatialSpec(),
            )
        }
    }

    val morph = remember(fromPolygon, toPolygon) {
        Morph(start = fromPolygon, end = toPolygon)
    }
    val staticShape = toPolygon.toShape()
    val currentProgress = progress.value

    return if (currentProgress >= 0.999f) {
        staticShape
    } else {
        remember(morph, currentProgress) {
            MorphPolygonShape(morph = morph, progress = currentProgress)
        }
    }
}
