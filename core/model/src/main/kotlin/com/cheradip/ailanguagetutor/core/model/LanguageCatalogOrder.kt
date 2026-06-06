package com.cheradip.ailanguagetutor.core.model

/**
 * Display order for 243-language lists:
 * 1. English (US) first
 * 2. Device region / locale language second (when not English)
 * 3. Remaining languages A→Z by name
 */
object LanguageCatalogOrder {

    const val ENGLISH_CODE = "en"
    const val DEFAULT_COUNTRY = "US"

    data class Hints(
        val countryCode: String = DEFAULT_COUNTRY,
        val languageCode: String = ENGLISH_CODE,
    )

    fun sort(
        languages: List<LanguageCatalogEntry>,
        hints: Hints = Hints(),
    ): List<LanguageCatalogEntry> {
        if (languages.isEmpty()) return languages
        val english = findEnglish(languages) ?: languages.firstOrNull { it.code.equals(ENGLISH_CODE, true) }
        val region = findRegionLanguage(languages, hints, excludeCode = english?.code)
        val pinnedCodes = buildSet {
            english?.code?.let { add(it.lowercase()) }
            region?.code?.let { add(it.lowercase()) }
        }
        val rest = languages
            .filter { it.code.lowercase() !in pinnedCodes }
            .sortedBy { it.name.lowercase() }
        return buildList {
            english?.let { add(it) }
            region?.let { add(it) }
            addAll(rest)
        }
    }

    fun findEnglish(languages: List<LanguageCatalogEntry>): LanguageCatalogEntry? {
        val englishEntries = languages.filter { it.code.equals(ENGLISH_CODE, ignoreCase = true) }
        if (englishEntries.isEmpty()) return null
        return englishEntries.firstOrNull { it.flagCountry.equals(DEFAULT_COUNTRY, ignoreCase = true) }
            ?: englishEntries.first()
    }

    fun findRegionLanguage(
        languages: List<LanguageCatalogEntry>,
        hints: Hints,
        excludeCode: String? = ENGLISH_CODE,
    ): LanguageCatalogEntry? {
        val exclude = excludeCode?.lowercase() ?: ENGLISH_CODE
        val country = hints.countryCode.uppercase()
        val lang = hints.languageCode.lowercase()

        if (lang != ENGLISH_CODE) {
            languages.firstOrNull {
                it.code.equals(lang, ignoreCase = true) && !it.code.equals(exclude, ignoreCase = true)
            }?.let { return it }
        }

        return languages.firstOrNull {
            !it.code.equals(exclude, ignoreCase = true) &&
                !it.code.equals(ENGLISH_CODE, ignoreCase = true) &&
                it.flagCountry.equals(country, ignoreCase = true)
        } ?: languages.firstOrNull {
            !it.code.equals(exclude, ignoreCase = true) &&
                !it.code.equals(ENGLISH_CODE, ignoreCase = true) &&
                countryLanguageFallback(country) == it.code.lowercase()
        }
    }

    /** Common country → primary catalog code when flagCountry alone is ambiguous. */
    private fun countryLanguageFallback(country: String): String? = when (country) {
        "BD" -> "bn"
        "IN" -> "hi"
        "FR" -> "fr"
        "DE" -> "de"
        "ES" -> "es"
        "IT" -> "it"
        "JP" -> "ja"
        "CN", "TW", "HK" -> "zh"
        "BR", "PT" -> "pt"
        "RU" -> "ru"
        "SA", "AE", "EG" -> "ar"
        "KR" -> "ko"
        "MX" -> "es"
        "AU", "GB", "IE", "NZ", "CA" -> "en"
        else -> null
    }
}
