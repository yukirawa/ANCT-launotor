package jp.yukirawa.launotor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class LaundrySemanticColors(
    val availableContainer: Color,
    val onAvailableContainer: Color,
    val almostDoneContainer: Color,
    val onAlmostDoneContainer: Color,
    val almostDoneBorder: Color,
    val finishedContainer: Color,
    val onFinishedContainer: Color,
    val finishedBorder: Color,
)

private val LightSemanticColors = LaundrySemanticColors(
    availableContainer = StatusAvailableLight,
    onAvailableContainer = StatusOnAvailableLight,
    almostDoneContainer = StatusAlmostDoneLight,
    onAlmostDoneContainer = StatusOnAlmostDoneLight,
    almostDoneBorder = StatusAlmostDoneBorderLight,
    finishedContainer = StatusFinishedLight,
    onFinishedContainer = StatusOnFinishedLight,
    finishedBorder = StatusFinishedBorderLight,
)

private val DarkSemanticColors = LaundrySemanticColors(
    availableContainer = StatusAvailableDark,
    onAvailableContainer = StatusOnAvailableDark,
    almostDoneContainer = StatusAlmostDoneDark,
    onAlmostDoneContainer = StatusOnAlmostDoneDark,
    almostDoneBorder = StatusAlmostDoneBorderDark,
    finishedContainer = StatusFinishedDark,
    onFinishedContainer = StatusOnFinishedDark,
    finishedBorder = StatusFinishedBorderDark,
)

val LocalLaundrySemanticColors = staticCompositionLocalOf { LightSemanticColors }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LaunotorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> LaunotorDarkColorScheme
        else -> LaunotorLightColorScheme
    }

    val semanticColors = if (darkTheme) DarkSemanticColors else LightSemanticColors

    CompositionLocalProvider(LocalLaundrySemanticColors provides semanticColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = LaunotorExpressiveShapes,
            typography = LaunotorExpressiveTypography,
            content = content,
        )
    }
}
