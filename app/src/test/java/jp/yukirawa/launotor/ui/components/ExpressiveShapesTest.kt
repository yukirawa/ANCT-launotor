package jp.yukirawa.launotor.ui.components

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import jp.yukirawa.launotor.domain.model.MachineStatus
import jp.yukirawa.launotor.domain.model.MachineType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpressiveShapesTest {

    @Test
    fun machineShapesAndMorphsProduceNonEmptyCenteredOutlines() {
        val density = Density(2f)
        val size = Size(200f, 200f)

        MachineType.entries.forEach { type ->
            val statuses = MachineStatus.entries
            for (i in statuses.indices) {
                val from = machinePolygon(type, statuses[i])
                val to = machinePolygon(type, statuses[(i + 1) % statuses.size])
                val morph = Morph(from, to)

                listOf(0f, 0.5f, 1f).forEach { progress ->
                    val outline = MorphPolygonShape(morph, progress).createOutline(
                        size = size,
                        layoutDirection = LayoutDirection.Ltr,
                        density = density,
                    ) as Outline.Generic
                    val bounds = outline.path.getBounds()
                    assertFalse("Empty bounds for $type ${statuses[i]}->$progress", bounds.isEmpty)
                    assertTrue("Width too small: ${bounds.width}", bounds.width > 120f)
                    assertTrue("Height too small: ${bounds.height}", bounds.height > 120f)
                }
            }
        }
    }
}
