package jp.yukirawa.launotor.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DefaultTypography = Typography()

val LaunotorExpressiveTypography = Typography(
    displaySmall = DefaultTypography.displaySmall.copy(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    ),
    headlineSmall = DefaultTypography.headlineSmall.copy(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    ),
    titleLarge = DefaultTypography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    ),
    titleMedium = DefaultTypography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    ),
    titleSmall = DefaultTypography.titleSmall.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    ),
    bodyLarge = DefaultTypography.bodyLarge.copy(
        fontFeatureSettings = "tnum",
    ),
    bodyMedium = DefaultTypography.bodyMedium.copy(
        fontFeatureSettings = "tnum",
    ),
    bodySmall = DefaultTypography.bodySmall.copy(
        fontFeatureSettings = "tnum",
    ),
    labelLarge = DefaultTypography.labelLarge.copy(
        fontWeight = FontWeight.SemiBold,
        fontFeatureSettings = "tnum",
    ),
    labelMedium = DefaultTypography.labelMedium.copy(
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
    labelSmall = DefaultTypography.labelSmall.copy(
        fontWeight = FontWeight.Medium,
        fontFeatureSettings = "tnum",
    ),
    headlineSmallEmphasized = DefaultTypography.headlineSmall.copy(
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum",
    ),
    titleLargeEmphasized = DefaultTypography.titleLarge.copy(
        fontWeight = FontWeight.ExtraBold,
        fontFeatureSettings = "tnum",
    ),
    titleMediumEmphasized = DefaultTypography.titleMedium.copy(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    ),
    labelLargeEmphasized = DefaultTypography.labelLarge.copy(
        fontWeight = FontWeight.Bold,
        fontFeatureSettings = "tnum",
    ),
)

val TabularCountdownStyle = TextStyle(
    fontFamily = FontFamily.SansSerif,
    fontWeight = FontWeight.ExtraBold,
    fontFeatureSettings = "tnum",
    letterSpacing = (-0.3).sp,
)
