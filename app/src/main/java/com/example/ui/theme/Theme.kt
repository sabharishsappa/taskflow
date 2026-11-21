package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = PinePrimary,
    onPrimary = PineOnPrimary,
    primaryContainer = PinePrimaryContainer,
    onPrimaryContainer = PineOnPrimaryContainer,
    secondary = TerracottaSecondary,
    onSecondary = TerracottaOnSecondary,
    secondaryContainer = TerracottaSecondaryContainer,
    onSecondaryContainer = TerracottaOnSecondaryContainer,
    tertiary = HoneyTertiary,
    onTertiary = HoneyOnTertiary,
    tertiaryContainer = HoneyTertiaryContainer,
    onTertiaryContainer = HoneyOnTertiaryContainer,
    background = LinenBackground,
    onBackground = InkOnBackground,
    surface = LinenSurface,
    onSurface = InkOnSurface,
    surfaceVariant = ParchmentSurfaceVariant,
    onSurfaceVariant = SlateOnSurfaceVariant,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    outline = WarmOutline,
    outlineVariant = SoftOutlineVariant,
    error = ErrorClay,
    onError = OnErrorLight,
    errorContainer = ErrorContainerLight,
    onErrorContainer = OnErrorContainerLight,
)

private val DarkColorScheme = darkColorScheme(
    primary = PinePrimaryDark,
    onPrimary = PineOnPrimaryDark,
    primaryContainer = PinePrimaryContainerDark,
    onPrimaryContainer = PineOnPrimaryContainerDark,
    secondary = TerracottaSecondaryDark,
    onSecondary = TerracottaOnSecondaryDark,
    secondaryContainer = TerracottaSecondaryContainerDark,
    onSecondaryContainer = TerracottaOnSecondaryContainerDark,
    tertiary = HoneyTertiaryDark,
    onTertiary = HoneyOnTertiaryDark,
    tertiaryContainer = HoneyTertiaryContainerDark,
    onTertiaryContainer = HoneyOnTertiaryContainerDark,
    background = ForestBackgroundDark,
    onBackground = CreamOnBackgroundDark,
    surface = ForestSurfaceDark,
    onSurface = CreamOnSurfaceDark,
    surfaceVariant = ForestSurfaceVariantDark,
    onSurfaceVariant = SageMutedOnSurfaceDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerDark,
    surfaceContainerHigh = SurfaceContainerHighDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = ErrorClayDark,
    onError = OnErrorDark,
    errorContainer = ErrorContainerDark,
    onErrorContainer = OnErrorContainerDark,
)

val DayloomShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

@Composable
fun DayloomTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = DayloomShapes,
        content = content
    )
}
