package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import kotlin.math.max

/**
 * Offline 12-step enhancement pipeline (subset on-device; mirrors upgrade.md stages).
 */
object ScanEnhancePipeline {

    data class PipelineResult(
        val bitmap: Bitmap,
        val rolledBack: Boolean,
        val metrics: ScanAnalysisMetrics?,
        val recommendation: ScanEnhanceRecommendation?,
    )

    fun enhance(
        source: Bitmap,
        mode: ScanEnhanceMode,
        level: Int,
        premium: Boolean = true,
    ): PipelineResult {
        if (level <= 0) {
            return PipelineResult(source, false, null, null)
        }
        val metrics = ScanDocumentAnalyzer.analyze(source)
        val recommendation = ScanEnhanceRecommender.recommend(metrics, premium)
        val selection = ScanCleanLevelProfiles.selection(mode, level)
            ?: return PipelineResult(source, false, metrics, recommendation)

        val beforeScore = readabilityScore(source)
        var working = source
        val hints = documentHints(recommendation.documentClass)

        if (level >= 1) {
            val quad = DocumentEdgeDetector.detectCorners(working, hints)
            working = ImageTransitionProcessor.apply(
                working,
                TransitionParams(
                    corners = quad,
                    perspectiveStrength = (30 + level * 8).coerceAtMost(90),
                    curvedPageCorrection = level >= 5,
                    scanType = hints.scanType,
                ),
            )
        }

        if (level >= 5 && recommendation.dewarpCap > 0.3f) {
            val curve = CurvedDocumentDetector.detect(working, hints)
            if (curve.isValid) {
                val warped = CurvedBoundaryWarp.warp(working, curve)
                if (warped !== working) {
                    if (working !== source) working.recycle()
                    working = warped
                }
            }
        }

        var enhanced = CleanFilterRenderer.applyStack(working, selection)
        if (working !== source && working !== enhanced) working.recycle()

        var rolledBack = false
        if (readabilityScore(enhanced) < beforeScore * ScanEnhanceStandards.READABILITY_ROLLBACK_RATIO) {
            if (enhanced !== source) enhanced.recycle()
            enhanced = CleanFilterRenderer.applyStack(
                source,
                ScanCleanLevelProfiles.selection(mode, maxOf(1, level - 2)) ?: selection,
            )
            rolledBack = true
        }

        return PipelineResult(enhanced, rolledBack, metrics, recommendation)
    }

    private fun documentHints(docClass: ScanDocumentClass): DocumentDetectionHints {
        val scanType = when (docClass) {
            ScanDocumentClass.OFFICIAL_ID -> DocumentScanType.FORM
            ScanDocumentClass.HANDWRITTEN -> DocumentScanType.AUTO
            ScanDocumentClass.DAMAGED -> DocumentScanType.BOOK
            else -> DocumentScanType.AUTO
        }
        return DocumentDetectionHints(scanType = scanType)
    }

    private fun readabilityScore(bitmap: Bitmap): Float {
        val sample = if (max(bitmap.width, bitmap.height) > 320) {
            val s = 320f / max(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * s).toInt()),
                max(1, (bitmap.height * s).toInt()),
                true,
            )
        } else bitmap
        val m = ScanDocumentAnalyzer.analyze(sample)
        if (sample !== bitmap) sample.recycle()
        return m.blurScore + m.contrast
    }
}
