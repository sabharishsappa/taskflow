package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography as M3Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PrismViolet,
    onPrimary = Color.White,
    primaryContainer = PrismVioletContainer,
    onPrimaryContainer = Color(0xFF22146E),
    secondary = PrismTeal,
    onSecondary = Color.White,
    secondaryContainer = PrismTealContainer,
    onSecondaryContainer = Color(0xFF003D31),
    tertiary = PrismCoral,
    onTertiary = Color.White,
    tertiaryContainer = PrismCoralContainer,
    onTertiaryContainer = Color(0xFF5C0B10),
    background = WarmBackground,
    onBackground = InkPrimary,
    surface = WarmSurface,
    onSurface = InkPrimary,
    surfaceVariant = WarmSurfaceVariant,
    onSurfaceVariant = InkSecondary,
    surfaceContainerLowest = WarmSurface,
    surfaceContainerLow = WarmSurfaceContainerLow,
    surfaceContainer = WarmSurfaceContainer,
    surfaceContainerHigh = WarmSurfaceContainerHigh,
    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,
    error = Color(0xFFDC2626),
    onError = Color.White,
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D)
)

private val DarkColorScheme = darkColorScheme(
    primary = PrismVioletDark,
    onPrimary = Color(0xFF1A1247),
    primaryContainer = PrismVioletContainerDark,
    onPrimaryContainer = Color(0xFFE5E0FF),
    secondary = PrismTealDark,
    onSecondary = Color(0xFF00382C),
    secondaryContainer = PrismTealContainerDark,
    onSecondaryContainer = Color(0xFFB5F9E9),
    tertiary = PrismCoralDark,
    onTertiary = Color(0xFF520B10),
    tertiaryContainer = PrismCoralContainerDark,
    onTertiaryContainer = Color(0xFFFFD6D8),
    background = MidnightBackground,
    onBackground = StarlightPrimary,
    surface = MidnightSurface,
    onSurface = StarlightPrimary,
    surfaceVariant = MidnightSurfaceVariant,
    onSurfaceVariant = StarlightSecondary,
    surfaceContainerLowest = MidnightBackground,
    surfaceContainerLow = MidnightSurfaceContainerLow,
    surfaceContainer = MidnightSurfaceContainer,
    surfaceContainerHigh = MidnightSurfaceContainerHigh,
    outline = MidnightOutline,
    outlineVariant = MidnightOutlineVariant,
    error = Color(0xFFF87171),
    onError = Color(0xFF450A0A),
    errorContainer = Color(0xFF5C1515),
    onErrorContainer = Color(0xFFFECACA)
)

val PrismShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

private fun TextStyle.scaledBy(scale: Float): TextStyle {
    if (scale == 1f) return this
    return copy(
        fontSize = if (fontSize != TextUnit.Unspecified) fontSize * scale else fontSize,
        lineHeight = if (lineHeight != TextUnit.Unspecified) lineHeight * scale else lineHeight
    )
}

private fun M3Typography.scaledBy(scale: Float): M3Typography {
    if (scale == 1f) return this
    return M3Typography(
        displayLarge = displayLarge.scaledBy(scale),
        displayMedium = displayMedium.scaledBy(scale),
        displaySmall = displaySmall.scaledBy(scale),
        headlineLarge = headlineLarge.scaledBy(scale),
        headlineMedium = headlineMedium.scaledBy(scale),
        headlineSmall = headlineSmall.scaledBy(scale),
        titleLarge = titleLarge.scaledBy(scale),
        titleMedium = titleMedium.scaledBy(scale),
        titleSmall = titleSmall.scaledBy(scale),
        bodyLarge = bodyLarge.scaledBy(scale),
        bodyMedium = bodyMedium.scaledBy(scale),
        bodySmall = bodySmall.scaledBy(scale),
        labelLarge = labelLarge.scaledBy(scale),
        labelMedium = labelMedium.scaledBy(scale),
        labelSmall = labelSmall.scaledBy(scale)
    )
}

val LocalIsDarkTheme = staticCompositionLocalOf { false }

@Composable
fun PrismTaskTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    textScale: Float = 1f,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val scaledTypography = Typography.scaledBy(textScale)

    CompositionLocalProvider(LocalIsDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = scaledTypography,
            shapes = PrismShapes,
            content = content
        )
    }
}
