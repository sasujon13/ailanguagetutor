package com.cheradip.ailanguagetutor.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.ScannedContentType
import com.cheradip.ailanguagetutor.feature.dictionary.WordDefinitionSheet
import com.cheradip.ailanguagetutor.ui.components.CheradipTopBar
import com.cheradip.ailanguagetutor.ui.components.GrammarDepthChips
import com.cheradip.ailanguagetutor.ui.components.TappableGrammarText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documentId: Long,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val grammarDepth by viewModel.grammarDepth.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(documentId) { viewModel.load(documentId) }
    DisposableEffect(Unit) {
        onDispose { viewModel.stopPlayback() }
    }
    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            CheradipTopBar(
                title = uiState.title,
                onBack = onBack,
                actions = {
                    IconButton(onClick = viewModel::runAiProcessing) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = "AI process scan")
                    }
                    IconButton(onClick = viewModel::toggleTranslation) {
                        Icon(Icons.Default.Translate, contentDescription = "Offline translate")
                    }
                },
            )
        },
    ) { padding ->
        if (uiState.isLoading) {
            CircularProgressIndicator(modifier = Modifier.padding(padding).padding(24.dp))
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                GrammarDepthChips(
                    selected = grammarDepth,
                    onSelected = viewModel::setGrammarDepth,
                )
                if (uiState.aiPrefetching) {
                    Text(
                        "Warming AI cache…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                if (uiState.aiLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(bottom = 8.dp))
                    Text("Processing with AI…", style = MaterialTheme.typography.bodySmall)
                }
                uiState.aiPanelText?.let { aiText ->
                    Card(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            val label = when (uiState.aiPanelIntent) {
                                ProcessingIntent.TRANSLATION -> "AI translation (tap a word for grammar)"
                                else -> "AI answer (tap a word for grammar)"
                            }
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            uiState.aiBackendLabel?.let {
                                AssistChip(onClick = {}, label = { Text("via $it") })
                            }
                            TappableGrammarText(
                                text = aiText,
                                words = uiState.aiPanelWords,
                                onWordTap = viewModel::onAiPanelWordTap,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                            )
                            IconButton(onClick = viewModel::dismissAiPanel) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss AI panel")
                            }
                        }
                    }
                }
                if (uiState.showTranslation) {
                    AssistChip(
                        onClick = {},
                        label = { Text("Offline translation → ${uiState.targetLanguageCode.uppercase()}") },
                    )
                    uiState.translations.forEach { result ->
                        Text(
                            text = result.translatedText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                    Text(
                        text = "—",
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text(uiState.primaryContentType.displayLabel()) },
                )
                Text(
                    "Tap any word for grammar help",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                TappableGrammarText(
                    text = uiState.fullText,
                    words = uiState.words,
                    onWordTap = viewModel::onWordTap,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                }
            }
        }
    }

    WordDefinitionSheet(
        sheet = uiState.wordSheet,
        onDismiss = viewModel::dismissDefinition,
        onSpeak = viewModel::speakWord,
        onTogglePlayback = viewModel::toggleWordPlayback,
        playbackState = playbackState,
        onSave = viewModel::saveSelectedWord,
    )
}
