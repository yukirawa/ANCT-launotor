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
data class LaundryExpressiveColors(
    val washerIdleContainer: Color,
    val washerIdleContent: Color,
    val washerRunningContainer: Color,
    val washerRunningContent: Color,
    val washerRunningTrack: Color,
    val washerAccent: Color,
    val washerOnAccent: Color,
    val washerAccentSoft: Color,
    val washerOnAccentSoft: Color,
    val dryerIdleContainer: Color,
    val dryerIdleContent: Color,
    val dryerRunningContainer: Color,
    val dryerRunningContent: Color,
    val dryerRunningTrack: Color,
    val dryerAccent: Color,
    val dryerOnAccent: Color,
    val dryerAccentSoft: Color,
    val dryerOnAccentSoft: Color,
    val almostDoneContainer: Color,
    val almostDoneContent: Color,
    val almostDoneTrack: Color,
    val finishedContainer: Color,
    val finishedContent: Color,
)

private val LightExpressiveColors = LaundryExpressiveColors(
    washerIdleContainer = WasherIdleContainerLight,
    washerIdleContent = WasherIdleContentLight,
    washerRunningContainer = WasherRunningContainerLight,
    washerRunningContent = WasherRunningContentLight,
    washerRunningTrack = WasherRunningTrackLight,
    washerAccent = WasherAccentLight,
    washerOnAccent = WasherOnAccentLight,
    washerAccentSoft = WasherAccentSoftLight,
    washerOnAccentSoft = WasherOnAccentSoftLight,
    dryerIdleContainer = DryerIdleContainerLight,
    dryerIdleContent = DryerIdleContentLight,
    dryerRunningContainer = DryerRunningContainerLight,
    dryerRunningContent = DryerRunningContentLight,
    dryerRunningTrack = DryerRunningTrackLight,
    dryerAccent = DryerAccentLight,
    dryerOnAccent = DryerOnAccentLight,
    dryerAccentSoft = DryerAccentSoftLight,
    dryerOnAccentSoft = DryerOnAccentSoftLight,
    almostDoneContainer = AlmostDoneContainerLight,
    almostDoneContent = AlmostDoneContentLight,
    almostDoneTrack = AlmostDoneTrackLight,
    finishedContainer = FinishedContainerLight,
    finishedContent = FinishedContentLight,
)

private val DarkExpressiveColors = LaundryExpressiveColors(
    washerIdleContainer = WasherIdleContainerDark,
    washerIdleContent = WasherIdleContentDark,
    washerRunningContainer = WasherRunningContainerDark,
    washerRunningContent = WasherRunningContentDark,
    washerRunningTrack = WasherRunningTrackDark,
    washerAccent = WasherAccentDark,
    washerOnAccent = WasherOnAccentDark,
    washerAccentSoft = WasherAccentSoftDark,
    washerOnAccentSoft = WasherOnAccentSoftDark,
    dryerIdleContainer = DryerIdleContainerDark,
    dryerIdleContent = DryerIdleContentDark,
    dryerRunningContainer = DryerRunningContainerDark,
    dryerRunningContent = DryerRunningContentDark,
    dryerRunningTrack = DryerRunningTrackDark,
    dryerAccent = DryerAccentDark,
    dryerOnAccent = DryerOnAccentDark,
    dryerAccentSoft = DryerAccentSoftDark,
    dryerOnAccentSoft = DryerOnAccentSoftDark,
    almostDoneContainer = AlmostDoneContainerDark,
    almostDoneContent = AlmostDoneContentDark,
    almostDoneTrack = AlmostDoneTrackDark,
    finishedContainer = FinishedContainerDark,
    finishedContent = FinishedContentDark,
)

val LocalLaundryExpressiveColors = staticCompositionLocalOf { LightExpressiveColors }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LaunotorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dynamicColor) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else if (darkTheme) {
        LaunotorDarkColorScheme
    } else {
        LaunotorLightColorScheme
    }

    val expressiveColors = if (darkTheme) DarkExpressiveColors else LightExpressiveColors

    CompositionLocalProvider(LocalLaundryExpressiveColors provides expressiveColors) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = LaunotorExpressiveShapes,
            typography = LaunotorExpressiveTypography,
            content = content,
        )
    }
}
