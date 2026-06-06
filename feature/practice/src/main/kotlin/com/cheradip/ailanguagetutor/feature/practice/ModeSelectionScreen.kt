package com.cheradip.ailanguagetutor.feature.practice

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.model.AiModeUiMeta
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.SectionHeader

private data class IntentOption(val intent: ProcessingIntent, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectionScreen(
    onDone: () -> Unit,
    onNavigatePaywall: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ModeSelectionViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val intentOptions = listOf(
        IntentOption(ProcessingIntent.ANSWER, "Answer — AI tutor explains"),
        IntentOption(ProcessingIntent.TRANSLATION, "Translation — direct output"),
    )
    val selectedIntentOption = intentOptions.first { it.intent == state.processingIntent }

    if (state.showPlusUpgrade) {
        ModalBottomSheet(onDismissRequest = viewModel::dismissPlusUpgrade, sheetState = sheetState) {
            PlusUpgradeSheet(
                onUpgrade = {
                    viewModel.dismissPlusUpgrade()
                    onNavigatePaywall()
                },
                onDismiss = viewModel::dismissPlusUpgrade,
            )
        }
    }

    CheradipScrollScreen(
        modifier = modifier,
        title = "AI processing",
        subtitle = "Intent + engine mode",
        onBack = onDone,
    ) {
        item {
            Text(
                "OCR scans automatically use Lightweight mode (4).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            SectionHeader(title = "Step 1 — Intent")
        }
        item {
            CheradipDropdown(
                label = "Processing intent",
                options = intentOptions,
                selected = selectedIntentOption,
                onSelected = { viewModel.setIntent(it.intent) },
                optionLabel = { it.label },
                leadingIcon = Icons.Default.Translate,
            )
        }
        item {
            SectionHeader(title = "Step 2 — AI engine mode")
        }
        items(state.selectableModes.size) { index ->
            val meta = state.selectableModes[index]
            ModeCard(
                meta = meta,
                selected = state.selectedMode == meta.mode,
                onClick = { viewModel.selectMode(meta.mode) },
            )
        }
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(
                        " Mode 4 Lightweight applies automatically when you scan with OCR.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        item {
            IconTextButton(
                label = "Save & continue",
                icon = Icons.Default.Save,
                onClick = { viewModel.save(onDone) },
            )
        }
    }
}

@Composable
private fun ModeCard(meta: AiModeUiMeta, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = if (selected) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(meta.emoji, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(meta.label, style = MaterialTheme.typography.titleMedium)
                Text(meta.description, style = MaterialTheme.typography.bodySmall)
            }
            if (selected) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun PlusUpgradeSheet(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Plus required", style = MaterialTheme.typography.titleLarge)
        Text(
            "High Accuracy mode (5) needs a Plus subscription. Pro includes modes 1–4.",
            modifier = Modifier.padding(vertical = 12.dp),
        )
        IconTextButton(label = "Upgrade to Plus", icon = Icons.Default.Star, onClick = onUpgrade)
        IconTextButton(label = "Not now", icon = Icons.Default.Close, onClick = onDismiss, filled = false)
    }
}
