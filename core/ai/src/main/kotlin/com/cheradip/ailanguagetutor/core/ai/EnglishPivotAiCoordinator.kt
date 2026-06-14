package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.pack.LanguageCodeResolver
import com.cheradip.ailanguagetutor.core.translation.EnglishPivotTranslator
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Non-English AI flows: translate input → English, process in English, translate output back.
 */
@Singleton
class EnglishPivotAiCoordinator @Inject constructor(
    private val offlinePivot: EnglishPivotTranslator,
    private val homeAiService: HomeAiService,
    private val homeAiSettings: HomeAiSettingsRepository,
    private val aiModePrefs: AiModePreferenceRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
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
        val tier = checkAppAccess.subscriptionTier()
        val mode = aiModePrefs.resolvedMode(inputSource, tier)
        if (homeAiSettings.preferredBackend.first() == com.cheradip.ailanguagetutor.core.model.AiBackend.LOCAL_HOME &&
            homeAiService.isReachable()
        ) {
            runCatching {
                homeAiService.translateParagraph(
                    paragraph = text,
                    sourceLang = LanguageCodeResolver.normalizePackCode(sourceLang),
                    targetLang = "en",
                    mode = mode,
                    tier = tier,
                    inputSource = inputSource,
                )
            }.getOrNull()
                ?.takeIf { it.isNotBlank() && !isTranslationStub(it) }
                ?.let { return it }
        }
        return offlinePivot.toEnglish(text, sourceLang)
    }

    suspend fun fromEnglish(
        text: String,
        targetLang: String,
        inputSource: InputSource,
    ): String {
        if (!offlinePivot.needsPivot(targetLang)) return text
        val tier = checkAppAccess.subscriptionTier()
        val mode = aiModePrefs.resolvedMode(inputSource, tier)
        if (homeAiSettings.preferredBackend.first() == com.cheradip.ailanguagetutor.core.model.AiBackend.LOCAL_HOME &&
            homeAiService.isReachable()
        ) {
            runCatching {
                homeAiService.translateParagraph(
                    paragraph = text,
                    sourceLang = "en",
                    targetLang = LanguageCodeResolver.normalizePackCode(targetLang),
                    mode = mode,
                    tier = tier,
                    inputSource = inputSource,
                )
            }.getOrNull()
                ?.takeIf { it.isNotBlank() && !isTranslationStub(it) }
                ?.let { return it }
        }
        return offlinePivot.fromEnglish(text, targetLang)
    }

    private fun isTranslationStub(text: String): Boolean =
        text.startsWith("[NLLB stub") || text.startsWith("[NLLB openvino stub")
}
