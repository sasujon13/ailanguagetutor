package com.cheradip.ailanguagetutor.core.translation

import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.pack.DictionaryRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OfflinePracticeProcessor @Inject constructor(
    private val translationEngine: OfflineTranslationEngine,
    private val dictionaryRepository: DictionaryRepository,
    private val languagePackRepository: LanguagePackRepository,
) {
    suspend fun process(
        text: String,
        sourceLang: String,
        targetLang: String,
        intent: ProcessingIntent = ProcessingIntent.TRANSLATION,
    ): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""

        val activePacks = languagePackRepository.observeActiveLanguageCodes().first()
        if (activePacks.isEmpty()) {
            return "Download at least one language pack on the Languages tab to use offline Process."
        }

        val src = sourceLang.lowercase()
        val tgt = targetLang.lowercase()

        return when (intent) {
            ProcessingIntent.TRANSLATION -> processTranslation(trimmed, src, tgt)
            ProcessingIntent.ANSWER -> processAnswer(trimmed, src, tgt)
        }
    }

    private suspend fun processTranslation(trimmed: String, src: String, tgt: String): String {
        if (src.equals(tgt, ignoreCase = true)) {
            return "Translation mode needs different input and output languages."
        }
        val lines = translationEngine.translateParagraph(trimmed, src, tgt)
        val translated = lines.joinToString("\n") { it.translatedText }
            .replace(Regex("""\[Offline\]\s*"""), "")
            .trim()
        return translated.ifBlank {
            "Offline translation unavailable for this pair. Connect to the internet for full quality."
        }
    }

    private suspend fun processAnswer(trimmed: String, src: String, tgt: String): String {
        val sb = StringBuilder()
        if (!src.equals(tgt, ignoreCase = true)) {
            val lines = translationEngine.translateParagraph(trimmed, src, tgt)
            val translated = lines.joinToString("\n") { it.translatedText }
                .replace(Regex("""\[Offline\]\s*"""), "")
                .trim()
            if (translated.isNotBlank() && !translated.contains("translation pending in pack")) {
                sb.appendLine("Meaning (${tgt.uppercase()}):")
                sb.appendLine(translated)
                sb.appendLine()
            }
        }

        val lookupLang = if (src.equals(tgt, ignoreCase = true)) src else src
        val tokens = trimmed.split(Regex("""[^\p{L}\p{N}'-]+""")).filter { it.length > 1 }
        if (tokens.isNotEmpty()) {
            sb.appendLine("Vocabulary (${lookupLang.uppercase()}):")
            val seen = mutableSetOf<String>()
            for (token in tokens) {
                val key = token.lowercase()
                if (!seen.add(key)) continue
                val def = dictionaryRepository.lookup(token, lookupLang)
                if (def != null) {
                    sb.appendLine("• ${def.word}: ${def.meanings.joinToString("; ")}")
                } else {
                    sb.appendLine("• $token: not in downloaded pack")
                }
            }
            sb.appendLine()
        }

        if (sb.isBlank()) {
            sb.append("Connect to the internet for a detailed AI tutor explanation.")
        } else {
            sb.append("Connect to the internet for a fuller tutor-style explanation.")
        }
        return sb.toString().trimEnd()
    }
}
