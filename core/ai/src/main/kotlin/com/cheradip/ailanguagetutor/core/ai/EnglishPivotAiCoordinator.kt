package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.model.AiBackend
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.pack.LanguageCodeResolver
import com.cheradip.ailanguagetutor.core.translation.EnglishPivotTranslator
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-English AI flows: translate input → English, process in English, translate output back.
 * Home AI first, then cloud APIs, then offline packs.
 */
@Singleton
class EnglishPivotAiCoordinator @Inject constructor(
    private val offlinePivot: EnglishPivotTranslator,
    private val homeAiService: HomeAiService,
    private val homeAiSettings: HomeAiSettingsRepository,
    private val aiModePrefs: AiModePreferenceRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
    private val cloudAiFallback: CloudAiFallbackService,
    private val aiProviderRepository: AiProviderRepository,
    private val developerOptions: DeveloperOptionsRepository,
) {
    fun needsPivot(sourceLang: String, responseLang: String): Boolean =
        !LanguageCodeResolver.isEnglish(sourceLang) || !LanguageCodeResolver.isEnglish(responseLang)

    fun responseLanguage(sourceLang: String, targetLang: String, intent: ProcessingIntent): String =
        when (intent) {
            ProcessingIntent.TRANSLATION -> targetLang
            ProcessingIntent.ANSWER -> targetLang.ifBlank { sourceLang }
        }

    suspend fun toEnglish(
        text: String,
        sourceLang: String,
        inputSource: InputSource,
    ): String {
        if (!offlinePivot.needsPivot(sourceLang)) return text
        translateViaHome(text, sourceLang, "en", inputSource)?.let { return it }
        if (checkAppAccess.subscriptionTier() != SubscriptionTier.FREE) {
            cloudAiFallback.translate(
                text = text,
                sourceLang = LanguageCodeResolver.normalizePackCode(sourceLang),
                targetLang = "en",
                homeFailureReason = "pivot_to_en_cloud",
            )?.let { return it }
        }
        return offlinePivot.toEnglish(text, sourceLang)
    }

    suspend fun fromEnglish(
        text: String,
        targetLang: String,
        inputSource: InputSource,
    ): String {
        if (!offlinePivot.needsPivot(targetLang)) return text
        translateViaHome(text, "en", targetLang, inputSource)?.let { return it }
        if (checkAppAccess.subscriptionTier() != SubscriptionTier.FREE) {
            cloudAiFallback.translate(
                text = text,
                sourceLang = "en",
                targetLang = LanguageCodeResolver.normalizePackCode(targetLang),
                homeFailureReason = "pivot_from_en_cloud",
            )?.let { return it }
        }
        return offlinePivot.fromEnglish(text, targetLang)
    }

    private suspend fun translateViaHome(
        text: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource,
    ): String? {
        if (!developerOptions.shouldTryHomeAi()) return null
        val tier = checkAppAccess.subscriptionTier()
        val mode = aiModePrefs.resolvedMode(inputSource, tier)
        val homeTimeoutMs = developerOptions.getHomeAiFallbackTimeoutMs()
        return runCatching {
            withTimeout(homeTimeoutMs) {
                homeAiService.translateParagraph(
                    paragraph = text,
                    sourceLang = LanguageCodeResolver.normalizePackCode(sourceLang),
                    targetLang = LanguageCodeResolver.normalizePackCode(targetLang),
                    mode = mode,
                    tier = tier,
                    inputSource = inputSource,
                )
            }
        }.fold(
            onSuccess = { result ->
                result.takeIf { it.isNotBlank() && !isTranslationStub(it) }
            },
            onFailure = { e ->
                val reason = when (e) {
                    is TimeoutCancellationException -> "home_ai_pivot_timeout"
                    else -> "home_ai_pivot_error: ${e.message}"
                }
                aiProviderRepository.recordFallback(reason)
                null
            },
        )
    }

    private fun isTranslationStub(text: String): Boolean =
        text.startsWith("[NLLB stub") || text.startsWith("[NLLB openvino stub")
}
