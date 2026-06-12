package com.cheradip.ailanguagetutor.feature.practice

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.ui.components.InputChannel
import com.cheradip.ailanguagetutor.ui.components.InputChannelBar
import com.cheradip.ailanguagetutor.ui.theme.CheradipPreviewTheme

private val previewLanguages = listOf(
    PracticeLanguageOption("en", "🇺🇸 EN", "🇺🇸"),
    PracticeLanguageOption("fr", "🇫🇷 FR", "🇫🇷"),
    PracticeLanguageOption("de", "🇩🇪 DE", "🇩🇪"),
)

@Preview(name = "Practice — languages (phone)", showBackground = true, widthDp = 360)
@Composable
private fun PracticeLanguageSelectorsPhonePreview() {
    CheradipPreviewTheme {
        PracticeLanguageSelectors(
            languageOptions = previewLanguages,
            inputLanguage = "en",
            onInputSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Practice — languages (wide)", showBackground = true, widthDp = 520)
@Composable
private fun PracticeLanguageSelectorsWidePreview() {
    CheradipPreviewTheme {
        PracticeLanguageSelectors(
            languageOptions = previewLanguages,
            inputLanguage = "fr",
            onInputSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Practice — input channels", showBackground = true, widthDp = 360)
@Composable
private fun PracticeInputChannelsPreview() {
    CheradipPreviewTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            InputChannelBar(
                selected = InputChannel.VOICE,
                onSelect = {},
                channels = listOf(
                    InputChannel.SCAN,
                    InputChannel.CAMERA,
                    InputChannel.IMPORT,
                    InputChannel.TYPE,
                    InputChannel.VOICE,
                    InputChannel.LISTEN,
                ),
                iconsOnly = true,
            )
        }
    }
}
