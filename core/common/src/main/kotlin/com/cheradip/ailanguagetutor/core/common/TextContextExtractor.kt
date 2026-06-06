package com.cheradip.ailanguagetutor.core.common

object TextContextExtractor {

    fun sentenceAt(fullText: String, offset: Int): String {
        if (fullText.isBlank()) return ""
        val safeOffset = offset.coerceIn(0, fullText.lastIndex.coerceAtLeast(0))
        val start = findSentenceStart(fullText, safeOffset)
        val end = findSentenceEnd(fullText, safeOffset)
        return fullText.substring(start, end).trim()
    }

    fun paragraphAt(fullText: String, offset: Int): String {
        if (fullText.isBlank()) return ""
        val safeOffset = offset.coerceIn(0, fullText.lastIndex.coerceAtLeast(0))
        val start = findParagraphStart(fullText, safeOffset)
        val end = findParagraphEnd(fullText, safeOffset)
        return fullText.substring(start, end).trim()
    }

    private fun findSentenceStart(text: String, offset: Int): Int {
        var i = offset.coerceAtMost(text.lastIndex)
        while (i > 0) {
            val c = text[i - 1]
            if (c == '\n' || c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？') {
                return i
            }
            i--
        }
        return 0
    }

    private fun findSentenceEnd(text: String, offset: Int): Int {
        var i = offset.coerceAtMost(text.lastIndex)
        while (i < text.length) {
            val c = text[i]
            if (c == '\n' || c == '.' || c == '!' || c == '?' || c == '。' || c == '！' || c == '？') {
                return (i + 1).coerceAtMost(text.length)
            }
            i++
        }
        return text.length
    }

    private fun findParagraphStart(text: String, offset: Int): Int {
        val breakBefore = text.lastIndexOf("\n\n", offset)
        return if (breakBefore >= 0) breakBefore + 2 else 0
    }

    private fun findParagraphEnd(text: String, offset: Int): Int {
        val breakAfter = text.indexOf("\n\n", offset)
        return if (breakAfter >= 0) breakAfter else text.length
    }

    /** First [max] sentences as (startOffset, text). */
    fun sentences(fullText: String, max: Int = 3): List<Pair<Int, String>> {
        if (fullText.isBlank() || max <= 0) return emptyList()
        val results = mutableListOf<Pair<Int, String>>()
        var searchFrom = 0
        while (results.size < max && searchFrom < fullText.length) {
            val start = findSentenceStart(fullText, searchFrom)
            val end = findSentenceEnd(fullText, start.coerceAtMost(fullText.lastIndex))
            val sentence = fullText.substring(start, end).trim()
            if (sentence.isNotBlank()) {
                results.add(start to sentence)
            }
            searchFrom = if (end > start) end else start + 1
            if (searchFrom >= fullText.length) break
        }
        return results
    }

    /** First paragraph as (startOffset, text). */
    fun firstParagraph(fullText: String): Pair<Int, String>? {
        if (fullText.isBlank()) return null
        val start = findParagraphStart(fullText, 0)
        val end = findParagraphEnd(fullText, 0)
        val paragraph = fullText.substring(start, end).trim()
        return if (paragraph.isBlank()) null else start to paragraph
    }
}
