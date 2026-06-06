package com.cheradip.ailanguagetutor.core.model

data class GrammarBook(
    val title: String,
    val languageCode: String,
    val languageName: String = "",
    val chapters: List<GrammarBookChapter> = emptyList(),
    val cached: Boolean = false,
)

data class GrammarBookChapter(
    val number: Int,
    val title: String,
    val summary: String = "",
    val sections: List<GrammarBookSection> = emptyList(),
)

data class GrammarBookSection(
    val heading: String,
    val body: String,
    val examples: List<String> = emptyList(),
)

data class GrammarBookLanguageOption(
    val code: String,
    val name: String,
    val flagEmoji: String,
)

/** AI-expanded content for a grammar book section (loaded in background while reading). */
data class GrammarSectionEnrichment(
    val expandedBody: String = "",
    val extraExamples: List<String> = emptyList(),
    val learnerTip: String = "",
    val loaded: Boolean = false,
)
