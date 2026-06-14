package com.cheradip.ailanguagetutor.ui.components

import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.cheradip.ailanguagetutor.core.ai.AiResponseFormatter
import com.cheradip.ailanguagetutor.core.model.WordSpan

@Composable
fun TappableGrammarText(
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
    ClickableText(
        text = buildTappableGrammarText(text, words),
        modifier = modifier,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
        ),
        onClick = onWordTap,
    )
}

fun buildTappableGrammarText(fullText: String, words: List<WordSpan>): AnnotatedString =
    buildAnnotatedString {
        if (words.isEmpty()) {
            appendStyledPlainText(fullText)
            return@buildAnnotatedString
        }
        var cursor = 0
        words.sortedBy { it.startOffset }.forEach { word ->
            if (word.startOffset > cursor) {
                appendStyledPlainText(
                    fullText.substring(cursor, word.startOffset.coerceAtMost(fullText.length)),
                )
            }
            pushStringAnnotation(tag = "word", annotation = word.text)
            withStyle(SpanStyle(fontWeight = FontWeight.Medium)) {
                append(fullText.substring(word.startOffset, word.endOffset.coerceAtMost(fullText.length)))
            }
            pop()
            cursor = word.endOffset.coerceAtMost(fullText.length)
        }
        if (cursor < fullText.length) {
            appendStyledPlainText(fullText.substring(cursor))
        }
    }

private fun AnnotatedString.Builder.appendStyledPlainText(text: String) {
    text.lines().forEachIndexed { index, line ->
        if (index > 0) append('\n')
        append(styledLine(line))
    }
}

private fun styledLine(line: String): AnnotatedString = buildAnnotatedString {
    when {
        line.startsWith("## ") -> withStyle(
            SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
        ) { append(line.removePrefix("## ").trim()) }
        line.startsWith("```") -> withStyle(
            SpanStyle(fontFamily = FontFamily.Monospace),
        ) { append(line) }
        AiResponseFormatter.isEquationLine(line) -> withStyle(
            SpanStyle(fontWeight = FontWeight.SemiBold),
        ) { append(line) }
        else -> append(line)
    }
}
