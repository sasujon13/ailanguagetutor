package com.cheradip.ailanguagetutor.feature.practice

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextPipeline
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.ExpandableSection
import com.cheradip.ailanguagetutor.ui.components.IconTextButton
import com.cheradip.ailanguagetutor.ui.components.InputChannel
import com.cheradip.ailanguagetutor.ui.components.InputChannelBar
import com.cheradip.ailanguagetutor.ui.components.SectionHeader
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val calibrationWords = listOf(
    "hello", "world", "learn", "language", "book", "read", "word", "good",
    "morning", "thank", "you", "practice", "speak", "write", "scan",
    "listen", "answer", "translate", "study", "voice",
)

data class PracticeHubUiState(
    val typedInput: String = "",
    val aiOutput: String? = null,
    val aiLoading: Boolean = false,
    val aiIntent: ProcessingIntent? = null,
)

@HiltViewModel
class PracticeHubViewModel @Inject constructor(
    private val pronunciationEngine: PronunciationEngine,
    private val learningActivityRepository: LearningActivityRepository,
    private val unifiedTextPipeline: UnifiedTextPipeline,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PracticeHubUiState())
    val uiState: StateFlow<PracticeHubUiState> = _uiState.asStateFlow()

    init { pronunciationEngine.init() }

    fun speak(text: String) = pronunciationEngine.speak(text)

    fun updateTypedInput(text: String) {
        _uiState.update { it.copy(typedInput = text) }
    }

    fun processTypedInput(sourceLang: String = "en", targetLang: String = "fr") {
        val text = _uiState.value.typedInput.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(aiLoading = true) }
            val result = runCatching {
                unifiedTextPipeline.process(
                    text = text,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                    inputSource = InputSource.TYPED,
                )
            }.getOrNull()
            _uiState.update {
                it.copy(
                    aiLoading = false,
                    aiOutput = result?.output ?: "Could not process. Check subscription and network.",
                    aiIntent = result?.intent,
                )
            }
            learningActivityRepository.record(
                title = "Practice: typed",
                activityType = "practice_typed",
                languageCode = sourceLang,
                summary = result?.output?.take(120),
            )
        }
    }

    fun recordPractice(mode: String) {
        viewModelScope.launch {
            learningActivityRepository.record(
                title = "Practice: $mode",
                activityType = mode,
                languageCode = "en",
                summary = "Interactive practice session",
            )
        }
    }
}

@Composable
fun PracticeHubScreen(
    onOpenModeSelection: () -> Unit = {},
    onScanClick: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PracticeHubViewModel = hiltViewModel(),
) {
    var calibrationIndex by remember { mutableIntStateOf(0) }
    var calibrated by remember { mutableStateOf(false) }
    var syncedLine by remember { mutableStateOf("") }
    var inputChannel by remember { mutableStateOf(InputChannel.TYPE) }
    val hubState by viewModel.uiState.collectAsStateWithLifecycle()
    val progress = (calibrationIndex.toFloat() / calibrationWords.size).coerceIn(0f, 1f)

    CheradipScrollScreen(
        modifier = modifier,
        title = "Practice",
        subtitle = "Say · Type · Scan → Answer / Translation",
    ) {
        item {
            InputChannelBar(
                selected = inputChannel,
                onSelect = { channel ->
                    inputChannel = channel
                    when (channel) {
                        InputChannel.SCAN, InputChannel.CAMERA, InputChannel.IMPORT -> onScanClick()
                        InputChannel.LISTEN -> {
                            syncedLine = hubState.aiOutput ?: hubState.typedInput
                            if (syncedLine.isNotBlank()) viewModel.speak(syncedLine)
                        }
                        InputChannel.VOICE -> viewModel.recordPractice("say_answer")
                        InputChannel.TYPE -> Unit
                    }
                },
                channels = listOf(
                    InputChannel.SCAN,
                    InputChannel.TYPE,
                    InputChannel.VOICE,
                    InputChannel.LISTEN,
                ),
            )
        }

        item {
            IconTextButton(
                label = "AI mode & intent",
                icon = Icons.Default.Settings,
                onClick = onOpenModeSelection,
                filled = false,
            )
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionHeader(title = "Type → AI")
                    OutlinedTextField(
                        value = hubState.typedInput,
                        onValueChange = viewModel::updateTypedInput,
                        label = { Text("Type a sentence or question") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        maxLines = 4,
                    )
                    IconTextButton(
                        label = "Process with AI",
                        icon = Icons.Default.AutoAwesome,
                        onClick = { viewModel.processTypedInput() },
                        enabled = !hubState.aiLoading && hubState.typedInput.isNotBlank(),
                    )
                    if (hubState.aiLoading) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                    hubState.aiOutput?.let { output ->
                        val label = when (hubState.aiIntent) {
                            ProcessingIntent.TRANSLATION -> "Translation"
                            else -> "Answer"
                        }
                        Text(
                            "$label: $output",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconTextButton(
                                label = "Listen",
                                icon = Icons.AutoMirrored.Filled.VolumeUp,
                                onClick = {
                                    syncedLine = output
                                    viewModel.speak(output)
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        syncedLine = output
                    }
                }
            }
        }

        item {
            ExpandableSection(title = "Voice calibration (${calibrationWords.size} words)", initiallyExpanded = false) {
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text("Repeat: ${calibrationWords.getOrElse(calibrationIndex) { "done" }}")
                Spacer(modifier = Modifier.height(8.dp))
                IconTextButton(
                    label = "Hear word",
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    onClick = {
                        val word = calibrationWords.getOrElse(calibrationIndex) { return@IconTextButton }
                        syncedLine = word
                        viewModel.speak(word)
                    },
                )
                IconTextButton(
                    label = "Next word",
                    icon = Icons.Default.KeyboardArrowRight,
                    onClick = {
                        if (calibrationIndex < calibrationWords.lastIndex) calibrationIndex++
                        else calibrated = true
                    },
                    filled = false,
                )
                if (calibrated) {
                    Text("Calibration complete ✓", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        item {
            PracticeModeRow(
                icon = Icons.Default.Mic,
                title = "Say → Answer",
                description = "Speak a prompt; STT when online.",
                onStart = { viewModel.recordPractice("say_answer") },
            )
        }
        item {
            PracticeModeRow(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "Write → Listen",
                description = "Process typed text, then hear TTS playback.",
                onStart = {
                    syncedLine = hubState.aiOutput ?: hubState.typedInput
                    viewModel.speak(syncedLine)
                },
            )
        }
        item {
            PracticeModeRow(
                icon = Icons.Default.QrCodeScanner,
                title = "Scan → Translate",
                description = "Scan from Home, then translate in Reader.",
                onStart = onScanClick,
            )
        }

        if (syncedLine.isNotBlank()) {
            item { SyncedTextDisplay(text = syncedLine) }
        }
    }
}

@Composable
private fun PracticeModeRow(
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
fun SyncedTextDisplay(text: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(" Synced playback", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 4.dp))
            }
            Text(text, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 8.dp))
        }
    }
}
