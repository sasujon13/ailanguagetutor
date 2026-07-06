package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.device.NetworkConnectivityMonitor
import com.cheradip.ailanguagetutor.core.image.BitmapUtils
import com.cheradip.ailanguagetutor.core.image.ScanDocumentClass
import com.cheradip.ailanguagetutor.core.image.ScanDocumentAnalyzer
import com.cheradip.ailanguagetutor.core.image.ScanEnhanceMode
import com.cheradip.ailanguagetutor.core.image.ScanEnhanceRecommendation
import com.cheradip.ailanguagetutor.core.image.ScanEnhanceRecommender
import com.cheradip.ailanguagetutor.core.image.ScanEnhanceStandards
import com.cheradip.ailanguagetutor.core.network.HomeAiScanAnalyzeResponse
import javax.inject.Inject
import javax.inject.Singleton

enum class ScanAnalyzeSource {
    HOME_AI,
    OFFLINE,
}

data class ScanEnhanceAnalyzeResult(
    val recommendation: ScanEnhanceRecommendation,
    val source: ScanAnalyzeSource,
)

/**
 * Standard analyze path: Home AI [/scan-analyze] when premium + online + Home AI enabled;
 * otherwise on-device metrics (same routing constants as server).
 */
@Singleton
class ScanEnhanceAnalyzeService @Inject constructor(
    private val homeAiService: HomeAiService,
    private val developerOptions: DeveloperOptionsRepository,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val aiProviderRepository: AiProviderRepository,
) {
    suspend fun analyze(imagePath: String, premium: Boolean): ScanEnhanceAnalyzeResult {
        if (premium && developerOptions.shouldTryHomeAi() && networkConnectivityMonitor.isOnline()) {
            runCatching {
                val response = homeAiService.scanAnalyze(imagePath, premium)
                aiProviderRepository.recordProviderUsed("home-ai-scan-analyze")
                return ScanEnhanceAnalyzeResult(
                    recommendation = response.toRecommendation(premium),
                    source = ScanAnalyzeSource.HOME_AI,
                )
            }.onFailure { e ->
                if (e is HomeAiUnreachableException || e is HomeAiResponseTimeoutException) {
                    aiProviderRepository.recordFallback(
                        when (e) {
                            is HomeAiUnreachableException -> "home_ai_unreachable"
                            else -> "home_ai_scan_analyze_timeout"
                        },
                    )
                }
            }
        }
        return ScanEnhanceAnalyzeResult(
            recommendation = analyzeOffline(imagePath, premium),
            source = ScanAnalyzeSource.OFFLINE,
        )
    }

    private fun analyzeOffline(imagePath: String, premium: Boolean): ScanEnhanceRecommendation {
        val bitmap = BitmapUtils.load(imagePath, maxEdge = ScanEnhanceStandards.ANALYZE_MAX_EDGE_PX)
        val metrics = ScanDocumentAnalyzer.analyze(bitmap)
        bitmap.recycle()
        return ScanEnhanceRecommender.recommend(metrics, premium)
    }
}

private fun HomeAiScanAnalyzeResponse.toRecommendation(premium: Boolean): ScanEnhanceRecommendation {
    val docClass = documentClass.toScanDocumentClass()
    val mode = if (recommendedMode == "ai_clean") ScanEnhanceMode.AI_CLEAN else ScanEnhanceMode.CLEAN
    return ScanEnhanceRecommendation(
        documentClass = docClass,
        recommendedMode = mode,
        recommendedLevel = recommendedLevel.coerceIn(
            ScanEnhanceStandards.MIN_LEVEL,
            ScanEnhanceStandards.MAX_LEVEL,
        ),
        dewarpCap = ScanEnhanceStandards.route(docClass, premium).dewarpCap,
        label = recommendedLabel.ifBlank {
            val modeLabel = if (mode == ScanEnhanceMode.AI_CLEAN) "AI Clean" else "Clean"
            "$modeLabel Level $recommendedLevel"
        },
    )
}

private fun String.toScanDocumentClass(): ScanDocumentClass = when (this) {
    "text-heavy" -> ScanDocumentClass.TEXT_HEAVY
    "visual-heavy" -> ScanDocumentClass.VISUAL_HEAVY
    "mixed-content" -> ScanDocumentClass.MIXED
    "official-id" -> ScanDocumentClass.OFFICIAL_ID
    "machine-readable" -> ScanDocumentClass.MACHINE_READABLE
    "handwritten" -> ScanDocumentClass.HANDWRITTEN
    "damaged-document" -> ScanDocumentClass.DAMAGED
    else -> ScanDocumentClass.MIXED
}
