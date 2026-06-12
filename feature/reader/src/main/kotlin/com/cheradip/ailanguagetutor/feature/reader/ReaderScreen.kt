package com.cheradip.ailanguagetutor.feature.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.ClickableText
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.feature.dictionary.WordDefinitionSheet
import com.cheradip.ailanguagetutor.ui.components.CheradipTopBar
import com.cheradip.ailanguagetutor.ui.components.GrammarDepthChips

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(
    documentId: Long,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val grammarDepth by viewModel.grammarDepth.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(documentId) { viewModel.load(documentId) }
    LaunchedEffect(uiState.saveMessage) {
        uiState.saveMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSaveMessage()
        }
    }

    val annotated = remember(uiState.fullText, uiState.words) {
        buildTappableText(uiState.fullText, uiState.words)
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
                                ProcessingIntent.TRANSLATION -> "AI translation"
                                else -> "AI answer"
                            }
                            Text(label, style = MaterialTheme.typography.titleSmall)
                            uiState.aiBackendLabel?.let {
                                AssistChip(onClick = {}, label = { Text("via $it") })
                            }
                            Text(
                                text = aiText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp),
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
                ClickableText(
                    text = annotated,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
                    ),
                    onClick = { offset -> viewModel.onWordTap(offset) },
                )
                }
            }
        }
    }

    WordDefinitionSheet(
        sheet = uiState.wordSheet,
        onDismiss = viewModel::dismissDefinition,
        onSpeak = viewModel::speakWord,
        onSave = viewModel::saveSelectedWord,
    )
}

private fun buildTappableText(fullText: String, words: List<com.cheradip.ailanguagetutor.core.model.WordSpan>): AnnotatedString =
    buildAnnotatedString {
        if (words.isEmpty()) {
            append(fullText)
            return@buildAnnotatedString
        }
        var cursor = 0
        words.sortedBy { it.startOffset }.forEach { word ->
            if (word.startOffset > cursor) {
                append(fullText.substring(cursor, word.startOffset))
            }
            pushStringAnnotation(tag = "word", annotation = word.text)
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                append(fullText.substring(word.startOffset, word.endOffset.coerceAtMost(fullText.length)))
            }
            pop()
            cursor = word.endOffset.coerceAtMost(fullText.length)
        }
        if (cursor < fullText.length) append(fullText.substring(cursor))
    }
