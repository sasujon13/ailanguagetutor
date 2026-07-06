package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import kotlin.math.hypot

/** Detected document boundary for live scan overlay and post-capture review. */
data class LiveScanBoundary(
    /** Closed polygon in normalized 0..1 coordinates (image space). */
    val polygonNorm: List<PointF>,
    val quad: QuadPoints,
    /** True when boundary is non-rectangular (curved / wrinkled page). */
    val isCurved: Boolean,
) {
    companion object {
        val empty = LiveScanBoundary(
            polygonNorm = QuadPoints.fullFrame().toPolygon(),
            quad = QuadPoints.fullFrame(),
            isCurved = false,
        )
    }
}

private fun QuadPoints.toPolygon(): List<PointF> = listOf(
    topLeft, topRight, bottomRight, bottomLeft, topLeft,
)

object LiveScanBoundaryDetector {

    /**
     * Fast boundary detect for live preview frames (downscale before call).
     * Uses OpenCV contour when available; falls back to quad edge detect.
     */
    fun detect(bitmap: Bitmap, hints: DocumentDetectionHints = DocumentDetectionHints()): LiveScanBoundary {
        val curve = CurvedDocumentDetector.detect(bitmap, hints)
        val polygon = curve.toPolygon(samplesPerSegment = 10)
        val quad = if (curve.isValid) curve.boundingQuad() else DocumentEdgeDetector.detectCorners(bitmap, hints)
        val isCurved = isCurvedBoundary(curve, quad, polygon)
        val path = if (polygon.size >= 4) polygon else quad.toPolygon()
        return LiveScanBoundary(path, quad, isCurved)
    }

    private fun isCurvedBoundary(
        curve: CurveBoundary,
        quad: QuadPoints,
        polygon: List<PointF>,
    ): Boolean {
        if (curve.anchors.size > 4) return true
        if (polygon.size < 8) return false
        val corners = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        var maxDist = 0f
        for (p in polygon) {
            val d = corners.minOf { hypot((p.x - it.x).toDouble(), (p.y - it.y).toDouble()).toFloat() }
            maxDist = maxOf(maxDist, d)
        }
        return maxDist > 0.025f
    }
}
