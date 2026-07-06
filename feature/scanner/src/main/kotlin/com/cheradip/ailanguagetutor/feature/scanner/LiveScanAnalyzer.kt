package com.cheradip.ailanguagetutor.feature.scanner

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.cheradip.ailanguagetutor.core.image.LiveScanBoundary
import com.cheradip.ailanguagetutor.core.image.LiveScanBoundaryDetector
import com.cheradip.ailanguagetutor.core.image.PointF
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean

/** Throttled OpenCV boundary detection on CameraX preview frames. */
class LiveScanAnalyzer(
    private val onBoundary: (List<PointF>, Boolean) -> Unit,
    private val minIntervalMs: Long = 120L,
) : ImageAnalysis.Analyzer {

    private val busy = AtomicBoolean(false)
    private var lastRunMs = 0L

    override fun analyze(imageProxy: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now - lastRunMs < minIntervalMs || !busy.compareAndSet(false, true)) {
            imageProxy.close()
            return
        }
        lastRunMs = now
        val rotation = imageProxy.imageInfo.rotationDegrees
        val bitmap = imageProxy.toBitmap(rotation)
        imageProxy.close()
        if (bitmap == null) {
            busy.set(false)
            return
        }
        try {
            val maxEdge = maxOf(bitmap.width, bitmap.height)
            val scaled = if (maxEdge > 480) {
                val s = 480f / maxEdge
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * s).toInt().coerceAtLeast(1),
                    (bitmap.height * s).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            val boundary: LiveScanBoundary = LiveScanBoundaryDetector.detect(scaled)
            if (scaled !== bitmap) scaled.recycle()
            onBoundary(boundary.polygonNorm, boundary.isCurved)
        } catch (_: Exception) {
            // keep last boundary on failure
        } finally {
            bitmap.recycle()
            busy.set(false)
        }
    }
}

private fun ImageProxy.toBitmap(rotationDegrees: Int): Bitmap? {
    if (format != ImageFormat.YUV_420_888 || planes.size < 3) return null
    val yBuffer = planes[0].buffer
    val uBuffer = planes[1].buffer
    val vBuffer = planes[2].buffer
    val ySize = yBuffer.remaining()
    val uSize = uBuffer.remaining()
    val vSize = vBuffer.remaining()
    val nv21 = ByteArray(ySize + uSize + vSize)
    yBuffer.get(nv21, 0, ySize)
    vBuffer.get(nv21, ySize, vSize)
    uBuffer.get(nv21, ySize + vSize, uSize)
    val yuv = YuvImage(nv21, ImageFormat.NV21, width, height, null)
    val out = ByteArrayOutputStream()
    yuv.compressToJpeg(Rect(0, 0, width, height), 80, out)
    var bitmap = BitmapFactory.decodeByteArray(out.toByteArray(), 0, out.size()) ?: return null
    if (rotationDegrees != 0) {
        val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) bitmap.recycle()
        bitmap = rotated
    }
    return bitmap
}
