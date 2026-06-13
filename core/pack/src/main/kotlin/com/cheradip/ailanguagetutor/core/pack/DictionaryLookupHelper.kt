package com.cheradip.ailanguagetutor.core.pack

import com.cheradip.ailanguagetutor.core.model.WordDefinition

object DictionaryLookupHelper {
    fun isNumericToken(word: String): Boolean {
        val trimmed = word.trim()
        return trimmed.isNotEmpty() && trimmed.all { it.isDigit() || it in ".,/-+" }
    }

    fun lookupForms(word: String): List<String> {
        val normalized = normalize(word)
        if (normalized.isBlank()) return emptyList()
        return buildList {
            add(normalized)
            if (normalized.endsWith("'s")) add(normalized.removeSuffix("'s"))
            if (normalized.endsWith("s") && normalized.length > 3) add(normalized.removeSuffix("s"))
            if (normalized.endsWith("ing") && normalized.length > 4) add(normalized.removeSuffix("ing"))
            if (normalized.endsWith("ed") && normalized.length > 3) add(normalized.removeSuffix("ed"))
        }.distinct()
    }

    fun placeholderDefinition(word: String, languageCode: String, hasActivePacks: Boolean): WordDefinition {
        if (isNumericToken(word)) {
            return WordDefinition(
                word = word,
                languageCode = languageCode,
                meanings = listOf("Number — use the playback or Listen button to hear it spoken."),
            )
        }
        val message = if (hasActivePacks) {
            "No offline entry yet. Download the $languageCode pack from Languages, or connect for AI help."
        } else {
            "Download a language pack on the Languages tab for offline meanings."
        }
        return WordDefinition(
            word = word,
            languageCode = languageCode,
            meanings = listOf(message),
        )
    }

    fun normalize(word: String): String =
        word.lowercase().trim().trim('(', ')', '.', ',', ';', ':', '!', '?', '"', '\'', '"')
}
