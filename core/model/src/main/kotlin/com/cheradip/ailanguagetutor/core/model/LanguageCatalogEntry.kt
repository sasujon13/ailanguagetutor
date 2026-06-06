package com.cheradip.ailanguagetutor.core.model

data class LanguageCatalogEntry(
    val code: String,
    val name: String,
    val nativeName: String,
    val script: String,
    val region: String,
    val iso639_1: String?,
    val iso639_3: String?,
    val tier: Int,
    val ocrEngine: String,
    val packStatus: String,
    val flagCountry: String?,
    val flagEmoji: String,
)

data class WorldLanguageCatalog(
    val catalogVersion: String,
    val totalLanguages: Int,
    val iso639_1Count: Int,
    val extendedCount: Int,
    val languages: List<LanguageCatalogEntry>,
)
