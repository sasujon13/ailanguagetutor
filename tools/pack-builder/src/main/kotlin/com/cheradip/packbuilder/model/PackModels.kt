package com.cheradip.packbuilder.model

data class WordSeed(
    val word: String,
    val lemma: String,
    val language: String,
    val frequencyScore: Double = 500.0,
    val meanings: List<String>,
    val examples: List<String?> = emptyList(),
    val synonyms: List<String> = emptyList(),
    val ipa: String? = null,
)

data class PhraseTranslation(
    val sourcePhrase: String,
    val sourceLang: String,
    val targetLang: String,
    val translatedText: String,
)

data class WordTranslation(
    val sourceWord: String,
    val sourceLang: String,
    val targetWord: String,
    val targetLang: String,
    val frequency: Double = 100.0,
)

data class QaPair(
    val questionText: String,
    val sourceLang: String,
    val answerText: String,
    val answerLang: String,
)

data class LanguagePackSeed(
    val languageCode: String,
    val words: List<WordSeed>,
    val phrases: List<PhraseTranslation> = emptyList(),
    val wordTranslations: List<WordTranslation> = emptyList(),
    val qaPairs: List<QaPair> = emptyList(),
)
