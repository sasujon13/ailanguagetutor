package com.cheradip.ailanguagetutor.core.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LanguageCatalogOrderTest {

    private val sample = listOf(
        entry("fr", "French", "FR"),
        entry("de", "German", "DE"),
        entry("en", "English", "US"),
        entry("bn", "Bengali", "BD"),
    )

    @Test
    fun sort_englishFirst_thenRegion_thenAlpha() {
        val sorted = LanguageCatalogOrder.sort(
            sample,
            LanguageCatalogOrder.Hints(countryCode = "BD", languageCode = "bn"),
        )
        assertEquals("en", sorted[0].code)
        assertEquals("bn", sorted[1].code)
        assertEquals(listOf("fr", "de"), sorted.drop(2).map { it.code })
    }

    @Test
    fun sort_skipsDuplicateWhenRegionIsEnglish() {
        val sorted = LanguageCatalogOrder.sort(
            sample,
            LanguageCatalogOrder.Hints(countryCode = "US", languageCode = "en"),
        )
        assertEquals("en", sorted.first().code)
        assertTrue(sorted.count { it.code == "en" } == 1)
    }

    private fun entry(code: String, name: String, country: String) = LanguageCatalogEntry(
        code = code,
        name = name,
        nativeName = name,
        script = "Latn",
        region = "Test",
        iso639_1 = code,
        iso639_3 = null,
        tier = 1,
        ocrEngine = "mlkit_latin",
        packStatus = "launch",
        flagCountry = country,
        flagEmoji = "🏳",
    )
}
