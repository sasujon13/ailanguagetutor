package com.cheradip.ailanguagetutor.feature.practice

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import com.cheradip.ailanguagetutor.core.model.WordSpan

@Composable
fun PracticeTappableText(
    text: String,
    words: List<WordSpan>,
    onWordTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    if (label != null) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
    }
    val annotated = buildPracticeTappableText(text, words)
    ClickableText(
        text = annotated,
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        onClick = onWordTap,
    )
}

fun buildPracticeTappableText(fullText: String, words: List<WordSpan>): AnnotatedString =
    buildAnnotatedString {
        if (words.isEmpty()) {
            append(fullText)
            return@buildAnnotatedString
        }
        var cursor = 0
        words.sortedBy { it.startOffset }.forEach { word ->
            if (word.startOffset > cursor) {
                append(fullText.substring(cursor, word.startOffset.coerceAtMost(fullText.length)))
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
