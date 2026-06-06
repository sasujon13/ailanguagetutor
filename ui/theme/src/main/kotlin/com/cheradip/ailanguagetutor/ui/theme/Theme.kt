package com.cheradip.ailanguagetutor.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = CheradipPrimary,
    onPrimary = CheradipOnPrimary,
    primaryContainer = CheradipPrimaryContainer,
    onPrimaryContainer = CheradipOnPrimaryContainer,
    secondary = CheradipSecondary,
    onSecondary = CheradipOnSecondary,
    secondaryContainer = CheradipSecondaryContainer,
    onSecondaryContainer = CheradipOnSecondaryContainer,
    tertiary = CheradipTertiary,
    onTertiary = CheradipOnTertiary,
    tertiaryContainer = CheradipTertiaryContainer,
    onTertiaryContainer = CheradipOnTertiaryContainer,
    error = CheradipError,
    onError = CheradipOnError,
    errorContainer = CheradipErrorContainer,
    onErrorContainer = CheradipOnErrorContainer,
    background = CheradipBackground,
    onBackground = CheradipOnBackground,
    surface = CheradipSurface,
    onSurface = CheradipOnSurface,
    surfaceVariant = CheradipSurfaceVariant,
    onSurfaceVariant = CheradipOnSurfaceVariant,
    outline = CheradipOutline,
    outlineVariant = CheradipOutlineVariant,
    inverseSurface = CheradipInverseSurface,
    inverseOnSurface = CheradipInverseOnSurface,
    inversePrimary = CheradipInversePrimary,
)

private val DarkColors = darkColorScheme(
    primary = CheradipPrimaryDark,
    onPrimary = CheradipOnPrimaryDark,
    primaryContainer = CheradipPrimaryContainerDark,
    onPrimaryContainer = CheradipOnPrimaryContainerDark,
    secondary = CheradipSecondaryDark,
    onSecondary = CheradipOnSecondaryDark,
    secondaryContainer = CheradipSecondaryContainerDark,
    onSecondaryContainer = CheradipOnSecondaryContainerDark,
    tertiary = CheradipTertiaryDark,
    onTertiary = CheradipOnTertiaryDark,
    tertiaryContainer = CheradipTertiaryContainerDark,
    onTertiaryContainer = CheradipOnTertiaryContainerDark,
    error = CheradipErrorDark,
    onError = CheradipOnErrorDark,
    errorContainer = CheradipErrorContainerDark,
    onErrorContainer = CheradipOnErrorContainerDark,
    background = CheradipBackgroundDark,
    onBackground = CheradipOnBackgroundDark,
    surface = CheradipSurfaceDark,
    onSurface = CheradipOnSurfaceDark,
    surfaceVariant = CheradipSurfaceVariantDark,
    onSurfaceVariant = CheradipOnSurfaceVariantDark,
    outline = CheradipOutlineDark,
    outlineVariant = CheradipOutlineVariantDark,
    inverseSurface = CheradipInverseSurfaceDark,
    inverseOnSurface = CheradipInverseOnSurfaceDark,
    inversePrimary = CheradipInversePrimaryDark,
)

@Composable
fun CheradipTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /** When true on Android 12+, derives palette from wallpaper with teal seed fallback. */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = CheradipTypography,
        shapes = CheradipShapes,
        content = content,
    )
}
