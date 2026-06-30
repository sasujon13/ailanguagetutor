package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Detect curved / multi-point document boundaries and dewarp to a flat rectangle. */
object CurvedDocumentDetector {

    fun detect(bitmap: Bitmap, hints: DocumentDetectionHints = DocumentDetectionHints()): CurveBoundary {
        OpenCvCurvedContourEngine.tryDetect(bitmap, hints)?.let { return it }
        val quad = DocumentEdgeDetector.detectCorners(bitmap, hints)
        return CurveBoundary.fromQuad(quad, curveStrength = if (hints.scanType == DocumentScanType.BOOK) 0.12f else 0.08f)
    }

    fun detectFromQuad(quad: QuadPoints, scanType: DocumentScanType = DocumentScanType.AUTO): CurveBoundary =
        CurveBoundary.fromQuad(quad, curveStrength = if (scanType == DocumentScanType.BOOK) 0.12f else 0.08f)
}

object CurvedBoundaryWarp {

    /** Dewarp using horizontal scanline mapping through a closed polygon boundary. */
    fun warp(bitmap: Bitmap, boundary: CurveBoundary): Bitmap {
        if (!boundary.isValid) return bitmap
        val poly = boundary.toPolygon(samplesPerSegment = 16)
        if (poly.size < 4) return bitmap

        val minY = poly.minOf { it.y }
        val maxY = poly.maxOf { it.y }
        val heightSpan = (maxY - minY).coerceAtLeast(0.01f)
        val dstHeight = estimateHeight(poly, bitmap.width, bitmap.height).roundToInt().coerceIn(64, bitmap.height)
        val dstWidth = estimateWidth(poly, bitmap.width, bitmap.height).roundToInt().coerceIn(64, bitmap.width)

        val srcPixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(srcPixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val dstPixels = IntArray(dstWidth * dstHeight)

        for (row in 0 until dstHeight) {
            val ny = minY + heightSpan * row / max(dstHeight - 1, 1)
            val intersections = horizontalIntersections(poly, ny)
            if (intersections.size < 2) continue
            val left = intersections.min()
            val right = intersections.max()
            val span = (right - left).coerceAtLeast(0.001f)
            for (col in 0 until dstWidth) {
                val t = col.toFloat() / max(dstWidth - 1, 1)
                val sx = (left + span * t) * bitmap.width
                val sy = ny * bitmap.height
                dstPixels[row * dstWidth + col] = sampleBilinear(
                    srcPixels,
                    bitmap.width,
                    bitmap.height,
                    sx,
                    sy,
                )
            }
        }

        return Bitmap.createBitmap(dstWidth, dstHeight, Bitmap.Config.ARGB_8888).also {
            it.setPixels(dstPixels, 0, dstWidth, 0, 0, dstWidth, dstHeight)
        }
    }

    private fun estimateWidth(poly: List<PointF>, imageW: Int, imageH: Int): Float {
        var maxW = 0f
        val steps = 24
        val minY = poly.minOf { it.y }
        val maxY = poly.maxOf { it.y }
        for (i in 0..steps) {
            val ny = minY + (maxY - minY) * i / steps
            val xs = horizontalIntersections(poly, ny)
            if (xs.size >= 2) {
                maxW = max(maxW, (xs.max() - xs.min()) * imageW)
            }
        }
        return maxW.coerceAtLeast(64f)
    }

    private fun estimateHeight(poly: List<PointF>, imageW: Int, imageH: Int): Float {
        val minY = poly.minOf { it.y }
        val maxY = poly.maxOf { it.y }
        return (maxY - minY).coerceAtLeast(0.01f) * imageH
    }

    private fun horizontalIntersections(poly: List<PointF>, y: Float): List<Float> {
        val xs = mutableListOf<Float>()
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            if ((a.y <= y && b.y > y) || (b.y <= y && a.y > y)) {
                val t = (y - a.y) / (b.y - a.y)
                xs += a.x + t * (b.x - a.x)
            }
        }
        return xs
    }

    private fun sampleBilinear(pixels: IntArray, w: Int, h: Int, x: Float, y: Float): Int {
        val cx = x.coerceIn(0f, (w - 1).toFloat())
        val cy = y.coerceIn(0f, (h - 1).toFloat())
        val x0 = cx.toInt()
        val y0 = cy.toInt()
        val x1 = min(x0 + 1, w - 1)
        val y1 = min(y0 + 1, h - 1)
        val tx = cx - x0
        val ty = cy - y0
        val c00 = pixels[y0 * w + x0]
        val c10 = pixels[y0 * w + x1]
        val c01 = pixels[y1 * w + x0]
        val c11 = pixels[y1 * w + x1]
        return lerpColor(
            lerpColor(c00, c10, tx),
            lerpColor(c01, c11, tx),
            ty,
        )
    }

    private fun lerpColor(c1: Int, c2: Int, t: Float): Int {
        val a = ((c1 ushr 24) and 0xFF) + (((c2 ushr 24) and 0xFF) - ((c1 ushr 24) and 0xFF)) * t
        val r = ((c1 ushr 16) and 0xFF) + (((c2 ushr 16) and 0xFF) - ((c1 ushr 16) and 0xFF)) * t
        val g = ((c1 ushr 8) and 0xFF) + (((c2 ushr 8) and 0xFF) - ((c1 ushr 8) and 0xFF)) * t
        val b = (c1 and 0xFF) + ((c2 and 0xFF) - (c1 and 0xFF)) * t
        return (a.roundToInt().coerceIn(0, 255) shl 24) or
            (r.roundToInt().coerceIn(0, 255) shl 16) or
            (g.roundToInt().coerceIn(0, 255) shl 8) or
            b.roundToInt().coerceIn(0, 255)
    }
}
