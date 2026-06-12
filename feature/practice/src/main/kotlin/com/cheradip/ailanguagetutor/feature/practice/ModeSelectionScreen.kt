package com.cheradip.ailanguagetutor.feature.practice

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.model.AiModeUiMeta
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown
import com.cheradip.ailanguagetutor.ui.components.ResponsivePairDropdowns
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.SectionHeader

private data class IntentOption(val intent: ProcessingIntent, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectionScreen(
    onDone: () -> Unit,
    onNavigatePaywall: () -> Unit,
    onOpenLanguages: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ModeSelectionViewModel = hiltViewModel(),
    calibrationViewModel: VoiceCalibrationViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val calibrationState by calibrationViewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val intentOptions = listOf(
        IntentOption(ProcessingIntent.ANSWER, "Answer — AI tutor explains"),
        IntentOption(ProcessingIntent.TRANSLATION, "Translation — direct output"),
    )
    val selectedIntentOption = intentOptions.first { it.intent == state.processingIntent }

    val context = LocalContext.current
    var pendingMicAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasMicPermission = granted
        if (granted) {
            pendingMicAction?.invoke()
        }
        pendingMicAction = null
    }
    fun withMicPermission(action: () -> Unit) {
        if (hasMicPermission) {
            action()
        } else {
            pendingMicAction = action
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

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
        title = "AI Mode, Languages & Voice Calibration",
        subtitle = "Intent, engine, languages, mic setup",
        onBack = onDone,
    ) {
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
                locked = viewModel.isModeLocked(meta.mode),
                onClick = { viewModel.selectMode(meta.mode) },
            )
        }
        if (state.languageOptions.isNotEmpty()) {
            item {
                SectionHeader(title = "Step 3 — Learning languages")
            }
            item {
                Text(
                    "Input is the language you speak or type. Output is the language you hear in TTS.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val inputOptions = state.languageOptions
                val selectedInput = inputOptions.firstOrNull {
                    it.code.equals(state.inputLanguage, ignoreCase = true)
                } ?: inputOptions.first()
                val outputOptions = state.languageOptions
                val selectedOutput = outputOptions.firstOrNull {
                    it.code.equals(state.outputLanguage, ignoreCase = true)
                } ?: outputOptions.first()
                ResponsivePairDropdowns(
                    firstLabel = "Input language (native / source)",
                    firstOptions = inputOptions,
                    firstSelected = selectedInput,
                    onFirstSelected = { viewModel.setInputLanguage(it.code) },
                    firstOptionLabel = { it.label },
                    secondLabel = "Output language (TTS voice)",
                    secondOptions = outputOptions,
                    secondSelected = selectedOutput,
                    onSecondSelected = { viewModel.setOutputLanguage(it.code) },
                    secondOptionLabel = { it.label },
                )
            }
        }
        item {
            SectionHeader(title = "Step 4 — Voice calibration")
        }
        item {
            VoiceCalibrationContent(
                state = calibrationState,
                onSelectLanguage = calibrationViewModel::selectCalibrationLanguage,
                onSelectTier = calibrationViewModel::selectCalibrationTier,
                onStartMic = { withMicPermission { calibrationViewModel.startCalibrationMic() } },
                onStopMic = calibrationViewModel::stopCalibrationMic,
                onOpenLanguages = onOpenLanguages,
            )
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
private fun ModeCard(
    meta: AiModeUiMeta,
    selected: Boolean,
    locked: Boolean,
    onClick: () -> Unit,
) {
    val containerColor = when {
        selected -> MaterialTheme.colorScheme.primaryContainer
        locked -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
        else -> MaterialTheme.colorScheme.surface
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(meta.emoji, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(meta.label, style = MaterialTheme.typography.titleMedium)
                Text(
                    meta.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when {
                locked -> Icon(
                    Icons.Default.Lock,
                    contentDescription = "Plus required",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                selected -> Icon(
                    Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun PlusUpgradeSheet(onUpgrade: () -> Unit, onDismiss: () -> Unit) {
    Column(modifier = Modifier.padding(24.dp)) {
        Text("Plus required", style = MaterialTheme.typography.titleLarge)
        Text(
            "High Quality mode needs a Plus subscription. It activates automatically when you upgrade. " +
                "You can switch to another mode anytime — your Plus plan stays active until you downgrade to Pro.",
            modifier = Modifier.padding(vertical = 12.dp),
        )
        IconTextButton(label = "Upgrade to Plus", icon = Icons.Default.Star, onClick = onUpgrade)
        IconTextButton(label = "Not now", icon = Icons.Default.Close, onClick = onDismiss, filled = false)
    }
}
