package com.cheradip.ailanguagetutor.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.cheradip.ailanguagetutor.ui.theme.CheradipPreviewTheme

@Preview(name = "Home", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun HomeScreenPreview() {
    CheradipPreviewTheme {
        HomeScreen()
    }
}

@Preview(name = "Home — dark", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun HomeScreenDarkPreview() {
    CheradipPreviewTheme(darkTheme = true) {
        HomeScreen()
    }
}
