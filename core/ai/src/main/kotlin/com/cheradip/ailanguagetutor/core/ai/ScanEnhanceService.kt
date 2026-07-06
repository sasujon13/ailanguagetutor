package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.image.ScanEnhanceMode
import com.cheradip.ailanguagetutor.core.image.ScanEnhanceRenderer
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Renders scan enhancement previews and export files.
 * Clean: fully offline. AI Clean: Home AI [/scan-enhance] for premium; offline fallback.
 */
@Singleton
class ScanEnhanceService @Inject constructor(
    private val homeAiService: HomeAiService,
    private val developerOptions: DeveloperOptionsRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
) {
    suspend fun isPremium(): Boolean =
        checkAppAccess.subscriptionTier() != SubscriptionTier.FREE

    suspend fun canUseAiClean(): Boolean = isPremium()

    suspend fun renderToFile(
        originalPath: String,
        mode: ScanEnhanceMode,
        level: Int,
        outFile: File,
        quality: Int = 92,
        documentClass: String? = null,
    ): String {
        val clamped = level.coerceIn(0, 7)
        val premium = isPremium()
        val effectiveMode = if (mode == ScanEnhanceMode.AI_CLEAN && !premium) {
            ScanEnhanceMode.CLEAN
        } else {
            mode
        }
        if (clamped <= 0) {
            return ScanEnhanceRenderer.renderToFile(originalPath, effectiveMode, 0, outFile, quality, premium)
        }
        if (effectiveMode == ScanEnhanceMode.AI_CLEAN && developerOptions.shouldTryHomeAi()) {
            runCatching {
                return homeAiService.scanEnhanceToFile(
                    originalPath,
                    clamped,
                    outFile,
                    premium = premium,
                    documentClass = documentClass,
                )
            }
        }
        return ScanEnhanceRenderer.renderToFile(
            originalPath,
            effectiveMode,
            clamped,
            outFile,
            quality,
            premium,
        )
    }
}
