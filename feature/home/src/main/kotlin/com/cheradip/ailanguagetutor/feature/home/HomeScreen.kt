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
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.QuickAction
import com.cheradip.ailanguagetutor.ui.components.QuickActionGrid
import com.cheradip.ailanguagetutor.ui.components.SectionHeader

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onScanClick: () -> Unit = {},
    onPracticeClick: () -> Unit = {},
    onTypeClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onListenClick: () -> Unit = {},
    onLearningClick: () -> Unit = {},
) {
    CheradipScrollScreen(
        modifier = modifier,
        title = "AI Language Tutor",
        subtitle = "243 languages · Offline-first",
    ) {
        item {
            Text(
                "What would you like to do?",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            QuickActionGrid(
                actions = listOf(
                    QuickAction("Scan", Icons.Default.QrCodeScanner, onScanClick),
                    QuickAction("Camera", Icons.Default.CameraAlt, onScanClick),
                    QuickAction("Import", Icons.Default.PhotoLibrary, onImportClick),
                    QuickAction("Practice", Icons.Default.Translate, onPracticeClick),
                    QuickAction("Type", Icons.Default.Keyboard, onTypeClick),
                    QuickAction("Voice", Icons.Default.Mic, onVoiceClick),
                    QuickAction("Listen", Icons.AutoMirrored.Filled.VolumeUp, onListenClick),
                    QuickAction("Learning", Icons.AutoMirrored.Filled.MenuBook, onLearningClick),
                ),
            )
        }
        item {
            Spacer(modifier = Modifier.height(8.dp))
            SectionHeader(title = "Quick tips")
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    "Scan or import a document → tap words to learn → practice with voice or typing. " +
                        "Pick up to 3 languages in the Languages tab.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
