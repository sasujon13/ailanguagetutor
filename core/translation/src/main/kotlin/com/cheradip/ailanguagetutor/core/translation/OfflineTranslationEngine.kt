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
        paragraph.split(Regex("(?<=[.!?])\\s+"))
            .filter { it.isNotBlank() }
            .map { sentence -> translate(sentence.trim(), sourceLang, targetLang) }
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

        val phraseHit = packConnector.lookupPhrase(text, sourceLang)
        if (phraseHit != null) {
            return@withContext cacheAndReturn(text, phraseHit, sourceLang, targetLang, TranslationStrategy.PHRASE, hash)
        }

        if (text.contains(' ') && text.length < 120) {
            val pivot = packConnector.pivotTranslation(text, targetLang)
            if (pivot != null) {
                return@withContext cacheAndReturn(text, pivot, sourceLang, targetLang, TranslationStrategy.WORD_PIVOT, hash)
            }
        }

        val sentenceFallback = "[Offline] $text → ($targetLang translation pending in pack)"
        cacheAndReturn(text, sentenceFallback, sourceLang, targetLang, TranslationStrategy.SENTENCE, hash)
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
