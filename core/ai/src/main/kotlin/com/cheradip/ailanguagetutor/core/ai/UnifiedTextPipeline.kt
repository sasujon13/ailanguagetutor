package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.model.AiBackend
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import javax.inject.Inject
import javax.inject.Singleton

data class UnifiedTextResult(
    val output: String,
    val intent: ProcessingIntent,
    val backend: AiBackend?,
)

/**
 * v2 unified entry: Scan / Type / Speak text → Answer or Translation via [AIManager].
 */
@Singleton
class UnifiedTextPipeline @Inject constructor(
    private val aiManager: AIManager,
    private val aiModePrefs: AiModePreferenceRepository,
) {
    suspend fun process(
        text: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource,
    ): UnifiedTextResult {
        val intent = aiModePrefs.current().processingIntent
        val output = when (intent) {
            ProcessingIntent.ANSWER -> aiManager.explainParagraph(
                paragraph = text,
                sourceLang = sourceLang,
                targetLang = targetLang,
                inputSource = inputSource,
            )
            ProcessingIntent.TRANSLATION -> aiManager.translateParagraph(
                paragraph = text,
                sourceLang = sourceLang,
                targetLang = targetLang,
                inputSource = inputSource,
            )
        }
        return UnifiedTextResult(
            output = output,
            intent = intent,
            backend = aiManager.lastBackendUsed(),
        )
    }
}
