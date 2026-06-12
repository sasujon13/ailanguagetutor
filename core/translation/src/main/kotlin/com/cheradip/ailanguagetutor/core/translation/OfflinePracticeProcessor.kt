package com.cheradip.ailanguagetutor.core.translation

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
    ): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""

        val activePacks = languagePackRepository.observeActiveLanguageCodes().first()
        if (activePacks.isEmpty()) {
            return "Download at least one language pack on the Languages tab to use offline Process."
        }

        val sb = StringBuilder()
        val src = sourceLang.lowercase()
        val tgt = targetLang.lowercase()

        if (!src.equals(tgt, ignoreCase = true)) {
            val lines = translationEngine.translateParagraph(trimmed, src, tgt)
            val translated = lines.joinToString("\n") { it.translatedText }
                .replace(Regex("""\[Offline\]\s*"""), "")
                .trim()
            if (translated.isNotBlank() && !translated.contains("translation pending in pack")) {
                sb.appendLine("Translation (${tgt.uppercase()}):")
                sb.appendLine(translated)
                sb.appendLine()
            }
        }

        val tokens = trimmed.split(Regex("""[^\p{L}\p{N}'-]+""")).filter { it.length > 1 }
        if (tokens.isNotEmpty()) {
            sb.appendLine("Meanings (${src.uppercase()}):")
            val seen = mutableSetOf<String>()
            for (token in tokens) {
                val key = token.lowercase()
                if (!seen.add(key)) continue
                val def = dictionaryRepository.lookup(token, src)
                if (def != null) {
                    sb.appendLine("• ${def.word}: ${def.meanings.joinToString("; ")}")
                } else {
                    sb.appendLine("• $token: not in downloaded pack")
                }
            }
        }

        sb.appendLine()
        sb.append("Tap Listen to hear pronunciation (device TTS).")

        return sb.toString().trimEnd()
    }
}
