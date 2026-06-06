package com.cheradip.ailanguagetutor.feature.dictionary

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.model.WordDefinition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WordDefinitionSheet(
    definition: WordDefinition?,
    onDismiss: () -> Unit,
    onSpeak: (String) -> Unit,
    onSave: (() -> Unit)? = null,
) {
    if (definition == null) return
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = definition.word,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { onSpeak(definition.word) }) {
                    Icon(Icons.Default.VolumeUp, contentDescription = "Speak")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            definition.meanings.forEachIndexed { index, meaning ->
                Text(
                    text = "${index + 1}. $meaning",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            if (onSave != null) {
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Icon(Icons.Default.BookmarkAdd, contentDescription = null)
                    Text("Save to study list", modifier = Modifier.padding(start = 8.dp))
                }
            }
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text("Close")
            }
        }
    }
}
