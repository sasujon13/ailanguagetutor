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
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** OpenCV Canny + contour quadrilateral detection for premium edge finding. */
object OpenCvDocumentDetector {
    @Volatile
    private var loaded = false

    fun ensureLoaded(): Boolean {
        if (loaded) return true
        loaded = OpenCVLoader.initLocal()
        return loaded
    }

    fun detectCorners(bitmap: Bitmap, scanType: DocumentScanType): QuadPoints? {
        if (!ensureLoaded()) return null
        val scaled = scaleDown(bitmap, 1200)
        val src = Mat()
        Utils.bitmapToMat(scaled, src)
        val gray = Mat()
        Imgproc.cvtColor(src, gray, Imgproc.COLOR_RGBA2GRAY)
        Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)
        val edges = Mat()
        Imgproc.Canny(gray, edges, 75.0, 200.0)
        Imgproc.dilate(edges, edges, Mat.ones(Size(3.0, 3.0), edges.type()))
        val contours = ArrayList<MatOfPoint>()
        Imgproc.findContours(edges, contours, Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE)
        val w = scaled.width.toDouble()
        val h = scaled.height.toDouble()
        val imageArea = w * h
        var best: QuadPoints? = null
        var bestArea = 0.0
        for (contour in contours) {
            val contour2f = MatOfPoint2f(*contour.toArray())
            val peri = Imgproc.arcLength(contour2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(contour2f, approx, 0.02 * peri, true)
            if (approx.total() != 4L) continue
            val area = abs(Imgproc.contourArea(approx))
            if (area < imageArea * 0.08) continue
            if (!Imgproc.isContourConvex(MatOfPoint(*approx.toArray()))) continue
            if (area <= bestArea) continue
            val quad = orderPoints(approx.toArray(), w, h)
            bestArea = area
            best = quad
        }
        src.release()
        gray.release()
        edges.release()
        if (best == null) return null
        return applyScanPadding(best, scanType)
    }

    fun detectSkewDegrees(corners: QuadPoints): Float {
        val dx = corners.topRight.x - corners.topLeft.x
        val dy = corners.topRight.y - corners.topLeft.y
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
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
            DocumentScanType.AUTO -> 0.02f to 0.02f
        }
        return QuadPoints(
            topLeft = PointF(min(1f, quad.topLeft.x + padX), min(1f, quad.topLeft.y + padY)),
            topRight = PointF(max(0f, quad.topRight.x - padX), min(1f, quad.topRight.y + padY)),
            bottomRight = PointF(max(0f, quad.bottomRight.x - padX), max(0f, quad.bottomRight.y - padY)),
            bottomLeft = PointF(min(1f, quad.bottomLeft.x + padX), max(0f, quad.bottomLeft.y - padY)),
        )
    }
}
