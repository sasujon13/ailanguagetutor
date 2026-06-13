package com.cheradip.ailanguagetutor.feature.dictionary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.audio.TtsPlaybackState
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.WordSheetState
import com.cheradip.ailanguagetutor.ui.components.PronunciationControlRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDefinitionSheet(
    sheet: WordSheetState?,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit,
    onTogglePlayback: ((String) -> Unit)? = null,
    playbackState: TtsPlaybackState = TtsPlaybackState.IDLE,
    onSave: (() -> Unit)? = null,
) {
    if (sheet == null) return
    val definition = sheet.definition
    val grammarText = sheet.grammarText
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(horizontal = 24.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = definition.word,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                if (onTogglePlayback != null) {
                    PronunciationControlRow(
                        playbackState = playbackState,
                        onTogglePlayback = { onTogglePlayback(definition.word) },
                        onSpeakFromStart = { onSpeak(definition.word) },
                    )
                } else {
                    PronunciationControlRow(
                        playbackState = playbackState,
                        onTogglePlayback = { onSpeak(definition.word) },
                        onSpeakFromStart = { onSpeak(definition.word) },
                    )
                }
            }
            Text(
                text = "Meanings (offline pack)",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(4.dp))
            definition.meanings.forEachIndexed { index, meaning ->
                Text(
                    text = "${index + 1}. $meaning",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = grammarSectionTitle(sheet.grammarDepth),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    sheet.contextSnippet?.let { snippet ->
                        Text(
                            text = snippet.take(120).let { if (snippet.length > 120) "$it…" else it },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (sheet.grammarLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                } else if (!grammarText.isNullOrBlank()) {
                    PronunciationControlRow(
                        playbackState = playbackState,
                        onTogglePlayback = {
                            if (onTogglePlayback != null) {
                                onTogglePlayback(grammarText)
                            } else {
                                onSpeak(grammarText)
                            }
                        },
                        onSpeakFromStart = { onSpeak(grammarText) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            when {
                sheet.grammarLoading -> Text(
                    "Loading grammar from AI…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                !grammarText.isNullOrBlank() -> Text(
                    text = grammarText,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                else -> Text(
                    text = "Grammar will appear here for Pro/Plus when Home AI or cloud is available.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (onSave != null) {
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Text("Save to study list", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, bottom = 16.dp),
            ) {
                Text("Close")
            }
        }
    }
}

private fun grammarSectionTitle(depth: GrammarDepth): String = when (depth) {
    GrammarDepth.WORD -> "Word grammar"
    GrammarDepth.SENTENCE -> "Sentence grammar"
    GrammarDepth.PARAGRAPH -> "Paragraph grammar"
}
