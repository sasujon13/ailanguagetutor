package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** OpenCV multi-point contour detection for curved document edges. */
internal object OpenCvCurvedContourEngine {
    @Volatile
    private var loadAttempted = false
    @Volatile
    private var loadSucceeded = false

    fun tryDetect(bitmap: Bitmap, hints: DocumentDetectionHints): CurveBoundary? {
        if (!ensureLoaded()) return null
        val scaled = scaleDown(bitmap, 1200)
        val src = Mat()
        Utils.bitmapToMat(scaled, src)
        val edges = Mat()
        val gray = Mat()
        val blurred = Mat()
        return try {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.Canny(blurred, edges, 50.0, 150.0)
            Imgproc.dilate(edges, edges, Mat.ones(Size(3.0, 3.0), edges.type()))
            val poly = extractBestContour(edges, scaled.width, scaled.height, hints)
            if (poly == null) {
                null
            } else {
                val curveStrength = if (hints.scanType == DocumentScanType.BOOK) 0.14f else 0.08f
                CurveBoundary.fromPolygon(poly, curveStrength)
            }
        } catch (_: Throwable) {
            null
        } finally {
            gray.release()
            blurred.release()
            edges.release()
            src.release()
        }
    }

    private fun extractBestContour(
        binary: Mat,
        width: Int,
        height: Int,
        hints: DocumentDetectionHints,
    ): List<PointF>? {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(binary, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        val imageArea = width.toDouble() * height
        var best: List<PointF>? = null
        var bestScore = 0.0
        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            if (peri < 100) continue
            val area = abs(Imgproc.contourArea(contour2f))
            val areaRatio = area / imageArea
            if (areaRatio < hints.scanType.minAreaRatio() || areaRatio > 0.96) continue
            for (epsFactor in listOf(0.008, 0.012, 0.018, 0.025)) {
                val approx = MatOfPoint2f()
                Imgproc.approxPolyDP(contour2f, approx, epsFactor * peri, true)
                val count = approx.total().toInt()
                if (count !in 5..24) continue
                if (!Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) continue
                val pts = approx.toArray().map { p ->
                    PointF(
                        (p.x / width).toFloat().coerceIn(0f, 1f),
                        (p.y / height).toFloat().coerceIn(0f, 1f),
                    )
                }
                val score = areaRatio * count.coerceAtMost(16) * rectangularityScore(approx.toArray())
                if (score > bestScore) {
                    bestScore = score
                    best = CurveBoundary.orderClockwise(pts)
                }
            }
        }
        hierarchy.release()
        return best
    }

    private fun rectangularityScore(pts: Array<Point>): Double {
        if (pts.size < 4) return 0.0
        var score = 0.0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val c = pts[(i + 2) % pts.size]
            val v1x = b.x - a.x
            val v1y = b.y - a.y
            val v2x = c.x - b.x
            val v2y = c.y - b.y
            val dot = v1x * v2x + v1y * v2y
            val m1 = hypot(v1x, v1y)
            val m2 = hypot(v2x, v2y)
            if (m1 > 1 && m2 > 1) {
                val cos = abs(dot / (m1 * m2))
                score += cos
            }
        }
        return score / pts.size
    }

    private fun ensureLoaded(): Boolean {
        if (loadAttempted) return loadSucceeded
        synchronized(this) {
            if (loadAttempted) return loadSucceeded
            loadAttempted = true
            loadSucceeded = runCatching { OpenCVLoader.initLocal() }.getOrDefault(false)
            return loadSucceeded
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val maxDim = max(bitmap.width, bitmap.height)
        if (maxDim <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / maxDim
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
    }
}
