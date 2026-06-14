package com.cheradip.ailanguagetutor.core.translation

import com.cheradip.ailanguagetutor.core.pack.LanguageCodeResolver
import com.cheradip.ailanguagetutor.core.pack.PackDatabaseConnector
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EnglishPivotTranslator @Inject constructor(
    private val offlineTranslationEngine: OfflineTranslationEngine,
    private val packConnector: PackDatabaseConnector,
) {
    fun needsPivot(languageCode: String): Boolean = !LanguageCodeResolver.isEnglish(languageCode)

    suspend fun toEnglish(text: String, sourceLang: String): String {
        if (!needsPivot(sourceLang)) return text
        val src = LanguageCodeResolver.normalizePackCode(sourceLang)
        val pivot = packConnector.pivotTranslation(text, src, "en")
        if (!pivot.isNullOrBlank() && !pivot.contains("translation pending")) return pivot
        val result = offlineTranslationEngine.translate(text, src, "en")
        return result.translatedText
            .replace(Regex("""\[Offline\]\s*"""), "")
            .replace(Regex("""→\s*\(.*translation pending.*\)"""), "")
            .trim()
            .ifBlank { text }
    }

    suspend fun fromEnglish(text: String, targetLang: String): String {
        if (!needsPivot(targetLang)) return text
        val tgt = LanguageCodeResolver.normalizePackCode(targetLang)
        val pivot = packConnector.pivotTranslation(text, "en", tgt)
        if (!pivot.isNullOrBlank() && !pivot.contains("translation pending")) return pivot
        val result = offlineTranslationEngine.translate(text, "en", tgt)
        return result.translatedText
            .replace(Regex("""\[Offline\]\s*"""), "")
            .replace(Regex("""→\s*\(.*translation pending.*\)"""), "")
            .trim()
            .ifBlank { text }
    }
}
