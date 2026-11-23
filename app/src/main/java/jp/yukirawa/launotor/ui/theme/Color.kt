package jp.yukirawa.launotor.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Expressive aquatic-teal & warm-amber palette crafted for Launotor
val CyanPrimaryLight = Color(0xFF00677F)
val OnCyanPrimaryLight = Color(0xFFFFFFFF)
val CyanPrimaryContainerLight = Color(0xFFB5EBFF)
val OnCyanPrimaryContainerLight = Color(0xFF001F28)

val TealSecondaryLight = Color(0xFF3E6373)
val OnTealSecondaryLight = Color(0xFFFFFFFF)
val TealSecondaryContainerLight = Color(0xFFC2E8FA)
val OnTealSecondaryContainerLight = Color(0xFF001F29)

val CoralTertiaryLight = Color(0xFF9C4327)
val OnCoralTertiaryLight = Color(0xFFFFFFFF)
val CoralTertiaryContainerLight = Color(0xFFFFDBD0)
val OnCoralTertiaryContainerLight = Color(0xFF3A0B00)

val SurfaceLight = Color(0xFFF6FAFD)
val OnSurfaceLight = Color(0xFF171C1F)
val SurfaceVariantLight = Color(0xFFDCE4E8)
val OnSurfaceVariantLight = Color(0xFF40484C)
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF0F4F8)
val SurfaceContainerLight = Color(0xFFEAEFF3)
val SurfaceContainerHighLight = Color(0xFFE4E9ED)
val SurfaceContainerHighestLight = Color(0xFFDEE3E7)
val OutlineLight = Color(0xFF70787D)
val OutlineVariantLight = Color(0xFFC0C8CD)

val CyanPrimaryDark = Color(0xFF6DD4F5)
val OnCyanPrimaryDark = Color(0xFF003544)
val CyanPrimaryContainerDark = Color(0xFF004E61)
val OnCyanPrimaryContainerDark = Color(0xFFB5EBFF)

val TealSecondaryDark = Color(0xFFA6CCDD)
val OnTealSecondaryDark = Color(0xFF093543)
val TealSecondaryContainerDark = Color(0xFF254B5A)
val OnTealSecondaryContainerDark = Color(0xFFC2E8FA)

val CoralTertiaryDark = Color(0xFFFFB59E)
val OnCoralTertiaryDark = Color(0xFF5D1902)
val CoralTertiaryContainerDark = Color(0xFF7D2C12)
val OnCoralTertiaryContainerDark = Color(0xFFFFDBD0)

val SurfaceDark = Color(0xFF0F1417)
val OnSurfaceDark = Color(0xFFDFE3E7)
val SurfaceVariantDark = Color(0xFF40484C)
val OnSurfaceVariantDark = Color(0xFFC0C8CD)
val SurfaceContainerLowestDark = Color(0xFF0A0F12)
val SurfaceContainerLowDark = Color(0xFF171C1F)
val SurfaceContainerDark = Color(0xFF1B2023)
val SurfaceContainerHighDark = Color(0xFF262B2E)
val SurfaceContainerHighestDark = Color(0xFF303639)
val OutlineDark = Color(0xFF8A9297)
val OutlineVariantDark = Color(0xFF40484C)

// Semantic status tokens
val StatusAvailableLight = Color(0xFFE0F5EC)
val StatusOnAvailableLight = Color(0xFF0B5237)
val StatusAvailableDark = Color(0xFF123D2D)
val StatusOnAvailableDark = Color(0xFF9DE6C4)

val StatusAlmostDoneLight = Color(0xFFFFECC7)
val StatusOnAlmostDoneLight = Color(0xFF6B3E00)
val StatusAlmostDoneBorderLight = Color(0xFFE08600)
val StatusAlmostDoneDark = Color(0xFF4F3100)
val StatusOnAlmostDoneDark = Color(0xFFFFD899)
val StatusAlmostDoneBorderDark = Color(0xFFFFB74D)

val StatusFinishedLight = Color(0xFFD6F7E8)
val StatusOnFinishedLight = Color(0xFF005136)
val StatusFinishedBorderLight = Color(0xFF00875A)
val StatusFinishedDark = Color(0xFF0D4430)
val StatusOnFinishedDark = Color(0xFF8CEBC0)
val StatusFinishedBorderDark = Color(0xFF34D399)

val LaunotorLightColorScheme = lightColorScheme(
    primary = CyanPrimaryLight,
    onPrimary = OnCyanPrimaryLight,
    primaryContainer = CyanPrimaryContainerLight,
    onPrimaryContainer = OnCyanPrimaryContainerLight,
    secondary = TealSecondaryLight,
    onSecondary = OnTealSecondaryLight,
    secondaryContainer = TealSecondaryContainerLight,
    onSecondaryContainer = OnTealSecondaryContainerLight,
    tertiary = CoralTertiaryLight,
    onTertiary = OnCoralTertiaryLight,
    tertiaryContainer = CoralTertiaryContainerLight,
    onTertiaryContainer = OnCoralTertiaryContainerLight,
    background = SurfaceLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
)

val LaunotorDarkColorScheme = darkColorScheme(
    primary = CyanPrimaryDark,
    onPrimary = OnCyanPrimaryDark,
    primaryContainer = CyanPrimaryContainerDark,
    onPrimaryContainer = OnCyanPrimaryContainerDark,
    secondary = TealSecondaryDark,
    onSecondary = OnTealSecondaryDark,
    secondaryContainer = TealSecondaryContainerDark,
    onSecondaryContainer = OnTealSecondaryContainerDark,
    tertiary = CoralTertiaryDark,
    onTertiary = OnCoralTertiaryDark,
    tertiaryContainer = CoralTertiaryContainerDark,
    onTertiaryContainer = OnCoralTertiaryContainerDark,
    background = SurfaceDark,
    onBackground = OnSurfaceDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
)
