package com.cheradip.ailanguagetutor.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Preview(name = "Theme — light", showBackground = true)
@Composable
private fun CheradipThemeLightPreview() {
    CheradipPreviewTheme {
        Text("AI Language Tutor", style = MaterialTheme.typography.headlineMedium)
    }
}

@Preview(name = "Theme — dark", showBackground = true)
@Composable
private fun CheradipThemeDarkPreview() {
    CheradipPreviewTheme(darkTheme = true) {
        Text("AI Language Tutor", style = MaterialTheme.typography.headlineMedium)
    }
}
