package com.cheradip.ailanguagetutor.core.translation

import com.cheradip.ailanguagetutor.core.database.dao.TranslationCacheDao
import com.cheradip.ailanguagetutor.core.database.entity.TranslationCacheEntity
import com.cheradip.ailanguagetutor.core.pack.PackDatabaseConnector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class TranslationStrategy { PHRASE, SENTENCE, WORD_PIVOT, CACHED }

data class TranslationResult(
    val sourceText: String,
    val translatedText: String,
    val strategy: TranslationStrategy,
    val sourceLang: String,
    val targetLang: String,
)

@Singleton
class OfflineTranslationEngine @Inject constructor(
    private val translationCacheDao: TranslationCacheDao,
    private val packConnector: PackDatabaseConnector,
) {
    suspend fun translateParagraph(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
    ): List<TranslationResult> = withContext(Dispatchers.Default) {
        splitSentences(paragraph)
            .filter { it.isNotBlank() }
            .map { sentence -> translate(sentence.trim(), sourceLang, targetLang) }
    }

    /** Sentence boundaries for Latin, CJK, Arabic, and Indic scripts. */
    private fun splitSentences(paragraph: String): List<String> {
        if (paragraph.isBlank()) return emptyList()
        return paragraph.split(Regex("""(?<=[.!?。！？؛۔।])(?:\s+|$)"""))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf(paragraph.trim()) }
    }

    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): TranslationResult = withContext(Dispatchers.Default) {
        val hash = sha256(text)
        translationCacheDao.find(hash, sourceLang, targetLang)?.let { cached ->
            return@withContext TranslationResult(
                sourceText = text,
                translatedText = cached.translatedText,
                strategy = TranslationStrategy.CACHED,
                sourceLang = sourceLang,
                targetLang = targetLang,
            )
        }

        val phraseHit = packConnector.lookupPhrase(text, sourceLang, targetLang)
        if (phraseHit != null) {
            return@withContext cacheAndReturn(text, phraseHit, sourceLang, targetLang, TranslationStrategy.PHRASE, hash)
        }

        if (text.contains(' ') && text.length < 120) {
            val pivot = packConnector.pivotTranslation(text, sourceLang, targetLang)
            if (pivot != null) {
                return@withContext cacheAndReturn(text, pivot, sourceLang, targetLang, TranslationStrategy.WORD_PIVOT, hash)
            }
        }

        translateByWords(text, sourceLang, targetLang)?.let { wordLine ->
            return@withContext cacheAndReturn(text, wordLine, sourceLang, targetLang, TranslationStrategy.WORD_PIVOT, hash)
        }

        val sentenceFallback = "[Offline] $text → ($targetLang translation pending in pack)"
        cacheAndReturn(text, sentenceFallback, sourceLang, targetLang, TranslationStrategy.SENTENCE, hash)
    }

    private suspend fun translateByWords(
        text: String,
        sourceLang: String,
        targetLang: String,
    ): String? {
        val parts = text.split(Regex("""(\s+)"""))
        var anyHit = false
        val built = buildString {
            for (part in parts) {
                if (part.isBlank() || part.none { ch -> ch.isLetterOrDigit() || Character.isLetterOrDigit(ch) }) {
                    append(part)
                    continue
                }
                val translated = packConnector.lookupWordTranslation(part, sourceLang, targetLang)
                if (translated != null) {
                    anyHit = true
                    append(translated)
                } else {
                    append(part)
                }
            }
        }
        return built.takeIf { anyHit }
    }

    private suspend fun cacheAndReturn(
        source: String,
        translated: String,
        sourceLang: String,
        targetLang: String,
        strategy: TranslationStrategy,
        hash: String,
    ): TranslationResult {
        translationCacheDao.insert(
            TranslationCacheEntity(
                sourceTextHash = hash,
                sourceText = source,
                sourceLang = sourceLang,
                targetLang = targetLang,
                translatedText = translated,
                strategy = strategy.name,
                cachedAt = System.currentTimeMillis(),
            ),
        )
        return TranslationResult(source, translated, strategy, sourceLang, targetLang)
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
