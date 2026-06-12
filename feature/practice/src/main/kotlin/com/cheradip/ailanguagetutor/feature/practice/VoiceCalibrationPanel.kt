package com.cheradip.ailanguagetutor.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.speech.CalibrationContent
import com.cheradip.ailanguagetutor.core.speech.CalibrationTier
import com.cheradip.ailanguagetutor.core.speech.LanguageCalibrationStatus
import com.cheradip.ailanguagetutor.ui.components.ExpandableSection
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.SectionHeader

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceCalibrationPanel(
    state: PracticeHubUiState,
    onSelectLanguage: (String) -> Unit,
    onSelectTier: (CalibrationTier) -> Unit,
    onStartMic: () -> Unit,
    onStopMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cal = state.calibration
    val pack = CalibrationContent.packFor(cal.languageCode)
    val prompt = CalibrationContent.prompt(pack, cal.tier, cal.itemIndex)
    val itemCount = CalibrationContent.itemCount(cal.tier)
    val status = state.calibrationStatuses.firstOrNull { it.languageCode == cal.languageCode }
    val tierProgress = when (cal.tier) {
        CalibrationTier.WORD -> (cal.itemIndex.toFloat() / itemCount).coerceIn(0f, 1f)
        CalibrationTier.SENTENCE -> (cal.itemIndex.toFloat() / itemCount).coerceIn(0f, 1f)
        CalibrationTier.PARAGRAPH -> if (status?.paragraphCompleted == true) 1f else 0f
    }

    ExpandableSection(
        title = "Voice setup — microphone calibration",
        initiallyExpanded = state.activeLanguageCodes.isNotEmpty(),
    ) {
        Text(
            "Read each prompt aloud. The microphone detects your voice — do not use speaker playback. " +
                "Complete Words, Sentences, and Paragraph for each study language (up to 3). " +
                "Paragraph unlocks Say input.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (state.activeLanguageCodes.isEmpty()) {
            Text(
                "Download languages on the Languages tab first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            return@ExpandableSection
        }

        Text("Language", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.activeLanguageCodes.forEach { code ->
                val langStatus = state.calibrationStatuses.firstOrNull { it.languageCode == code }
                FilterChip(
                    selected = cal.languageCode == code,
                    onClick = { onSelectLanguage(code) },
                    label = {
                        Text("${code.uppercase()} ${calibrationBadge(langStatus)}")
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text("Level", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CalibrationTier.entries.forEach { tier ->
                val done = tierDone(status, tier)
                FilterChip(
                    selected = cal.tier == tier,
                    onClick = { onSelectTier(tier) },
                    label = {
                        Text("${CalibrationContent.tierLabel(tier)}${if (done) " ✓" else ""}")
                    },
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        LinearProgressIndicator(progress = { tierProgress }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))

        prompt?.let { text ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "${CalibrationContent.tierLabel(cal.tier)} ${cal.itemIndex + 1} of $itemCount",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text,
                        style = if (cal.tier == CalibrationTier.WORD) {
                            MaterialTheme.typography.headlineSmall
                        } else {
                            MaterialTheme.typography.bodyLarge
                        },
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconTextButton(
                label = if (cal.listening) "Listening…" else "Start microphone",
                icon = Icons.Default.Mic,
                onClick = onStartMic,
                enabled = !cal.listening && prompt != null,
                modifier = Modifier.weight(1f),
            )
            if (cal.listening) {
                IconTextButton(
                    label = "Stop",
                    icon = Icons.Default.MicOff,
                    onClick = onStopMic,
                    filled = false,
                )
            }
        }

        if (cal.partialText.isNotBlank()) {
            Text(
                "Heard: ${cal.partialText}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        cal.lastScore?.let { score ->
            Text(
                "Match: ${(score * 100).toInt()}%",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        cal.message?.let { msg ->
            Text(
                msg,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun calibrationBadge(status: LanguageCalibrationStatus?): String = when {
    status == null -> ""
    status.paragraphCompleted -> "✓✓✓"
    status.sentenceCompleted -> "✓✓"
    status.wordCompleted -> "✓"
    else -> "○"
}

private fun tierDone(status: LanguageCalibrationStatus?, tier: CalibrationTier): Boolean = when (tier) {
    CalibrationTier.WORD -> status?.wordCompleted == true
    CalibrationTier.SENTENCE -> status?.sentenceCompleted == true
    CalibrationTier.PARAGRAPH -> status?.paragraphCompleted == true
}

@Composable
fun PracticeInputCard(
    hubState: PracticeHubUiState,
    onInputChange: (String) -> Unit,
    onProcess: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onSpeakOutput: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var syncedLine by remember { mutableStateOf("") }
    val displayInput = when {
        hubState.isListening && hubState.partialSpeech.isNotBlank() -> hubState.partialSpeech
        else -> hubState.typedInput
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = displayInput,
                onValueChange = onInputChange,
                label = {
                    Text(
                        if (hubState.isListening) "Listening… speak now" else "Type or use the mic button",
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 7,
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (hubState.isListening) onStopVoice() else onStartVoice()
                        },
                    ) {
                        Icon(
                            if (hubState.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = if (hubState.isListening) "Stop microphone" else "Start microphone",
                            tint = if (hubState.isListening) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
            hubState.speechError?.let { err ->
                Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTextButton(
                    label = if (hubState.isListening) "Stop mic" else "Voice input",
                    icon = if (hubState.isListening) Icons.Default.MicOff else Icons.Default.Mic,
                    onClick = { if (hubState.isListening) onStopVoice() else onStartVoice() },
                    modifier = Modifier.weight(1f),
                )
                IconTextButton(
                    label = "Process with AI",
                    icon = Icons.Default.AutoAwesome,
                    onClick = onProcess,
                    enabled = !hubState.aiLoading && hubState.typedInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                )
            }
            if (hubState.aiLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            hubState.aiOutput?.let { output ->
                val label = when (hubState.aiIntent) {
                    com.cheradip.ailanguagetutor.core.model.ProcessingIntent.TRANSLATION -> "Translation"
                    else -> "Answer"
                }
                Text(
                    "$label: $output",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                IconTextButton(
                    label = "Listen",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    onClick = {
                        syncedLine = output
                        onSpeakOutput(output)
                    },
                )
                syncedLine = output
            }
        }
    }
}

@Composable
fun PracticeModeRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    onStart: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onStart) {
                Icon(icon, contentDescription = "Start", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun PracticeQuickActions(
    onOpenModeSelection: () -> Unit,
    onScanClick: () -> Unit,
    onStartVoice: () -> Unit,
    onSpeakSynced: () -> Unit,
    onRecordPractice: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        IconTextButton(
            label = "AI mode & languages",
            icon = Icons.Default.Settings,
            onClick = onOpenModeSelection,
            filled = false,
        )
        PracticeModeRow(
            icon = Icons.Default.Mic,
            title = "Say → Answer",
            description = "Tap mic in the input box, speak, then process with AI.",
            onStart = onStartVoice,
        )
        PracticeModeRow(
            icon = Icons.AutoMirrored.Filled.VolumeUp,
            title = "Write → Listen",
            description = "Process typed text, then hear TTS playback.",
            onStart = onSpeakSynced,
        )
        PracticeModeRow(
            icon = Icons.Default.QrCodeScanner,
            title = "Scan → Translate",
            description = "Scan from Home, then translate in Reader.",
            onStart = onScanClick,
        )
    }
}
