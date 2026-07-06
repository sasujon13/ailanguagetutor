package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Offline smart pre-process analyzer (mirrors server heuristics). */
object ScanDocumentAnalyzer {

    fun analyze(bitmap: Bitmap): ScanAnalysisMetrics {
        val sample = if (max(bitmap.width, bitmap.height) > 480) {
            val scale = 480f / max(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                max(1, (bitmap.width * scale).toInt()),
                max(1, (bitmap.height * scale).toInt()),
                true,
            )
        } else {
            bitmap
        }
        val w = sample.width
        val h = sample.height
        val pixels = IntArray(w * h)
        sample.getPixels(pixels, 0, w, 0, 0, w, h)
        if (sample !== bitmap) sample.recycle()

        var sum = 0.0
        var sumSq = 0.0
        var dark = 0
        val grays = IntArray(pixels.size)
        for (i in pixels.indices) {
            val g = gray(pixels[i])
            grays[i] = g
            sum += g
            sumSq += g * g
            if (g < 40) dark++
        }
        val n = pixels.size.toDouble()
        val mean = sum / n
        val variance = sumSq / n - mean * mean
        val contrast = (sqrt(max(variance, 0.0)) / 128.0).toFloat().coerceIn(0f, 1f)
        val brightness = (mean / 255.0).toFloat().coerceIn(0f, 1f)
        val blurScore = (laplacianVariance(grays, w, h) / 1000f).coerceIn(0f, 2f)

        val third = max(h / 3, 1)
        var topSum = 0.0
        var botSum = 0.0
        for (y in 0 until third) for (x in 0 until w) topSum += grays[y * w + x]
        for (y in h - third until h) for (x in 0 until w) botSum += grays[y * w + x]
        val shadowSeverity = (abs(topSum / (third * w) - botSum / (third * w)) / 80.0)
            .toFloat().coerceIn(0f, 1f)

        var satSum = 0.0
        var satSq = 0.0
        for (px in pixels) {
            val s = saturation(px).toDouble()
            satSum += s
            satSq += s * s
        }
        val satMean = satSum / n
        val colorRichness = (sqrt(max(satSq / n - satMean * satMean, 0.0)) * 2.0)
            .toFloat().coerceIn(0f, 1f)

        var edges = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                if (abs(grays[i + 1] - grays[i - 1]) + abs(grays[i + w] - grays[i - w]) > 40) edges++
            }
        }
        val edgeDensity = (edges.toFloat() / (w * h)).coerceIn(0f, 1f)

        var wrinkle = 0
        for (y in 2 until h - 2) {
            for (x in 2 until w - 2) {
                val i = y * w + x
                val local = (grays[i - 1] + grays[i + 1] + grays[i - w] + grays[i + w]) / 4
                if (abs(grays[i] - local) > 12) wrinkle++
            }
        }
        val wrinkleScore = (wrinkle.toFloat() / (w * h) * 8f).coerceIn(0f, 1f)
        val damageScore = ((dark.toFloat() / pixels.size) * 3f + wrinkleScore * 0.5f).coerceIn(0f, 1f)

        return ScanAnalysisMetrics(
            blurScore = blurScore,
            brightness = brightness,
            contrast = contrast,
            shadowSeverity = shadowSeverity,
            colorRichness = colorRichness,
            edgeDensity = edgeDensity,
            wrinkleScore = wrinkleScore,
            damageScore = damageScore,
            hasMachineReadable = edgeDensity > 0.05f && countQrHeuristic(grays, w, h),
            aspectRatio = w.toFloat() / max(h, 1),
        )
    }

    private fun gray(px: Int): Int {
        val r = Color.red(px)
        val g = Color.green(px)
        val b = Color.blue(px)
        return (0.299 * r + 0.587 * g + 0.114 * b).toInt()
    }

    private fun saturation(px: Int): Float {
        val r = Color.red(px) / 255f
        val g = Color.green(px) / 255f
        val b = Color.blue(px) / 255f
        val maxC = max(r, max(g, b))
        val minC = min(r, min(g, b))
        if (maxC <= 0f) return 0f
        return (maxC - minC) / maxC
    }

    private fun laplacianVariance(grays: IntArray, w: Int, h: Int): Float {
        var sum = 0.0
        var sumSq = 0.0
        var count = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val i = y * w + x
                val lap = -4 * grays[i] + grays[i - 1] + grays[i + 1] + grays[i - w] + grays[i + w]
                sum += lap
                sumSq += lap * lap
                count++
            }
        }
        if (count == 0) return 0f
        val mean = sum / count
        return ((sumSq / count) - mean * mean).toFloat()
    }

    private fun countQrHeuristic(grays: IntArray, w: Int, h: Int): Boolean {
        var corners = 0
        for (y in 4 until h - 4 step 8) {
            for (x in 4 until w - 4 step 8) {
                val i = y * w + x
                val v = grays[i]
                if (abs(grays[i - 4] - v) > 50 && abs(grays[i + 4] - v) > 50) corners++
            }
        }
        return corners >= 6
    }
}

object ScanEnhanceRecommender {

    fun recommend(metrics: ScanAnalysisMetrics, premium: Boolean): ScanEnhanceRecommendation {
        val docClass = classify(metrics)
        val route = ScanEnhanceStandards.route(docClass, premium)
        var level = route.level
        val dewarpCap = route.dewarpCap
        val mode = ScanEnhanceStandards.recommendMode(docClass, premium, route.forceClean)
        if (metrics.shadowSeverity > ScanEnhanceStandards.Recommend.SHADOW_BOOST_THRESHOLD) {
            level = min(ScanEnhanceStandards.MAX_LEVEL, level + 1)
        }
        if (metrics.blurScore < ScanEnhanceStandards.Recommend.BLUR_BOOST_THRESHOLD) {
            level = min(ScanEnhanceStandards.MAX_LEVEL, level + 1)
        }
        val label = "${if (mode == ScanEnhanceMode.AI_CLEAN) "AI Clean" else "Clean"} Level $level"
        return ScanEnhanceRecommendation(docClass, mode, level, dewarpCap, label)
    }

    private fun classify(metrics: ScanAnalysisMetrics): ScanDocumentClass {
        val c = ScanEnhanceStandards.Classify
        if (metrics.hasMachineReadable) return ScanDocumentClass.MACHINE_READABLE
        if (metrics.aspectRatio in c.OFFICIAL_ID_ASPECT_MIN..c.OFFICIAL_ID_ASPECT_MAX &&
            metrics.edgeDensity > c.OFFICIAL_ID_EDGE_MIN &&
            metrics.colorRichness < c.OFFICIAL_ID_COLOR_MAX
        ) {
            return ScanDocumentClass.OFFICIAL_ID
        }
        if (metrics.damageScore > c.DAMAGE_SCORE_MIN || metrics.wrinkleScore > c.WRINKLE_SCORE_MIN) {
            return ScanDocumentClass.DAMAGED
        }
        if (metrics.colorRichness > c.VISUAL_COLOR_MIN && metrics.edgeDensity < c.VISUAL_EDGE_MAX) {
            return ScanDocumentClass.VISUAL_HEAVY
        }
        if (metrics.edgeDensity > c.TEXT_EDGE_MIN && metrics.colorRichness < c.TEXT_COLOR_MAX) {
            return ScanDocumentClass.TEXT_HEAVY
        }
        if (metrics.edgeDensity < c.HANDWRITTEN_EDGE_MAX) return ScanDocumentClass.HANDWRITTEN
        return ScanDocumentClass.MIXED
    }
}
