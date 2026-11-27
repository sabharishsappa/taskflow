package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val DaybookLightColorScheme = lightColorScheme(
    primary = ForestSage,
    onPrimary = OnForestSage,
    primaryContainer = ForestSageContainer,
    onPrimaryContainer = OnForestSageContainer,
    secondary = Terracotta,
    onSecondary = OnTerracotta,
    secondaryContainer = TerracottaContainer,
    onSecondaryContainer = OnTerracottaContainer,
    tertiary = AntiquedBrass,
    onTertiary = OnAntiquedBrass,
    tertiaryContainer = AntiquedBrassContainer,
    onTertiaryContainer = OnAntiquedBrassContainer,
    background = WarmParchment,
    onBackground = WarmInk,
    surface = WarmParchment,
    onSurface = WarmInk,
    surfaceVariant = LinenCard,
    onSurfaceVariant = MutedStone,
    surfaceContainerLowest = OnForestSage,
    surfaceContainerLow = LinenCard,
    surfaceContainer = LinenContainer,
    surfaceContainerHigh = LinenContainerHigh,
    outline = ParchmentOutline,
    outlineVariant = ParchmentOutlineVariant,
    error = CrimsonInk,
    onError = OnCrimsonInk,
    errorContainer = CrimsonContainer,
    onErrorContainer = OnCrimsonContainer
)

private val ColorErrorDark = androidx.compose.ui.graphics.Color(0xFFF2B8B5)
private val ColorOnErrorDark = androidx.compose.ui.graphics.Color(0xFF601410)
private val ColorErrorContainerDark = androidx.compose.ui.graphics.Color(0xFF8C1D18)
private val ColorOnErrorContainerDark = androidx.compose.ui.graphics.Color(0xFFF9DEDC)

private val DaybookDarkColorScheme = darkColorScheme(
    primary = SageMist,
    onPrimary = OnSageMist,
    primaryContainer = DeepSageContainer,
    onPrimaryContainer = OnDeepSageContainer,
    secondary = WarmClay,
    onSecondary = OnWarmClay,
    secondaryContainer = DeepClayContainer,
    onSecondaryContainer = OnDeepClayContainer,
    tertiary = SoftGold,
    onTertiary = OnSoftGold,
    tertiaryContainer = DeepGoldContainer,
    onTertiaryContainer = OnDeepGoldContainer,
    background = EspressoCanvas,
    onBackground = WarmCreamText,
    surface = EspressoCanvas,
    onSurface = WarmCreamText,
    surfaceVariant = EspressoSurfaceLow,
    onSurfaceVariant = MutedSandText,
    surfaceContainerLowest = EspressoCanvas,
    surfaceContainerLow = EspressoSurfaceLow,
    surfaceContainer = EspressoSurface,
    surfaceContainerHigh = EspressoSurfaceHigh,
    outline = EspressoOutline,
    outlineVariant = EspressoOutlineVariant,
    error = ColorErrorDark,
    onError = ColorOnErrorDark,
    errorContainer = ColorErrorContainerDark,
    onErrorContainer = ColorOnErrorContainerDark
)

val DaybookShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun DaybookTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DaybookDarkColorScheme else DaybookLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = DaybookTypography,
        shapes = DaybookShapes,
        content = content
    )
}
