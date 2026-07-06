package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.database.dao.AiCacheDao
import com.cheradip.ailanguagetutor.core.database.entity.AiCacheEntity
import com.cheradip.ailanguagetutor.core.device.GuestAiUsageRepository
import com.cheradip.ailanguagetutor.core.device.NetworkConnectivityMonitor
import com.cheradip.ailanguagetutor.core.model.AiBackend
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.GuestAiLimitReachedException
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.ScannedContentType
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.network.AiStructureOcrRequest
import com.cheradip.ailanguagetutor.core.network.AiltAiService
import com.cheradip.ailanguagetutor.core.ocr.OcrTextFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

data class OcrStructureResult(
    val structuredText: String,
    val contentType: ScannedContentType,
    val backendLabel: String,
)

/**
 * Post-OCR structuring: detect content type, then route to cloud APIs for math/code/diagrams
 * or home AI clean-ocr for plain prose.
 */
@Singleton
class OcrStructureService @Inject constructor(
    private val homeAiService: HomeAiService,
    private val homeAiSettings: HomeAiSettingsRepository,
    private val cloudAiService: AiltAiService,
    private val checkAppAccess: CheckAppAccessUseCase,
    private val guestAiUsageRepository: GuestAiUsageRepository,
    private val aiProviderRepository: AiProviderRepository,
    private val aiCacheDao: AiCacheDao,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val developerOptions: DeveloperOptionsRepository,
    private val appConfig: AppConfig,
) {
    suspend fun structure(
        rawOcrText: String,
        contentType: ScannedContentType,
        languageCode: String,
    ): OcrStructureResult = withContext(Dispatchers.IO) {
        val tier = checkAppAccess.subscriptionTier()
        if (tier == SubscriptionTier.FREE || rawOcrText.isBlank()) {
            return@withContext offlineResult(rawOcrText, contentType, "offline")
        }

        val cacheKey = "ocr-struct:${contentType.name}:$languageCode:${rawOcrText.hashCode()}"
        aiCacheDao.get(cacheKey)?.responseJson?.let {
            return@withContext OcrStructureResult(formatStructured(it), contentType, "cache")
        }

        val useCloudOnly = contentType.prefersCloudStructure()
        val online = networkConnectivityMonitor.isOnline()

        if (useCloudOnly) {
            if (!online) {
                return@withContext offlineResult(rawOcrText, contentType, "offline")
            }
            return@withContext structureViaCloud(rawOcrText, contentType, languageCode, cacheKey)
                ?: offlineResult(rawOcrText, contentType, "offline")
        }

        if (!online) {
            return@withContext tryHomeCleanOcr(rawOcrText, languageCode, tier, contentType, cacheKey)
                ?: offlineResult(rawOcrText, contentType, "offline")
        }

        // Prose: home clean-ocr first, then cloud structure
        tryHomeCleanOcr(rawOcrText, languageCode, tier, contentType, cacheKey)?.let { return@withContext it }

        structureViaCloud(rawOcrText, contentType, languageCode, cacheKey)?.let { return@withContext it }

        offlineResult(rawOcrText, contentType, "offline")
    }

    private suspend fun tryHomeCleanOcr(
        rawOcrText: String,
        languageCode: String,
        tier: SubscriptionTier,
        contentType: ScannedContentType,
        cacheKey: String,
    ): OcrStructureResult? {
        if (!developerOptions.shouldTryHomeAi()) return null
        return runCatching {
            guestAiUsageRepository.ensureGuestCanUseAi()
            homeAiService.withHomeAi {
                homeAiService.cleanOcr(
                    text = rawOcrText,
                    languageCode = languageCode,
                    tier = tier,
                )
            }
        }.fold(
            onSuccess = { cleaned ->
                guestAiUsageRepository.recordGuestAiUsage()
                aiProviderRepository.recordProviderUsed("home-ai-clean-ocr")
                val formatted = formatStructured(cleaned)
                aiCacheDao.put(AiCacheEntity(cacheKey, formatted, System.currentTimeMillis()))
                OcrStructureResult(formatted, contentType, "home-ai")
            },
            onFailure = { e ->
                if (e is GuestAiLimitReachedException) throw e
                val reason = when (e) {
                    is HomeAiUnreachableException -> "home_ai_unreachable"
                    is HomeAiResponseTimeoutException -> "home_ai_response_timeout"
                    else -> "home_ai_clean_ocr: ${e.message}"
                }
                aiProviderRepository.recordFallback(reason)
                null
            },
        )
    }

    private suspend fun structureViaCloud(
        rawOcrText: String,
        contentType: ScannedContentType,
        languageCode: String,
        cacheKey: String,
    ): OcrStructureResult? = runCatching {
        guestAiUsageRepository.ensureGuestCanUseAi()
        val prompt = OcrStructurePrompts.build(contentType, rawOcrText, languageCode)
        val response = cloudAiService.structureOcr(
            AiStructureOcrRequest(
                rawText = rawOcrText,
                contentType = contentType.name.lowercase(),
                languageCode = languageCode,
                prompt = prompt,
            ),
        )
        guestAiUsageRepository.recordGuestAiUsage()
        aiProviderRepository.recordProviderUsed(response.providerUsed ?: "cloud")
        val text = formatStructured(response.structuredText.ifBlank { rawOcrText })
        aiCacheDao.put(AiCacheEntity(cacheKey, text, System.currentTimeMillis()))
        OcrStructureResult(text, contentType, response.providerUsed ?: "cloud")
    }.fold(
        onSuccess = { it },
        onFailure = { e ->
            if (e is GuestAiLimitReachedException) throw e
            aiProviderRepository.recordFallback("cloud_structure: ${e.message}")
            null
        },
    )

    private fun offlineResult(
        raw: String,
        type: ScannedContentType,
        label: String,
    ) = OcrStructureResult(
        structuredText = formatStructured(OcrTextFormatter.format(raw, type)),
        contentType = type,
        backendLabel = label,
    )

    private fun formatStructured(text: String): String = AiResponseFormatter.format(text)
}
