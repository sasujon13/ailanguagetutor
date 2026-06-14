package com.cheradip.ailanguagetutor.core.pack

import com.cheradip.ailanguagetutor.core.model.WordDefinition

object DictionaryLookupHelper {
    fun isNumericToken(word: String): Boolean {
        val trimmed = word.trim()
        return trimmed.isNotEmpty() && trimmed.all { it.isDigit() || it in ".,/-+" }
    }

    /**
     * Candidate surface forms for dictionary / translation lookup.
     * Inflection rules are scoped by language and script — never apply English -s/-ed to French, etc.
     */
    fun lookupForms(word: String, languageCode: String? = null): List<String> {
        val normalized = normalize(word)
        if (normalized.isBlank()) return emptyList()
        val baseLang = languageCode?.let { LanguageCodeResolver.normalizePackCode(it) }
        return buildList {
            add(normalized)
            when {
                baseLang == "en" -> addEnglishInflections(normalized)
                baseLang == "bn" || looksLikeBengali(normalized) -> addBengaliInflections(normalized)
                baseLang == "hi" || looksLikeDevanagari(normalized) -> addDevanagariInflections(normalized)
                baseLang in ARABIC_SCRIPT_LANGS || looksLikeArabicScript(normalized) -> addArabicInflections(normalized)
            }
        }.distinct()
    }

    private val ARABIC_SCRIPT_LANGS = setOf("ar", "fa", "ur", "ps", "ku", "sd")

    private fun looksLikeBengali(text: String): Boolean =
        text.any { it in '\u0980'..'\u09FF' }

    private fun looksLikeDevanagari(text: String): Boolean =
        text.any { it in '\u0900'..'\u097F' }

    private fun looksLikeArabicScript(text: String): Boolean =
        text.any { it in '\u0600'..'\u06FF' || it in '\u0750'..'\u077F' }

    private fun MutableList<String>.addEnglishInflections(normalized: String) {
        if (normalized.endsWith("'s")) add(normalized.removeSuffix("'s"))
        if (normalized.endsWith("s") && normalized.length > 3) add(normalized.removeSuffix("s"))
        if (normalized.endsWith("ing") && normalized.length > 4) add(normalized.removeSuffix("ing"))
        if (normalized.endsWith("ed") && normalized.length > 3) add(normalized.removeSuffix("ed"))
    }

    private fun MutableList<String>.addBengaliInflections(normalized: String) {
        val suffixes = listOf("গুলো", "টি", "ের", "তে", "কে", "রা", "দের", "য়")
        for (suffix in suffixes) {
            if (normalized.endsWith(suffix) && normalized.length > suffix.length + 1) {
                add(normalized.removeSuffix(suffix))
            }
        }
    }

    private fun MutableList<String>.addDevanagariInflections(normalized: String) {
        val suffixes = listOf("ों", "ें", "ों", "ा", "ी", "े", "ो", "ों")
        for (suffix in suffixes.distinct()) {
            if (normalized.endsWith(suffix) && normalized.length > suffix.length + 1) {
                add(normalized.removeSuffix(suffix))
            }
        }
    }

    private fun MutableList<String>.addArabicInflections(normalized: String) {
        val suffixes = listOf("ها", "هم", "ات", "ون", "ين", "ان", "ة")
        for (suffix in suffixes) {
            if (normalized.endsWith(suffix) && normalized.length > suffix.length + 1) {
                add(normalized.removeSuffix(suffix))
            }
        }
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
        word.lowercase().trim().trim('(', ')', '.', ',', ';', ':', '!', '?', '"', '\'', '"', '।', '…', '؟')

    /** First English lemma from a dictionary gloss (e.g. "to write" → "write", "a greeting (French)" → "greeting"). */
    fun englishLemmaFromGloss(gloss: String): String {
        val withoutParens = gloss.substringBefore('(').trim()
        val first = withoutParens.split(';', ',').first().trim().lowercase()
        val withoutArticle = first.removePrefix("a ").removePrefix("an ").removePrefix("the ").trim()
        val lemma = withoutArticle.removePrefix("to ").trim().substringBefore(' ').ifBlank { withoutArticle }
        return lemma.ifBlank { first }
    }
}
