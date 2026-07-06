package com.cheradip.ailanguagetutor.core.ocr

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real ML Kit OCR gate for Learning mode — pick image path that yields better recognition.
 * Replaces Laplacian-only proxy when preparing pages for OCR.
 */
@Singleton
class ScanOcrQualityGate @Inject constructor(
    private val ocrEngine: MlKitOcrEngine,
) {
    suspend fun pickBestPathForOcr(originalPath: String, enhancedPath: String): String {
        if (originalPath == enhancedPath) return originalPath
        return runCatching {
            val original = ocrEngine.recognize(originalPath)
            val enhanced = ocrEngine.recognize(enhancedPath)
            val origScore = ocrQualityScore(original)
            val enhScore = ocrQualityScore(enhanced)
            if (enhScore >= origScore * MIN_IMPROVEMENT_RATIO) enhancedPath else originalPath
        }.getOrDefault(enhancedPath)
    }

    private fun ocrQualityScore(result: OcrResult): Float {
        val lenFactor = result.fullText.trim().length.coerceAtLeast(1).toFloat()
        return result.confidence.coerceIn(0f, 1f) * 100f + lenFactor * 0.05f
    }

    companion object {
        private const val MIN_IMPROVEMENT_RATIO = 0.92f
    }
}
