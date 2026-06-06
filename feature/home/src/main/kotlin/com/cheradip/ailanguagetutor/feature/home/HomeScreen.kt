package com.cheradip.ailanguagetutor.feature.home

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.QuickAction
import com.cheradip.ailanguagetutor.ui.components.QuickActionGrid
import com.cheradip.ailanguagetutor.ui.components.SectionHeader

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit = {},
    onCameraClick: () -> Unit = {},
    onPracticeClick: () -> Unit = {},
    onTypeClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onListenClick: () -> Unit = {},
    onLearningClick: () -> Unit = {},
    onGrammarClick: () -> Unit = {},
) {
    CheradipScrollScreen(
        modifier = modifier,
        title = appString("home_title"),
        subtitle = appString("home_subtitle"),
    ) {
        item {
            Text(
                appString("home_prompt"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            QuickActionGrid(
                actions = listOf(
                    QuickAction(appString("action_scan"), Icons.Default.QrCodeScanner, onScanClick),
                    QuickAction(appString("action_camera"), Icons.Default.CameraAlt, onCameraClick),
                    QuickAction(appString("action_import"), Icons.Default.PhotoLibrary, onImportClick),
                    QuickAction(appString("action_practice"), Icons.Default.Translate, onPracticeClick),
                    QuickAction(appString("action_type"), Icons.Default.Keyboard, onTypeClick),
                    QuickAction(appString("action_voice"), Icons.Default.Mic, onVoiceClick),
                    QuickAction(appString("action_listen"), Icons.AutoMirrored.Filled.VolumeUp, onListenClick),
                    QuickAction(appString("action_learning"), Icons.AutoMirrored.Filled.MenuBook, onLearningClick),
                    QuickAction(appString("action_grammar"), Icons.Default.AutoStories, onGrammarClick),
                ),
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = appString("home_tips_header"))
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    appString("home_tips_body"),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
