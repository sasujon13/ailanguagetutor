package com.cheradip.ailanguagetutor.ui.theme

import androidx.compose.runtime.Composable

/** Wraps previews in the app theme (no dynamic/wallpaper colors — stable in Android Studio). */
@Composable
fun CheradipPreviewTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    CheradipTheme(darkTheme = darkTheme, dynamicColor = false, content = content)
}
