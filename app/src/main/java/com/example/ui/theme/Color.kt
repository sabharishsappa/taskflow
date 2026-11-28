package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Brand Core Palette ("Prism Pop" — vibrant accents on warm tactile surfaces)
val PrismCoral = Color(0xFFFF5A5F)
val PrismCoralDark = Color(0xFFFF8589)
val PrismCoralContainer = Color(0xFFFFE5E6)
val PrismCoralContainerDark = Color(0xFF4A181B)

val PrismTeal = Color(0xFF00B894)
val PrismTealDark = Color(0xFF4CE0BE)
val PrismTealContainer = Color(0xFFD9FAF2)
val PrismTealContainerDark = Color(0xFF0E3B31)

val PrismAmber = Color(0xFFF59E0B)
val PrismAmberDark = Color(0xFFFFCA58)
val PrismAmberContainer = Color(0xFFFFF2D6)
val PrismAmberContainerDark = Color(0xFF443009)

val PrismViolet = Color(0xFF6C5CE7)
val PrismVioletDark = Color(0xFFA79BFF)
val PrismVioletContainer = Color(0xFFEBE8FF)
val PrismVioletContainerDark = Color(0xFF28205C)

val PrismSky = Color(0xFF0EA5E9)
val PrismSkyDark = Color(0xFF5CC8FA)
val PrismSkyContainer = Color(0xFFDDF4FF)
val PrismSkyContainerDark = Color(0xFF0B354D)

val PrismBerry = Color(0xFFEC4899)
val PrismBerryDark = Color(0xFFFF8AC4)
val PrismBerryContainer = Color(0xFFFFE2F1)
val PrismBerryContainerDark = Color(0xFF4D1635)

// Light Surface Tokens (warm ivory alabaster so colors pop without sterile gray)
val WarmBackground = Color(0xFFFAF8F5)
val WarmSurface = Color(0xFFFFFFFF)
val WarmSurfaceVariant = Color(0xFFF2EFEA)
val WarmSurfaceContainerLow = Color(0xFFFDFBF8)
val WarmSurfaceContainer = Color(0xFFF5F1EB)
val WarmSurfaceContainerHigh = Color(0xFFECE6DD)
val InkPrimary = Color(0xFF181D3D)
val InkSecondary = Color(0xFF535A75)
val WarmOutline = Color(0xFFDAD4CA)
val WarmOutlineVariant = Color(0xFFEAE4DC)

// Dark Surface Tokens (deep midnight indigo matching launcher icon)
val MidnightBackground = Color(0xFF111427)
val MidnightSurface = Color(0xFF181D36)
val MidnightSurfaceVariant = Color(0xFF232948)
val MidnightSurfaceContainerLow = Color(0xFF151930)
val MidnightSurfaceContainer = Color(0xFF1E2442)
val MidnightSurfaceContainerHigh = Color(0xFF283055)
val StarlightPrimary = Color(0xFFF6F7FF)
val StarlightSecondary = Color(0xFFB5BCD9)
val MidnightOutline = Color(0xFF3A436D)
val MidnightOutlineVariant = Color(0xFF2B3254)

data class TaskAccentPalette(
    val accent: Color,
    val softContainer: Color,
    val onSoftContainer: Color,
    val badgeBorder: Color
)
