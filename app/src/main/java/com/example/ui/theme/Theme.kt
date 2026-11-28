package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val JournalDarkColorScheme = darkColorScheme(
    primary = JournalPeriwinkleDark,
    onPrimary = JournalPeriwinkleOnDark,
    primaryContainer = JournalIndigoContainerDark,
    onPrimaryContainer = JournalLavenderOnContainerDark,
    secondary = JournalPlumDark,
    onSecondary = Color(0xFF211147),
    secondaryContainer = JournalPlumContainerDark,
    onSecondaryContainer = JournalPlumOnContainerDark,
    tertiary = JournalAmberDark,
    onTertiary = Color(0xFF3E2200),
    tertiaryContainer = JournalAmberContainerDark,
    onTertiaryContainer = JournalAmberOnContainerDark,
    background = CanvasObsidianDark,
    onBackground = InkPrimaryDark,
    surface = CanvasObsidianDark,
    onSurface = InkPrimaryDark,
    surfaceVariant = SurfaceElevatedDark,
    onSurfaceVariant = InkSecondaryDark,
    surfaceContainerLowest = Color(0xFF13131A),
    surfaceContainerLow = SurfaceCardDark,
    surfaceContainer = SurfaceCardDark,
    surfaceContainerHigh = SurfaceElevatedDark,
    surfaceContainerHighest = SurfacePillDark,
    outline = HairlineDark,
    outlineVariant = Color(0xFF252533),
    error = ErrorCoralDark,
    onError = Color(0xFF3B0B0B),
    errorContainer = ErrorContainerDark,
    onErrorContainer = Color(0xFFFECACA),
)

private val JournalLightColorScheme = lightColorScheme(
    primary = JournalIndigo,
    onPrimary = Color.White,
    primaryContainer = JournalLavenderSoft,
    onPrimaryContainer = JournalLavenderInk,
    secondary = JournalPlum,
    onSecondary = Color.White,
    secondaryContainer = JournalPlumContainer,
    onSecondaryContainer = JournalPlumInk,
    tertiary = JournalAmber,
    onTertiary = Color.White,
    tertiaryContainer = JournalAmberContainer,
    onTertiaryContainer = JournalAmberInk,
    background = CanvasWarmLight,
    onBackground = InkPrimaryLight,
    surface = CanvasWarmLight,
    onSurface = InkPrimaryLight,
    surfaceVariant = SurfaceMutedLight,
    onSurfaceVariant = InkSecondaryLight,
    surfaceContainerLowest = SurfaceCardLight,
    surfaceContainerLow = Color(0xFFFDFCFA),
    surfaceContainer = SurfaceCardLight,
    surfaceContainerHigh = SurfaceMutedLight,
    surfaceContainerHighest = SurfacePillLight,
    outline = Color(0xFFD6D3CB),
    outlineVariant = HairlineLight,
    error = ErrorCrimsonLight,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = Color(0xFF7F1D1D),
)

val JournalShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun FolioJournalTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) JournalDarkColorScheme else JournalLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = JournalShapes,
        content = content
    )
}
