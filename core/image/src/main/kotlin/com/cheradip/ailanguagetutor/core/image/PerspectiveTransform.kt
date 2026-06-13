package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object PerspectiveTransform {
    /** Warp bitmap using 4 source corners (pixel coords) into a flat rectangle. */
    fun warp(bitmap: Bitmap, srcCorners: FloatArray, strength: Float = 1f): Bitmap {
        val dstWidth = estimateWidth(srcCorners).roundToInt().coerceAtLeast(64)
        val dstHeight = estimateHeight(srcCorners).roundToInt().coerceAtLeast(64)
        val dst = Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888)
        val homography = computeHomography(
            src = srcCorners,
            dst = floatArrayOf(
                0f, 0f,
                dstWidth.toFloat(), 0f,
                dstWidth.toFloat(), dstHeight.toFloat(),
                0f, dstHeight.toFloat(),
            ),
        )
        val pixels = IntArray(dstWidth * dstHeight)
        val srcPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(srcPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        for (y in 0 until dstHeight) {
            for (x in 0 until dstWidth) {
                val tx = x.toFloat()
                val ty = y.toFloat()
                val (sx, sy) = applyHomography(homography, tx, ty)
                val blended = if (strength >= 0.999f) {
                    sampleBilinear(srcPixels, bitmap.width, bitmap.height, sx, sy)
                } else {
                    val linear = sampleBilinear(srcPixels, bitmap.width, bitmap.height, tx * bitmap.width / dstWidth, ty * bitmap.height / dstHeight)
                    val warped = sampleBilinear(srcPixels, bitmap.width, bitmap.height, sx, sy)
                    blendColors(linear, warped, strength)
                }
                pixels[y * dstWidth + x] = blended
            }
        }
        dst.setPixels(pixels, 0, dstWidth, 0, 0, dstWidth, dstHeight)
        return dst
    }

    fun cornersToPixels(quad: QuadPoints, width: Int, height: Int): FloatArray = floatArrayOf(
        quad.topLeft.x * width, quad.topLeft.y * height,
        quad.topRight.x * width, quad.topRight.y * height,
        quad.bottomRight.x * width, quad.bottomRight.y * height,
        quad.bottomLeft.x * width, quad.bottomLeft.y * height,
    )

    fun applyKeystoneCorrection(quad: QuadPoints, vertical: Int, horizontal: Int): QuadPoints {
        val v = (vertical - 50) / 100f * 0.15f
        val h = (horizontal - 50) / 100f * 0.15f
        return quad.copy(
            topLeft = PointF(quad.topLeft.x + h, quad.topLeft.y + v),
            topRight = PointF(quad.topRight.x - h, quad.topRight.y + v),
            bottomLeft = PointF(quad.bottomLeft.x + h, quad.bottomLeft.y - v),
            bottomRight = PointF(quad.bottomRight.x - h, quad.bottomRight.y - v),
        )
    }

    private fun estimateWidth(c: FloatArray): Float =
        max(hypot(c[2] - c[0], c[3] - c[1]), hypot(c[4] - c[6], c[5] - c[7]))

    private fun estimateHeight(c: FloatArray): Float =
        max(hypot(c[6] - c[0], c[7] - c[1]), hypot(c[4] - c[2], c[5] - c[3]))

    private fun computeHomography(src: FloatArray, dst: FloatArray): DoubleArray {
        val a = Array(8) { DoubleArray(8) }
        val b = DoubleArray(8)
        for (i in 0 until 4) {
            val sx = src[i * 2].toDouble()
            val sy = src[i * 2 + 1].toDouble()
            val dx = dst[i * 2].toDouble()
            val dy = dst[i * 2 + 1].toDouble()
            a[i * 2][0] = sx; a[i * 2][1] = sy; a[i * 2][2] = 1.0; a[i * 2][6] = -dx * sx; a[i * 2][7] = -dx * sy
            b[i * 2] = dx
            a[i * 2 + 1][3] = sx; a[i * 2 + 1][4] = sy; a[i * 2 + 1][5] = 1.0; a[i * 2 + 1][6] = -dy * sx; a[i * 2 + 1][7] = -dy * sy
            b[i * 2 + 1] = dy
        }
        val h = solveLinear8x8(a, b)
        return doubleArrayOf(h[0], h[1], h[2], h[3], h[4], h[5], h[6], h[7], 1.0)
    }

    private fun applyHomography(h: DoubleArray, x: Float, y: Float): Pair<Float, Float> {
        val w = h[6] * x + h[7] * y + h[8]
        val sx = (h[0] * x + h[1] * y + h[2]) / w
        val sy = (h[3] * x + h[4] * y + h[5]) / w
        return sx.toFloat() to sy.toFloat()
    }

    private fun solveLinear8x8(a: Array<DoubleArray>, b: DoubleArray): DoubleArray {
        val n = 8
        val aug = Array(n) { i -> DoubleArray(n + 1) { j -> if (j < n) a[i][j] else b[i] } }
        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > abs(aug[pivot][col])) pivot = row
            }
            if (pivot != col) {
                val tmp = aug[col]; aug[col] = aug[pivot]; aug[pivot] = tmp
            }
            val div = aug[col][col].takeIf { abs(it) > 1e-9 } ?: 1e-9
            for (j in col..n) aug[col][j] /= div
            for (row in 0 until n) {
                if (row == col) continue
                val factor = aug[row][col]
                for (j in col..n) aug[row][j] -= factor * aug[col][j]
            }
        }
        return DoubleArray(n) { aug[it][n] }
    }

    private fun sampleBilinear(pixels: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        if (x < 0 || y < 0 || x >= w - 1 || y >= h - 1) return 0xFFFFFFFF.toInt()
        val x0 = x.toInt(); val y0 = y.toInt()
        val fx = x - x0; val fy = y - y0
        val c00 = pixels[y0 * w + x0]; val c10 = pixels[y0 * w + x0 + 1]
        val c01 = pixels[(y0 + 1) * w + x0]; val c11 = pixels[(y0 + 1) * w + x0 + 1]
        return lerpColor(lerpColor(c00, c10, fx), lerpColor(c01, c11, fx), fy)
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val a = ((c1 ushr 24) * (1 - t) + (c2 ushr 24) * t).roundToInt().coerceIn(0, 255)
        val r = (((c1 shr 16) and 0xFF) * (1 - t) + ((c2 shr 16) and 0xFF) * t).roundToInt()
        val g = (((c1 shr 8) and 0xFF) * (1 - t) + ((c2 shr 8) and 0xFF) * t).roundToInt()
        val b = ((c1 and 0xFF) * (1 - t) + (c2 and 0xFF) * t).roundToInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun blendColors(c1: Int, c2: Int, t: Float): Int = lerpColor(c1, c2, t.coerceIn(0f, 1f))
}

object DocumentEdgeDetector {
    /**
     * Detect document corners — OpenCV (lazy, multi-pipeline) first, Sobel fallback.
     * OpenCV loads only when this method runs (scanner editor), not at app startup.
     */
    fun detectCorners(bitmap: Bitmap, hints: DocumentDetectionHints = DocumentDetectionHints()): QuadPoints {
        OpenCvDocumentScannerEngine.tryDetect(bitmap, hints)?.let { return it }
        return detectCornersFallback(bitmap, hints.scanType)
    }

    fun detectCorners(bitmap: Bitmap, scanType: DocumentScanType): QuadPoints =
        detectCorners(bitmap, DocumentDetectionHints(scanType = scanType))

    fun detectSkewDegrees(corners: QuadPoints): Float =
        OpenCvDocumentScannerEngine.tryDetectSkew(corners)

    private fun detectCornersFallback(bitmap: Bitmap, scanType: DocumentScanType = DocumentScanType.AUTO): QuadPoints {
        val scaled = if (max(bitmap.width, bitmap.height) > 800) {
            val scale = 800f / max(bitmap.width, bitmap.height)
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).roundToInt().coerceAtLeast(1),
                (bitmap.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else bitmap

        val w = scaled.width
        val h = scaled.height
        val gray = toGray(scaled)
        val edges = sobel(gray, w, h)
        val threshold = edges.average() * 1.5
        var minX = w; var maxX = 0; var minY = h; var maxY = 0
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                if (edges[y * w + x] > threshold) {
                    minX = min(minX, x); maxX = max(maxX, x)
                    minY = min(minY, y); maxY = max(maxY, y)
                }
            }
        }
        if (maxX <= minX || maxY <= minY) return defaultQuad(scanType)
        val (padXFactor, padYFactor) = paddingForScanType(scanType)
        val padX = (maxX - minX) * padXFactor
        val padY = (maxY - minY) * padYFactor
        return QuadPoints(
            topLeft = PointF((minX + padX) / w, (minY + padY) / h),
            topRight = PointF((maxX - padX) / w, (minY + padY) / h),
            bottomRight = PointF((maxX - padX) / w, (maxY - padY) / h),
            bottomLeft = PointF((minX + padX) / w, (maxY - padY) / h),
        )
    }

    private fun defaultQuad(scanType: DocumentScanType): QuadPoints = when (scanType) {
        DocumentScanType.RECEIPT -> QuadPoints(
            topLeft = PointF(0.08f, 0.05f),
            topRight = PointF(0.92f, 0.05f),
            bottomRight = PointF(0.92f, 0.95f),
            bottomLeft = PointF(0.08f, 0.95f),
        )
        DocumentScanType.WHITEBOARD -> QuadPoints(
            topLeft = PointF(0.02f, 0.02f),
            topRight = PointF(0.98f, 0.02f),
            bottomRight = PointF(0.98f, 0.98f),
            bottomLeft = PointF(0.02f, 0.98f),
        )
        else -> QuadPoints()
    }

    private fun paddingForScanType(scanType: DocumentScanType): Pair<Float, Float> = when (scanType) {
        DocumentScanType.BOOK -> 0.01f to 0.04f
        DocumentScanType.RECEIPT -> 0.03f to 0.01f
        DocumentScanType.WHITEBOARD -> 0.005f to 0.005f
        DocumentScanType.CONTRACT -> 0.02f to 0.02f
        DocumentScanType.FORM -> 0.025f to 0.025f
        DocumentScanType.AUTO -> 0.02f to 0.02f
    }

    private fun toGray(bitmap: Bitmap): IntArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return IntArray(pixels.size) { i ->
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            (0.299 * r + 0.587 * g + 0.114 * b).roundToInt()
        }
    }

    private fun sobel(gray: IntArray, w: Int, h: Int): DoubleArray {
        val out = DoubleArray(w * h)
        for (y in 1 until h - 1) {
            for (x in 1 until w - 1) {
                val gx = -gray[(y - 1) * w + x - 1] + gray[(y - 1) * w + x + 1] +
                    -2 * gray[y * w + x - 1] + 2 * gray[y * w + x + 1] +
                    -gray[(y + 1) * w + x - 1] + gray[(y + 1) * w + x + 1]
                val gy = -gray[(y - 1) * w + x - 1] - 2 * gray[(y - 1) * w + x] - gray[(y - 1) * w + x + 1] +
                    gray[(y + 1) * w + x - 1] + 2 * gray[(y + 1) * w + x] + gray[(y + 1) * w + x + 1]
                out[y * w + x] = hypot(gx.toDouble(), gy.toDouble())
            }
        }
        return out
    }
}
