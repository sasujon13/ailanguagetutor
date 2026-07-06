package com.cheradip.ailanguagetutor.feature.practice

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cheradip.ailanguagetutor.core.model.ScannedContentType
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.core.speech.CalibrationContent
import com.cheradip.ailanguagetutor.core.speech.CalibrationTier
import com.cheradip.ailanguagetutor.core.speech.LanguageCalibrationStatus
import com.cheradip.ailanguagetutor.core.audio.TtsPlaybackState
import com.cheradip.ailanguagetutor.ui.components.CheradipFormError
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.PronunciationControlRow

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VoiceCalibrationContent(
    state: VoiceCalibrationUiState,
    onSelectLanguage: (String) -> Unit,
    onSelectTier: (CalibrationTier) -> Unit,
    onStartMic: () -> Unit,
    onStopMic: () -> Unit,
    onOpenLanguages: () -> Unit = {},
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

    Column(modifier = modifier) {
        Text(
            "Read each prompt aloud. The microphone detects your voice — do not use speaker playback. " +
                "Complete Words, Sentences, and Paragraph for each study language (up to 3). " +
                "Paragraph unlocks mic input on Learning.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(8.dp))

        if (state.activeLanguageCodes.isEmpty()) {
            val teal = MaterialTheme.colorScheme.primary
            Text(
                text = appString("download_language_first"),
                style = MaterialTheme.typography.bodyMedium,
                color = teal,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenLanguages),
            )
            return@Column
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
        state.speechError?.let { err ->
            Text(
                err,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
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
    onProcessOffline: () -> Unit,
    onProcessWithAi: () -> Unit,
    onStartVoice: () -> Unit,
    onStopVoice: () -> Unit,
    onClearInput: () -> Unit,
    onSpeakOutput: (String) -> Unit,
    onTogglePlayback: (String) -> Unit = onSpeakOutput,
    playbackState: TtsPlaybackState = TtsPlaybackState.IDLE,
    onSave: () -> Unit,
    onCancelVoiceAutoAi: () -> Unit = {},
    onOpenVoiceCalibration: (() -> Unit)? = null,
    onWordTapInput: (Int) -> Unit = {},
    onWordTapOutput: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var syncedLine by remember { mutableStateOf("") }
    val composedInput = when {
        hubState.isListening && hubState.partialSpeech.isNotBlank() ->
            PracticeHubViewModel.appendVoiceSegment(hubState.typedInput, hubState.partialSpeech)
        else -> hubState.typedInput
    }
    var textFieldValue by remember { mutableStateOf(TextFieldValue(hubState.typedInput)) }

    LaunchedEffect(hubState.typedInput, hubState.isListening, hubState.partialSpeech) {
        val target = when {
            hubState.isListening && hubState.partialSpeech.isNotBlank() ->
                PracticeHubViewModel.appendVoiceSegment(hubState.typedInput, hubState.partialSpeech)
            else -> hubState.typedInput
        }
        if (textFieldValue.text != target) {
            val selection = if (hubState.isListening) {
                TextRange(target.length)
            } else {
                val start = textFieldValue.selection.start.coerceIn(0, target.length)
                val end = textFieldValue.selection.end.coerceIn(0, target.length)
                TextRange(start.coerceAtMost(end), end.coerceAtLeast(start))
            }
            textFieldValue = TextFieldValue(text = target, selection = selection)
        }
    }

    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            hubState.scanPrefillBanner?.let { banner ->
                ScanPrefillBannerRow(banner = banner)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (composedInput.isNotBlank() || hubState.isListening) {
                    IconButton(onClick = onClearInput) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear input",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            val scanBanner = hubState.scanPrefillBanner
            val useMonospace = scanBanner?.contentType == ScannedContentType.CODE ||
                scanBanner?.contentType == ScannedContentType.MATH
            OutlinedTextField(
                value = textFieldValue,
                onValueChange = { updated ->
                    textFieldValue = updated
                    onInputChange(updated.text)
                },
                label = {
                    Text(
                        when {
                            hubState.isListening -> "Listening… speak now"
                            scanBanner != null -> "Scanned text · edit or use mic"
                            else -> "Type or use the mic button"
                        },
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.Default,
                ),
                colors = if (scanBanner != null) {
                    OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    )
                } else {
                    OutlinedTextFieldDefaults.colors()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !hubState.isListening) {
                            onCancelVoiceAutoAi()
                        }
                    },
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
                val teal = MaterialTheme.colorScheme.primary
                if (hubState.speechErrorLinksCalibration && onOpenVoiceCalibration != null) {
                    Text(
                        text = err,
                        style = MaterialTheme.typography.bodySmall,
                        color = teal,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenVoiceCalibration),
                    )
                } else {
                    Text(err, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            hubState.processError?.let { err ->
                CheradipFormError(message = err)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconTextButton(
                    label = "Process",
                    icon = Icons.Default.Translate,
                    onClick = onProcessOffline,
                    enabled = !hubState.aiLoading && hubState.typedInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    filled = false,
                )
                IconTextButton(
                    label = "AI Process",
                    icon = Icons.Default.AutoAwesome,
                    onClick = onProcessWithAi,
                    enabled = !hubState.aiLoading && hubState.typedInput.isNotBlank(),
                    modifier = Modifier.weight(1f),
                    filled = false,
                )
            }
            if (hubState.aiLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            if (hubState.aiOutput == null && hubState.typedInput.isNotBlank() && !hubState.isListening) {
                Text(
                    "Your text — tap a word for grammar",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PracticeTappableText(
                    text = hubState.typedInput,
                    words = hubState.inputWords,
                    onWordTap = onWordTapInput,
                )
            }
            hubState.aiOutput?.let { output ->
                val label = when {
                    hubState.outputOffline -> "Offline"
                    hubState.aiIntent == com.cheradip.ailanguagetutor.core.model.ProcessingIntent.TRANSLATION -> "Translation"
                    else -> "Answer"
                }
                Text(
                    "$label (tap a word for grammar):",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                PracticeTappableText(
                    text = output,
                    words = hubState.outputWords,
                    onWordTap = onWordTapOutput,
                )
                if (hubState.typedInput.isNotBlank()) {
                    Text(
                        "Your input — tap a word for grammar",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    PracticeTappableText(
                        text = hubState.typedInput,
                        words = hubState.inputWords,
                        onWordTap = onWordTapInput,
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PronunciationControlRow(
                        playbackState = playbackState,
                        onTogglePlayback = { onTogglePlayback(output) },
                        onSpeakFromStart = {
                            syncedLine = output
                            onSpeakOutput(output)
                        },
                    )
                    Text(
                        "Listen",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                val canSave = hubState.typedInput.isNotBlank() || output.isNotBlank()
                IconTextButton(
                    label = if (hubState.resultSaved) "Saved" else "Save",
                    icon = if (hubState.resultSaved) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                    onClick = onSave,
                    enabled = canSave && !hubState.resultSaved,
                    filled = hubState.resultSaved,
                )
                syncedLine = output
            }
            hubState.saveMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            hubState.grammarStudyMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
private fun ScanPrefillBannerRow(banner: ScanPrefillBanner) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        banner.previewImagePath?.let { path ->
            AsyncImage(
                model = path,
                contentDescription = "Scan preview",
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = true,
                    onClick = {},
                    enabled = false,
                    label = { Text(banner.contentType.displayLabel()) },
                )
                banner.documentClass?.let { docClass ->
                    Text(
                        text = docClass.replace('-', ' '),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Text(
                text = "Structured via ${banner.structureBackend}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        Icon(
            Icons.Default.QrCodeScanner,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(24.dp),
        )
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
) {
    IconTextButton(
        label = "AI Mode, Languages & Voice Calibration",
        icon = Icons.Default.Settings,
        onClick = onOpenModeSelection,
        filled = false,
    )
}
