package com.cheradip.ailanguagetutor.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.ui.theme.CheradipPreviewTheme

@Preview(name = "Input channels — icons", showBackground = true, widthDp = 360)
@Composable
private fun InputChannelBarIconsPreview() {
    CheradipPreviewTheme {
        InputChannelBar(
            selected = InputChannel.TYPE,
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
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Input channels — labels", showBackground = true, widthDp = 360)
@Composable
private fun InputChannelBarLabelsPreview() {
    CheradipPreviewTheme {
        InputChannelBar(
            selected = InputChannel.VOICE,
            onSelect = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Quick actions", showBackground = true, widthDp = 360)
@Composable
private fun QuickActionGridPreview() {
    CheradipPreviewTheme {
        QuickActionGrid(
            actions = listOf(
                QuickAction("Scan", Icons.Default.QrCodeScanner, {}),
                QuickAction("Camera", Icons.Default.QrCodeScanner, {}),
                QuickAction("Practice", Icons.Default.QrCodeScanner, {}),
            ),
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Dropdown", showBackground = true, widthDp = 360)
@Composable
private fun CheradipDropdownPreview() {
    val options = listOf("English", "French", "German")
    CheradipPreviewTheme {
        CheradipDropdown(
            label = "Speak/Type language",
            options = options,
            selected = options[0],
            onSelected = {},
            modifier = Modifier.padding(16.dp),
        )
    }
}

@Preview(name = "Section header", showBackground = true, widthDp = 360)
@Composable
private fun SectionHeaderPreview() {
    CheradipPreviewTheme {
        Column(modifier = Modifier.padding(16.dp)) {
            SectionHeader(title = "Input language")
        }
    }
}
