package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.pow
import kotlin.math.roundToInt

object ImageCleanProcessor {
    fun apply(bitmap: Bitmap, params: CleanParams): Bitmap {
        val effective = if (params.autoEnhance) params.autoOptimized() else params
        val original = bitmap.copy(Bitmap.Config.ARGB_8888, false)
        var result = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        result = adjustBrightnessContrast(result, effective.brightness, effective.contrast)
        if (effective.shadowRemoval > 0) result = removeShadows(result, effective.shadowRemoval)
        if (effective.paperWhitening > 0) result = whitenPaper(result, effective.paperWhitening)
        if (effective.noiseReduction > 0) result = reduceNoise(result, effective.noiseReduction)
        if (effective.sharpness > 0) result = sharpen(result, effective.sharpness)
        if (effective.inkEnhancement > 0) result = enhanceInk(result, effective.inkEnhancement)
        if (effective.gamma != 50) {
            val gamma = (effective.gamma / 50f).coerceIn(0.3f, 2.5f)
            result = applyGamma(result, gamma)
        }
        if (effective.adaptiveThreshold) {
            val thresholded = adaptiveThreshold(result)
            result = if (params.preserveSignatures || params.preserveStamps || params.preserveLogos) {
                preserveMarkedRegions(original, thresholded, params)
            } else {
                thresholded
            }
        } else if (params.preserveSignatures || params.preserveStamps || params.preserveLogos) {
            result = preserveMarkedRegions(original, result, params)
        }
        return result
    }

    private fun preserveMarkedRegions(original: Bitmap, processed: Bitmap, params: CleanParams): Bitmap {
        val w = original.width; val h = original.height
        val origPx = IntArray(w * h)
        val procPx = IntArray(w * h)
        original.getPixels(origPx, 0, w, 0, 0, w, h)
        processed.getPixels(procPx, 0, w, 0, 0, w, h)
        for (i in origPx.indices) {
            val o = origPx[i]
            val r = (o shr 16) and 0xFF
            val g = (o shr 8) and 0xFF
            val b = o and 0xFF
            val maxC = maxOf(r, g, b)
            val minC = minOf(r, g, b)
            val sat = if (maxC == 0) 0 else (maxC - minC) * 100 / maxC
            val lum = (r * 299 + g * 587 + b * 114) / 1000
            val preserve = when {
                params.preserveSignatures && lum < 90 -> true
                params.preserveStamps && sat > 35 && lum in 40..210 -> true
                params.preserveLogos && sat > 25 && maxC > 80 -> true
                else -> false
            }
            if (preserve) procPx[i] = o
        }
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        out.setPixels(procPx, 0, w, 0, 0, w, h)
        return out
    }

    private fun adaptiveThreshold(src: Bitmap): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val gray = IntArray(pixels.size) { i ->
            val p = pixels[i]
            ((p shr 16 and 0xFF) * 299 + (p shr 8 and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        val radius = 12
        val localMean = localMeanGray(gray, w, h, radius)
        val out = pixels.copyOf()
        for (y in 0 until h) {
            for (x in 0 until w) {
                val threshold = localMean[y * w + x] - 8
                val v = if (gray[y * w + x] < threshold) 0 else 255
                out[y * w + x] = (0xFF shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(out, 0, w, 0, 0, w, h)
        return dst
    }

    private fun localMeanGray(gray: IntArray, width: Int, height: Int, radius: Int): IntArray {
        val out = IntArray(gray.size)
        for (y in 0 until height) {
            for (x in 0 until width) {
                var sum = 0
                var count = 0
                val y0 = (y - radius).coerceAtLeast(0)
                val y1 = (y + radius).coerceAtMost(height - 1)
                val x0 = (x - radius).coerceAtLeast(0)
                val x1 = (x + radius).coerceAtMost(width - 1)
                for (ny in y0..y1) {
                    for (nx in x0..x1) {
                        sum += gray[ny * width + nx]
                        count++
                    }
                }
                out[y * width + x] = sum / count
            }
        }
        return out
    }

    private fun CleanParams.autoOptimized() = copy(
        brightness = blendToward(brightness, 55),
        contrast = blendToward(contrast, 62),
        sharpness = blendToward(sharpness, 58),
        noiseReduction = blendToward(noiseReduction, 35),
        shadowRemoval = blendToward(shadowRemoval, 55),
        paperWhitening = blendToward(paperWhitening, 48),
        inkEnhancement = blendToward(inkEnhancement, 52),
        gamma = blendToward(gamma, 50),
    )

    private fun blendToward(current: Int, target: Int, weight: Float = 0.55f): Int =
        (current + (target - current) * weight).roundToInt().coerceIn(0, 100)

    private fun adjustBrightnessContrast(src: Bitmap, brightness: Int, contrast: Int): Bitmap {
        val b = (brightness - 50) / 50f * 40f
        val c = contrast / 50f
        val matrix = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, b,
                0f, c, 0f, 0f, b,
                0f, 0f, c, 0f, b,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        return applyColorMatrix(src, matrix)
    }

    private fun removeShadows(src: Bitmap, strength: Int): Bitmap {
        val lift = strength / 100f * 30f
        val matrix = ColorMatrix().apply { setScale(1f, 1f, 1f, 1f) }
        matrix.postConcat(ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, lift,
            0f, 1f, 0f, 0f, lift,
            0f, 0f, 1f, 0f, lift,
            0f, 0f, 0f, 1f, 0f,
        )))
        return applyColorMatrix(src, matrix)
    }

    private fun whitenPaper(src: Bitmap, strength: Int): Bitmap {
        val s = strength / 100f
        val matrix = ColorMatrix(
            floatArrayOf(
                1f + s * 0.2f, 0f, 0f, 0f, s * 15f,
                0f, 1f + s * 0.2f, 0f, 0f, s * 15f,
                0f, 0f, 1f + s * 0.2f, 0f, s * 15f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        return applyColorMatrix(src, matrix)
    }

    private fun reduceNoise(src: Bitmap, strength: Int): Bitmap {
        val radius = (strength / 25f).coerceIn(1f, 3f).roundToInt()
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.copyOf()
        for (y in radius until h - radius) {
            for (x in radius until w - radius) {
                var r = 0; var g = 0; var b = 0; var count = 0
                for (dy in -radius..radius) {
                    for (dx in -radius..radius) {
                        val p = pixels[(y + dy) * w + x + dx]
                        r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                        count++
                    }
                }
                out[y * w + x] = (0xFF shl 24) or ((r / count) shl 16) or ((g / count) shl 8) or (b / count)
            }
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(out, 0, w, 0, 0, w, h)
        return dst
    }

    private fun sharpen(src: Bitmap, strength: Int): Bitmap {
        val amount = strength / 100f * 0.6f
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.copyOf()
        val kernel = arrayOf(
            intArrayOf(0, -1, 0),
            intArrayOf(-1, 5, -1),
            intArrayOf(0, -1, 0),
        )
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                var r = 0; var g = 0; var b = 0
                for (ky in -1..1) for (kx in -1..1) {
                    val p = pixels[(y + ky) * w + x + kx]
                    val k = kernel[ky + 1][kx + 1]
                    r += ((p shr 16) and 0xFF) * k
                    g += ((p shr 8) and 0xFF) * k
                    b += (p and 0xFF) * k
                }
                val orig = pixels[y * w + x]
                val or = (orig shr 16) and 0xFF; val og = (orig shr 8) and 0xFF; val ob = orig and 0xFF
                val nr = (or + (r - or * 5) * amount).roundToInt().coerceIn(0, 255)
                val ng = (og + (g - og * 5) * amount).roundToInt().coerceIn(0, 255)
                val nb = (ob + (b - ob * 5) * amount).roundToInt().coerceIn(0, 255)
                out[y * w + x] = (0xFF shl 24) or (nr shl 16) or (ng shl 8) or nb
            }
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(out, 0, w, 0, 0, w, h)
        return dst
    }

    private fun enhanceInk(src: Bitmap, strength: Int): Bitmap {
        val darken = strength / 100f * 25f
        val matrix = ColorMatrix(
            floatArrayOf(
                1f, 0f, 0f, 0f, -darken,
                0f, 1f, 0f, 0f, -darken,
                0f, 0f, 1f, 0f, -darken,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        return applyColorMatrix(src, matrix)
    }

    private fun applyGamma(src: Bitmap, gamma: Float): Bitmap {
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val invGamma = 1.0 / gamma
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (((p shr 16) and 0xFF) / 255.0).pow(invGamma) * 255
            val g = (((p shr 8) and 0xFF) / 255.0).pow(invGamma) * 255
            val b = ((p and 0xFF) / 255.0).pow(invGamma) * 255
            pixels[i] = (0xFF shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        return dst
    }

    private fun applyColorMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val dst = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dst)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dst
    }
}

object ImageGrayProcessor {
    fun apply(bitmap: Bitmap, params: GrayParams): Bitmap {
        val modeMatrix = when (params.mode) {
            GrayMode.STANDARD -> grayscaleMatrix(1f)
            GrayMode.HIGH_CONTRAST -> highContrastMatrix()
            GrayMode.NEWSPAPER -> highContrastMatrix(threshold = 0.45f)
            GrayMode.SOFT -> grayscaleMatrix(0.85f)
            GrayMode.OCR_OPTIMIZED -> ocrMatrix()
            GrayMode.RECEIPT -> highContrastMatrix(threshold = 0.55f)
            GrayMode.BOOK -> grayscaleMatrix(0.95f)
            GrayMode.HANDWRITTEN -> grayscaleMatrix(0.9f)
            GrayMode.HISTORICAL -> sepiaMatrix()
        }
        var result = applyColorMatrix(bitmap, modeMatrix)
        if (params.improveOcrAccuracy && params.mode != GrayMode.OCR_OPTIMIZED) {
            result = applyColorMatrix(result, ocrMatrix())
        }
        val b = (params.brightness - 50) / 50f * 40f
        val c = params.contrast / 50f
        val exposure = (params.exposure - 50) / 50f * 30f
        val gamma = (params.gamma / 50f).coerceIn(0.3f, 2.5f)
        result = applyColorMatrix(
            result,
            ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, b + exposure,
                    0f, c, 0f, 0f, b + exposure,
                    0f, 0f, c, 0f, b + exposure,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
        if (gamma != 1f) result = applyGamma(result, gamma)
        if (params.blackPoint != 5 || params.whitePoint != 95) {
            result = applyLevels(result, params.blackPoint, params.whitePoint)
        }
        if (params.darkenText) {
            result = applyColorMatrix(
                result,
                ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, -18f, 0f, 1f, 0f, 0f, -18f, 0f, 0f, 1f, 0f, -18f, 0f, 0f, 0f, 1f, 0f)),
            )
        }
        if (params.lightenPaper) {
            result = applyColorMatrix(
                result,
                ColorMatrix(floatArrayOf(1f, 0f, 0f, 0f, 12f, 0f, 1f, 0f, 0f, 12f, 0f, 0f, 1f, 0f, 12f, 0f, 0f, 0f, 1f, 0f)),
            )
        }
        return result
    }

    private fun grayscaleMatrix(scale: Float): ColorMatrix {
        return ColorMatrix().apply { setSaturation(scale.coerceIn(0f, 1f)) }
    }

    private fun highContrastMatrix(threshold: Float = 0.5f): ColorMatrix {
        val m = ColorMatrix(); m.setSaturation(0f)
        val contrast = 1.8f
        val translate = (-.5f * contrast + .5f + threshold * 0.2f) * 255f
        m.postConcat(ColorMatrix(floatArrayOf(
            contrast, 0f, 0f, 0f, translate,
            0f, contrast, 0f, 0f, translate,
            0f, 0f, contrast, 0f, translate,
            0f, 0f, 0f, 1f, 0f,
        )))
        return m
    }

    private fun ocrMatrix(): ColorMatrix {
        val m = ColorMatrix(); m.setSaturation(0f)
        m.postConcat(ColorMatrix(floatArrayOf(
            1.4f, 0f, 0f, 0f, -25f,
            0f, 1.4f, 0f, 0f, -25f,
            0f, 0f, 1.4f, 0f, -25f,
            0f, 0f, 0f, 1f, 0f,
        )))
        return m
    }

    private fun sepiaMatrix(): ColorMatrix = ColorMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

    private fun applyGamma(src: Bitmap, gamma: Float): Bitmap {
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val invGamma = 1.0 / gamma
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (((p shr 16) and 0xFF) / 255.0).pow(invGamma) * 255
            val g = (((p shr 8) and 0xFF) / 255.0).pow(invGamma) * 255
            val b = ((p and 0xFF) / 255.0).pow(invGamma) * 255
            pixels[i] = (0xFF shl 24) or (r.roundToInt().coerceIn(0, 255) shl 16) or
                (g.roundToInt().coerceIn(0, 255) shl 8) or b.roundToInt().coerceIn(0, 255)
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        return dst
    }

    private fun applyLevels(src: Bitmap, blackPoint: Int, whitePoint: Int): Bitmap {
        val low = blackPoint / 100f * 255f
        val high = whitePoint / 100f * 255f
        val range = (high - low).coerceAtLeast(1f)
        val w = src.width; val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = ((((p shr 16) and 0xFF) - low) / range * 255).roundToInt().coerceIn(0, 255)
            val g = ((((p shr 8) and 0xFF) - low) / range * 255).roundToInt().coerceIn(0, 255)
            val b = (((p and 0xFF) - low) / range * 255).roundToInt().coerceIn(0, 255)
            pixels[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(pixels, 0, w, 0, 0, w, h)
        return dst
    }

    private fun applyColorMatrix(src: Bitmap, matrix: ColorMatrix): Bitmap {
        val dst = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dst)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return dst
    }
}
