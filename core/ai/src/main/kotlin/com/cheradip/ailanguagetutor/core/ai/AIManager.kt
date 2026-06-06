package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.database.dao.AiCacheDao
import com.cheradip.ailanguagetutor.core.database.entity.AiCacheEntity
import com.cheradip.ailanguagetutor.core.model.AiBackend
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.network.AiActivityMetadataRequest
import com.cheradip.ailanguagetutor.core.network.AiltAiService
import com.cheradip.ailanguagetutor.core.network.AiParagraphRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

data class ActivityMetadata(
    val title: String,
    val summary: String?,
    val tags: List<String>,
    val providerUsed: String? = null,
)

/**
 * Batch-only AI. Primary: home PC (LOCAL_HOME). If response takes >= 30s → free cloud APIs.
 */
@Singleton
class AIManager @Inject constructor(
    private val aiService: AiltAiService,
    private val homeAiService: HomeAiService,
    private val homeAiSettings: HomeAiSettingsRepository,
    private val aiModePrefs: AiModePreferenceRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
    private val aiCacheDao: AiCacheDao,
    private val aiProviderRepository: AiProviderRepository,
    private val appConfig: AppConfig,
) {
    private var lastBackend: AiBackend = AiBackend.CLOUD_POOL

    fun lastBackendUsed(): AiBackend = lastBackend

    suspend fun generateActivityMetadata(text: String, languageCode: String): ActivityMetadata =
        withContext(Dispatchers.IO) {
            val key = "meta:${languageCode}:${text.hashCode()}"
            aiCacheDao.get(key)?.let {
                return@withContext ActivityMetadata(
                    title = text.take(48).trim(),
                    summary = it.responseJson,
                    tags = emptyList(),
                )
            }
            runCatching {
                aiService.activityMetadata(AiActivityMetadataRequest(text, languageCode))
            }.fold(
                onSuccess = { resp ->
                    aiProviderRepository.recordProviderUsed(resp.providerUsed)
                    lastBackend = AiBackend.CLOUD_POOL
                    aiCacheDao.put(AiCacheEntity(key, resp.summary ?: resp.title, System.currentTimeMillis()))
                    ActivityMetadata(resp.title, resp.summary, resp.tags, resp.providerUsed)
                },
                onFailure = {
                    ActivityMetadata(
                        title = text.lines().firstOrNull()?.take(48)?.trim() ?: "Activity",
                        summary = text.take(200),
                        tags = listOf("offline"),
                    )
                },
            )
        }

    suspend fun explainParagraph(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource = InputSource.TYPED,
    ): String = processParagraph(
        paragraph = paragraph,
        sourceLang = sourceLang,
        targetLang = targetLang,
        inputSource = inputSource,
        intent = ProcessingIntent.ANSWER,
    )

    suspend fun translateParagraph(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource = InputSource.TYPED,
    ): String = processParagraph(
        paragraph = paragraph,
        sourceLang = sourceLang,
        targetLang = targetLang,
        inputSource = inputSource,
        intent = ProcessingIntent.TRANSLATION,
    )

    private suspend fun processParagraph(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource,
        intent: ProcessingIntent,
    ): String = withContext(Dispatchers.IO) {
        val tier = checkAppAccess.subscriptionTier()
        if (tier == SubscriptionTier.FREE) {
            return@withContext offlineSummary(paragraph)
        }

        val mode = aiModePrefs.resolvedMode(inputSource, tier)
        val key = "batch:$sourceLang:$targetLang:${intent.name}:${mode.id}:${inputSource.name}:${paragraph.hashCode()}"
        aiCacheDao.get(key)?.responseJson?.let { return@withContext it }

        val backend = homeAiSettings.preferredBackend.first()
        if (backend == AiBackend.LOCAL_HOME) {
            runCatching {
                withTimeout(appConfig.homeAiTimeoutMs) {
                    callHomeAi(paragraph, sourceLang, targetLang, inputSource, intent, mode, tier)
                }
            }.onSuccess { result ->
                if (intent == ProcessingIntent.TRANSLATION && isTranslationStub(result)) {
                    aiProviderRepository.recordFallback("home_ai_translation_stub")
                } else {
                    lastBackend = AiBackend.LOCAL_HOME
                    aiCacheDao.put(AiCacheEntity(key, result, System.currentTimeMillis()))
                    return@withContext result
                }
            }.onFailure { e ->
                val reason = when (e) {
                    is TimeoutCancellationException -> "home_ai_timeout_${appConfig.homeAiTimeoutMs}ms"
                    else -> "home_ai_error: ${e.message}"
                }
                aiProviderRepository.recordFallback(reason)
            }
        }

        val cloud = runCatching {
            val body = if (intent == ProcessingIntent.TRANSLATION) {
                AiParagraphRequest(
                    paragraph = "Translate this text from $sourceLang to $targetLang. " +
                        "Reply with ONLY the translation, nothing else:\n\n$paragraph",
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                )
            } else {
                AiParagraphRequest(paragraph, sourceLang, targetLang)
            }
            aiService.explainParagraph(body)
        }.fold(
            onSuccess = { resp ->
                aiProviderRepository.recordProviderUsed(resp.providerUsed)
                lastBackend = AiBackend.CLOUD_POOL
                resp.explanation
            },
            onFailure = { offlineSummary(paragraph) },
        )
        aiCacheDao.put(AiCacheEntity(key, cloud, System.currentTimeMillis()))
        cloud
    }

    private fun isTranslationStub(text: String): Boolean =
        text.startsWith("[NLLB stub") || text.startsWith("[NLLB openvino stub")

    private suspend fun callHomeAi(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource,
        intent: ProcessingIntent,
        mode: AiEngineMode,
        tier: SubscriptionTier,
    ): String = when (intent) {
        ProcessingIntent.TRANSLATION -> homeAiService.translateParagraph(
            paragraph = paragraph,
            sourceLang = sourceLang,
            targetLang = targetLang,
            mode = mode,
            tier = tier,
            inputSource = inputSource,
        )
        ProcessingIntent.ANSWER -> homeAiService.ask(
            text = paragraph,
            mode = mode,
            intent = intent,
            inputSource = inputSource,
            tier = tier,
            languageCode = sourceLang,
            targetLang = targetLang,
        )
    }

    private fun offlineSummary(paragraph: String) =
        "Offline summary: ${paragraph.take(160)}…"
}
