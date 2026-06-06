package com.cheradip.ailanguagetutor.ui.theme

import androidx.compose.ui.graphics.Color

// ── Core brand palette ──────────────────────────────────────────────────────
val CheradipTeal = Color(0xFF00897B)
val CheradipForestGreen = Color(0xFF228B22)
val CheradipGolden = Color(0xFFD4AF37)
val CheradipBlack = Color(0xFF000000)
val CheradipWhite = Color(0xFFFFFFFF)
val CheradipBlue = Color(0xFF1565C0)
val CheradipDarkRed = Color(0xFF8B0000)
val CheradipSkyBlue = Color(0xFF87CEEB)

// ── Light theme tokens ──────────────────────────────────────────────────────
val CheradipPrimary = CheradipTeal
val CheradipOnPrimary = CheradipWhite
val CheradipPrimaryContainer = Color(0xFFB2DFDB)
val CheradipOnPrimaryContainer = Color(0xFF00251A)

val CheradipSecondary = CheradipForestGreen
val CheradipOnSecondary = CheradipWhite
val CheradipSecondaryContainer = Color(0xFFC8E6C9)
val CheradipOnSecondaryContainer = Color(0xFF0D3310)

val CheradipTertiary = CheradipGolden
val CheradipOnTertiary = Color(0xFF3E2E00)
val CheradipTertiaryContainer = Color(0xFFFFF8E1)
val CheradipOnTertiaryContainer = Color(0xFF3E2E00)

val CheradipError = CheradipDarkRed
val CheradipOnError = CheradipWhite
val CheradipErrorContainer = Color(0xFFFFDAD6)
val CheradipOnErrorContainer = Color(0xFF410002)

val CheradipBackground = CheradipWhite
val CheradipOnBackground = Color(0xFF1C1B1F)
val CheradipSurface = Color(0xFFFAFAFA)
val CheradipOnSurface = Color(0xFF1C1B1F)
val CheradipSurfaceVariant = Color(0xFFE0F2F1)
val CheradipOnSurfaceVariant = Color(0xFF424242)
val CheradipOutline = CheradipBlue
val CheradipOutlineVariant = Color(0xFFB0BEC5)
val CheradipInverseSurface = Color(0xFF313033)
val CheradipInverseOnSurface = Color(0xFFF4EFF4)
val CheradipInversePrimary = CheradipSkyBlue

// ── Dark theme tokens ───────────────────────────────────────────────────────
val CheradipPrimaryDark = Color(0xFF4DB6AC)
val CheradipOnPrimaryDark = Color(0xFF003731)
val CheradipPrimaryContainerDark = Color(0xFF004D40)
val CheradipOnPrimaryContainerDark = Color(0xFFB2DFDB)

val CheradipSecondaryDark = Color(0xFF66BB6A)
val CheradipOnSecondaryDark = Color(0xFF0A2E0C)
val CheradipSecondaryContainerDark = Color(0xFF1B5E20)
val CheradipOnSecondaryContainerDark = Color(0xFFC8E6C9)

val CheradipTertiaryDark = Color(0xFFFFCA28)
val CheradipOnTertiaryDark = Color(0xFF3E2E00)
val CheradipTertiaryContainerDark = Color(0xFF5D4037)
val CheradipOnTertiaryContainerDark = Color(0xFFFFF8E1)

val CheradipErrorDark = Color(0xFFFFB4AB)
val CheradipOnErrorDark = Color(0xFF690005)
val CheradipErrorContainerDark = Color(0xFF93000A)
val CheradipOnErrorContainerDark = Color(0xFFFFDAD6)

val CheradipBackgroundDark = Color(0xFF121212)
val CheradipOnBackgroundDark = Color(0xFFE6E1E5)
val CheradipSurfaceDark = Color(0xFF121212)
val CheradipOnSurfaceDark = Color(0xFFE6E1E5)
val CheradipSurfaceVariantDark = Color(0xFF1E3A38)
val CheradipOnSurfaceVariantDark = Color(0xFFB0BEC5)
val CheradipOutlineDark = Color(0xFF64B5F6)
val CheradipOutlineVariantDark = Color(0xFF546E7A)
val CheradipInverseSurfaceDark = Color(0xFFE6E1E5)
val CheradipInverseOnSurfaceDark = Color(0xFF313033)
val CheradipInversePrimaryDark = Color(0xFF00695C)

/** Semantic brand colors for status chips, paywall accents, etc. */
object CheradipColors {
    val teal = CheradipTeal
    val forestGreen = CheradipForestGreen
    val golden = CheradipGolden
    val blue = CheradipBlue
    val skyBlue = CheradipSkyBlue
    val darkRed = CheradipDarkRed
    val black = CheradipBlack
    val white = CheradipWhite

    val statusHealthy = CheradipForestGreen
    val statusWarning = CheradipGolden
    val statusCritical = CheradipDarkRed
}
