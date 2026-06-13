package com.cheradip.ailanguagetutor.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.QuickAction
import com.cheradip.ailanguagetutor.ui.components.QuickActionGrid
import com.cheradip.ailanguagetutor.ui.components.SectionHeader

private enum class HomeActivityMode {
    SCAN_ONLY,
    LEARNING,
}

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onScanClick: (scanOnly: Boolean) -> Unit = {},
    onCameraClick: () -> Unit = {},
    onPracticeClick: () -> Unit = {},
    onTypeClick: () -> Unit = {},
    onVoiceClick: () -> Unit = {},
    onImportClick: () -> Unit = {},
    onListenClick: () -> Unit = {},
    onLearningClick: () -> Unit = {},
    onGrammarClick: () -> Unit = {},
) {
    var activityModeOrdinal by rememberSaveable { mutableIntStateOf(HomeActivityMode.LEARNING.ordinal) }
    val activityMode = HomeActivityMode.entries[activityModeOrdinal.coerceIn(HomeActivityMode.entries.indices)]

    val learningActions = listOf(
        QuickAction(
            appString("action_scan"),
            Icons.Default.QrCodeScanner,
            onClick = { onScanClick(activityMode == HomeActivityMode.SCAN_ONLY) },
        ),
        QuickAction(appString("action_camera"), Icons.Default.CameraAlt, onCameraClick),
        QuickAction(appString("action_import"), Icons.Default.PhotoLibrary, onImportClick),
        QuickAction(appString("action_practice"), Icons.AutoMirrored.Filled.MenuBook, onPracticeClick),
        QuickAction(appString("action_type"), Icons.Default.Keyboard, onTypeClick),
        QuickAction(appString("action_voice"), Icons.Default.Mic, onVoiceClick),
        QuickAction(appString("action_listen"), Icons.AutoMirrored.Filled.VolumeUp, onListenClick),
        QuickAction(appString("action_learning"), Icons.Default.Refresh, onLearningClick),
        QuickAction(appString("action_grammar"), Icons.Default.AutoStories, onGrammarClick),
    )
    val visibleActions = when (activityMode) {
        HomeActivityMode.SCAN_ONLY -> listOf(learningActions.first())
        HomeActivityMode.LEARNING -> learningActions
    }

    CheradipScrollScreen(
        modifier = modifier,
        title = appString("home_title"),
        subtitle = appString("home_subtitle"),
    ) {
        item {
            HomeActivityModeSelector(
                selected = activityMode,
                onSelected = { activityModeOrdinal = it.ordinal },
            )
        }
        item {
            Text(
                appString("home_prompt"),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            QuickActionGrid(actions = visibleActions)
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

@Composable
private fun HomeActivityModeSelector(
    selected: HomeActivityMode,
    onSelected: (HomeActivityMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val inline = maxWidth >= 340.dp
        if (inline) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = appString("home_select_mode"),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(end = 4.dp),
                )
                HomeActivityModeChip(
                    label = appString("home_mode_scan_only"),
                    selected = selected == HomeActivityMode.SCAN_ONLY,
                    onClick = { onSelected(HomeActivityMode.SCAN_ONLY) },
                )
                HomeActivityModeChip(
                    label = appString("home_mode_learning"),
                    selected = selected == HomeActivityMode.LEARNING,
                    onClick = { onSelected(HomeActivityMode.LEARNING) },
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = appString("home_select_mode"),
                    style = MaterialTheme.typography.titleSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HomeActivityModeChip(
                        label = appString("home_mode_scan_only"),
                        selected = selected == HomeActivityMode.SCAN_ONLY,
                        onClick = { onSelected(HomeActivityMode.SCAN_ONLY) },
                    )
                    HomeActivityModeChip(
                        label = appString("home_mode_learning"),
                        selected = selected == HomeActivityMode.LEARNING,
                        onClick = { onSelected(HomeActivityMode.LEARNING) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeActivityModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}
