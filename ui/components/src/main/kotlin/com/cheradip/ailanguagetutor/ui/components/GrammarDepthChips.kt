package com.cheradip.ailanguagetutor.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cheradip.ailanguagetutor.core.model.GrammarDepth

@Composable
fun GrammarDepthChips(
    selected: GrammarDepth,
    onSelected: (GrammarDepth) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = contentPadding,
    ) {
        items(GrammarDepth.entries) { depth ->
            FilterChip(
                selected = depth == selected,
                onClick = { onSelected(depth) },
                label = {
                    Text(
                        text = when (depth) {
                            GrammarDepth.WORD -> "Word"
                            GrammarDepth.SENTENCE -> "Sentence"
                            GrammarDepth.PARAGRAPH -> "Paragraph"
                        },
                        style = MaterialTheme.typography.labelLarge,
                    )
                },
            )
        }
    }
}
