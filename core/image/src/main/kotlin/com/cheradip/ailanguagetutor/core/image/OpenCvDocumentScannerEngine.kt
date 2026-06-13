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
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * OpenCV document quad detection — loaded lazily on first use (never at app startup).
 * Uses multiple pipelines and scores candidates so an ID card on top of printed paper
 * is preferred over the outer page boundary.
 */
internal object OpenCvDocumentScannerEngine {
    @Volatile
    private var loadAttempted = false
    @Volatile
    private var loadSucceeded = false

    fun tryDetect(bitmap: Bitmap, hints: DocumentDetectionHints): QuadPoints? {
        if (!ensureLoaded()) return null
        val scaled = scaleDown(bitmap, 1400)
        val src = Mat()
        Utils.bitmapToMat(scaled, src)
        return runCatching {
            val candidates = buildList {
                addAll(collectQuadsCanny(src))
                addAll(collectQuadsAdaptive(src))
                addAll(collectQuadsOtsu(src))
            }.distinctBy { quadKey(it.quad) }
            pickBest(candidates, src, hints, scaled.width, scaled.height)
        }.getOrNull().also {
            src.release()
        }
    }

    fun tryDetectSkew(corners: QuadPoints): Float {
        val dx = corners.topRight.x - corners.topLeft.x
        val dy = corners.topRight.y - corners.topLeft.y
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
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

    private data class Candidate(val quad: QuadPoints, val areaRatio: Float, val rectangularity: Float)

    private fun collectQuadsCanny(src: Mat): List<Candidate> {
        val gray = Mat()
        val blurred = Mat()
        val edges = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        Imgproc.Canny(blurred, edges, 60.0, 180.0)
        Imgproc.dilate(edges, edges, Mat.ones(Size(3.0, 3.0), edges.type()))
        val result = extractQuads(edges, src.cols(), src.rows())
        gray.release()
        blurred.release()
        edges.release()
        return result
    }

    private fun collectQuadsAdaptive(src: Mat): List<Candidate> {
        val gray = Mat()
        val blurred = Mat()
        val thresh = Mat()
        val closed = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        Imgproc.adaptiveThreshold(
            blurred,
            thresh,
            255.0,
            Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
            Imgproc.THRESH_BINARY,
            11,
            2.0,
        )
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        Imgproc.morphologyEx(thresh, closed, Imgproc.MORPH_CLOSE, kernel)
        val result = extractQuads(closed, src.cols(), src.rows(), useExternalOnly = false)
        gray.release()
        blurred.release()
        thresh.release()
        closed.release()
        kernel.release()
        return result
    }

    private fun collectQuadsOtsu(src: Mat): List<Candidate> {
        val gray = Mat()
        val blurred = Mat()
        val thresh = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
        Imgproc.threshold(blurred, thresh, 0.0, 255.0, Imgproc.THRESH_BINARY + Imgproc.THRESH_OTSU)
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        Imgproc.morphologyEx(thresh, thresh, Imgproc.MORPH_CLOSE, kernel)
        val result = extractQuads(thresh, src.cols(), src.rows(), useExternalOnly = false)
        gray.release()
        blurred.release()
        thresh.release()
        kernel.release()
        return result
    }

    private fun extractQuads(
        binary: Mat,
        width: Int,
        height: Int,
        useExternalOnly: Boolean = true,
    ): List<Candidate> {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        val mode = if (useExternalOnly) Imgproc.RETR_EXTERNAL else Imgproc.RETR_LIST
        Imgproc.findContours(binary, contours, hierarchy, mode, Imgproc.CHAIN_APPROX_SIMPLE)
        val imageArea = width.toDouble() * height
        val out = mutableListOf<Candidate>()
        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            if (peri < 80) continue
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
            if (approx.total() != 4L) continue
            val area = abs(Imgproc.contourArea(approx))
            val areaRatio = (area / imageArea).toFloat()
            if (areaRatio < 0.02f || areaRatio > 0.98f) continue
            if (!Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) continue
            val rect = scoreRectangularity(approx.toArray())
            if (rect < 0.55f) continue
            val quad = orderPoints(approx.toArray(), width.toDouble(), height.toDouble())
            out += Candidate(quad, areaRatio, rect)
        }
        hierarchy.release()
        return out
    }

    private fun pickBest(
        candidates: List<Candidate>,
        src: Mat,
        hints: DocumentDetectionHints,
        width: Int,
        height: Int,
    ): QuadPoints? {
        if (candidates.isEmpty()) return null
        val minArea = hints.scanType.minAreaRatio()
        val maxArea = hints.scanType.maxAreaRatio()
        val targetAspect = hints.targetAspectRatio()
        val preferSmall = hints.preferSmallDocument()

        val filtered = candidates.filter { it.areaRatio in minArea..maxArea }
            .ifEmpty { candidates.filter { it.areaRatio in 0.03f..0.92f } }
        if (filtered.isEmpty()) return null

        val scored = filtered.map { c ->
            val aspect = quadAspectRatio(c.quad)
            val aspectScore = targetAspect?.let { target ->
                val diff = abs(aspect - target) / target
                exp(-diff * 4.0).toFloat()
            } ?: 1f
            val contrast = borderContrastScore(src, c.quad, width, height)
            val sizeScore = if (preferSmall) {
                exp(-c.areaRatio * 3.0).toFloat()
            } else {
                c.areaRatio.coerceIn(0.1f, 0.85f)
            }
            val total = c.rectangularity * 2.2f +
                aspectScore * 2.5f +
                contrast * 2.0f +
                sizeScore * 1.5f
            c to total
        }
        val best = scored.maxByOrNull { it.second }?.first ?: return null
        return applyScanPadding(best.quad, hints.scanType)
    }

    /** Higher when document border separates bright paper from darker background / other text. */
    private fun borderContrastScore(src: Mat, quad: QuadPoints, w: Int, h: Int): Float {
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        val pts = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        var sum = 0.0
        var count = 0
        for (i in pts.indices) {
            val a = pts[i]
            val b = pts[(i + 1) % pts.size]
            val mx = ((a.x + b.x) / 2f * w).toInt().coerceIn(1, w - 2)
            val my = ((a.y + b.y) / 2f * h).toInt().coerceIn(1, h - 2)
            val nx = -(b.y - a.y)
            val ny = (b.x - a.x)
            val len = hypot(nx.toDouble(), ny.toDouble()).coerceAtLeast(1e-6)
            val step = 8
            val inX = (mx + (nx / len * step).toInt()).coerceIn(0, w - 1)
            val inY = (my + (ny / len * step).toInt()).coerceIn(0, h - 1)
            val outX = (mx - (nx / len * step).toInt()).coerceIn(0, w - 1)
            val outY = (my - (ny / len * step).toInt()).coerceIn(0, h - 1)
            val inside = gray.get(inY, inX)[0]
            val outside = gray.get(outY, outX)[0]
            sum += abs(inside - outside)
            count++
        }
        gray.release()
        return (sum / count / 128.0).coerceIn(0.0, 1.0).toFloat()
    }

    private fun scoreRectangularity(pts: Array<Point>): Float {
        if (pts.size != 4) return 0f
        val angles = mutableListOf<Double>()
        for (i in pts.indices) {
            val prev = pts[(i + 3) % 4]
            val cur = pts[i]
            val next = pts[(i + 1) % 4]
            val v1x = prev.x - cur.x
            val v1y = prev.y - cur.y
            val v2x = next.x - cur.x
            val v2y = next.y - cur.y
            val dot = v1x * v2x + v1y * v2y
            val m1 = hypot(v1x, v1y)
            val m2 = hypot(v2x, v2y)
            if (m1 < 1e-6 || m2 < 1e-6) continue
            val cos = (dot / (m1 * m2)).coerceIn(-1.0, 1.0)
            val angle = Math.toDegrees(kotlin.math.acos(cos))
            angles += abs(angle - 90.0)
        }
        if (angles.isEmpty()) return 0f
        val avgDev = angles.average()
        return (1.0 - avgDev / 45.0).coerceIn(0.0, 1.0).toFloat()
    }

    private fun quadAspectRatio(quad: QuadPoints): Float {
        val wTop = hypot(
            (quad.topRight.x - quad.topLeft.x).toDouble(),
            (quad.topRight.y - quad.topLeft.y).toDouble(),
        )
        val wBottom = hypot(
            (quad.bottomRight.x - quad.bottomLeft.x).toDouble(),
            (quad.bottomRight.y - quad.bottomLeft.y).toDouble(),
        )
        val hLeft = hypot(
            (quad.bottomLeft.x - quad.topLeft.x).toDouble(),
            (quad.bottomLeft.y - quad.topLeft.y).toDouble(),
        )
        val hRight = hypot(
            (quad.bottomRight.x - quad.topRight.x).toDouble(),
            (quad.bottomRight.y - quad.topRight.y).toDouble(),
        )
        val w = (wTop + wBottom) / 2.0
        val h = (hLeft + hRight) / 2.0
        return if (h < 1e-6) 1f else (w / h).toFloat()
    }

    private fun scaleDown(bitmap: Bitmap, maxDim: Int): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= maxDim) return bitmap
        val scale = maxDim.toFloat() / maxSide
        return Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).toInt().coerceAtLeast(1),
            (bitmap.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun orderPoints(pts: Array<Point>, w: Double, h: Double): QuadPoints {
        val sorted = pts.sortedWith(compareBy({ it.y }, { it.x }))
        val top = sorted.take(2).sortedBy { it.x }
        val bottom = sorted.takeLast(2).sortedBy { it.x }
        return QuadPoints(
            topLeft = PointF((top[0].x / w).toFloat(), (top[0].y / h).toFloat()),
            topRight = PointF((top[1].x / w).toFloat(), (top[1].y / h).toFloat()),
            bottomRight = PointF((bottom[1].x / w).toFloat(), (bottom[1].y / h).toFloat()),
            bottomLeft = PointF((bottom[0].x / w).toFloat(), (bottom[0].y / h).toFloat()),
        )
    }

    private fun applyScanPadding(quad: QuadPoints, scanType: DocumentScanType): QuadPoints {
        val (padX, padY) = when (scanType) {
            DocumentScanType.BOOK -> 0.01f to 0.04f
            DocumentScanType.RECEIPT -> 0.03f to 0.01f
            DocumentScanType.WHITEBOARD -> 0.005f to 0.005f
            DocumentScanType.CONTRACT -> 0.02f to 0.02f
            DocumentScanType.FORM -> 0.025f to 0.025f
            DocumentScanType.AUTO -> 0.015f to 0.015f
        }
        return QuadPoints(
            topLeft = PointF(min(1f, quad.topLeft.x + padX), min(1f, quad.topLeft.y + padY)),
            topRight = PointF(max(0f, quad.topRight.x - padX), min(1f, quad.topRight.y + padY)),
            bottomRight = PointF(max(0f, quad.bottomRight.x - padX), max(0f, quad.bottomRight.y - padY)),
            bottomLeft = PointF(min(1f, quad.bottomLeft.x + padX), max(0f, quad.bottomLeft.y - padY)),
        )
    }

    private fun quadKey(quad: QuadPoints): String {
        fun r(v: Float) = (v * 50).toInt()
        return "${r(quad.topLeft.x)}${r(quad.topLeft.y)}" +
            "${r(quad.topRight.x)}${r(quad.topRight.y)}" +
            "${r(quad.bottomRight.x)}${r(quad.bottomRight.y)}" +
            "${r(quad.bottomLeft.x)}${r(quad.bottomLeft.y)}"
    }
}
