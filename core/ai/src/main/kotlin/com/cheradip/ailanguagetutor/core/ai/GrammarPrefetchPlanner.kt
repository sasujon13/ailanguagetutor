package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.common.TextContextExtractor
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.GrammarPrefetchTarget
import com.cheradip.ailanguagetutor.core.model.WordSpan
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrammarPrefetchPlanner @Inject constructor() {

    fun plan(
        fullText: String,
        words: List<WordSpan>,
        depth: GrammarDepth,
    ): List<GrammarPrefetchTarget> = when (depth) {
        GrammarDepth.WORD -> planWords(fullText, words)
        GrammarDepth.SENTENCE -> planSentences(fullText)
        GrammarDepth.PARAGRAPH -> planParagraph(fullText)
    }

    private fun planWords(fullText: String, words: List<WordSpan>): List<GrammarPrefetchTarget> {
        if (words.isEmpty()) {
            // Typed practice has no OCR word map — warm first sentences instead.
            return planSentences(fullText)
        }
        val seen = mutableSetOf<String>()
        return words
            .sortedBy { it.startOffset }
            .mapNotNull { span ->
                val token = span.text.lowercase().trim().trim('.', ',', ';', ':', '!', '?', '"', '(', ')')
                if (token.isBlank() || token in seen) return@mapNotNull null
                seen.add(token)
                val context = TextContextExtractor.sentenceAt(fullText, span.startOffset)
                GrammarPrefetchTarget(
                    offset = span.startOffset,
                    focusWord = span.text,
                    contextText = context.ifBlank { span.text },
                )
            }
            .take(3)
    }

    private fun planSentences(fullText: String): List<GrammarPrefetchTarget> =
        TextContextExtractor.sentences(fullText, max = 3).map { (offset, sentence) ->
            val focus = sentence.split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: sentence
            GrammarPrefetchTarget(
                offset = offset,
                focusWord = focus,
                contextText = sentence,
            )
        }

    private fun planParagraph(fullText: String): List<GrammarPrefetchTarget> {
        val para = TextContextExtractor.firstParagraph(fullText) ?: return emptyList()
        val (offset, text) = para
        val focus = text.split(Regex("\\s+")).firstOrNull { it.isNotBlank() } ?: text
        return listOf(
            GrammarPrefetchTarget(
                offset = offset,
                focusWord = focus,
                contextText = text,
            ),
        )
    }
}
