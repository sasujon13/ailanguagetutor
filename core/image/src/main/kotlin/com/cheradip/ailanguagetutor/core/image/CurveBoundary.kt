package com.cheradip.ailanguagetutor.core.image

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Pen-tool style closed boundary: anchor points with optional cubic-bezier handles.
 * Normalized coordinates (0..1) relative to image width/height.
 */
data class CurveAnchor(
    val x: Float,
    val y: Float,
    val handleIn: PointF? = null,
    val handleOut: PointF? = null,
)

data class CurveBoundary(
    val anchors: List<CurveAnchor> = emptyList(),
    val closed: Boolean = true,
) {
    val isValid: Boolean get() = anchors.size >= 4

    fun boundingQuad(): QuadPoints {
        if (anchors.isEmpty()) return QuadPoints.fullFrame()
        val xs = anchors.map { it.x }
        val ys = anchors.map { it.y }
        val minX = xs.min().coerceIn(0f, 1f)
        val maxX = xs.max().coerceIn(0f, 1f)
        val minY = ys.min().coerceIn(0f, 1f)
        val maxY = ys.max().coerceIn(0f, 1f)
        return QuadPoints(
            topLeft = PointF(minX, minY),
            topRight = PointF(maxX, minY),
            bottomRight = PointF(maxX, maxY),
            bottomLeft = PointF(minX, maxY),
        )
    }

    /** Flatten beziers into a dense polygon for warp + hit-testing. */
    fun toPolygon(samplesPerSegment: Int = 12): List<PointF> {
        if (anchors.size < 2) return anchors.map { PointF(it.x, it.y) }
        val n = anchors.size
        val out = ArrayList<PointF>(n * samplesPerSegment)
        for (i in 0 until n) {
            if (!closed && i == n - 1) break
            val a0 = anchors[i]
            val a1 = anchors[(i + 1) % n]
            val p0 = PointF(a0.x, a0.y)
            val p1 = a0.handleOut?.let { PointF(it.x, it.y) } ?: p0
            val p3 = PointF(a1.x, a1.y)
            val p2 = a1.handleIn?.let { PointF(it.x, it.y) } ?: p3
            for (s in 0 until samplesPerSegment) {
                val t = s.toFloat() / samplesPerSegment
                out += cubicPoint(p0, p1, p2, p3, t)
            }
        }
        return out
    }

    companion object {
        fun fromQuad(quad: QuadPoints, curveStrength: Float = 0.08f): CurveBoundary {
            val pts = listOf(
                PointF(quad.topLeft.x, quad.topLeft.y),
                PointF(quad.topRight.x, quad.topRight.y),
                PointF(quad.bottomRight.x, quad.bottomRight.y),
                PointF(quad.bottomLeft.x, quad.bottomLeft.y),
            )
            return fromPolygon(pts, curveStrength)
        }

        fun fromPolygon(points: List<PointF>, curveStrength: Float = 0.06f): CurveBoundary {
            if (points.size < 4) {
                return CurveBoundary(
                    anchors = points.map { CurveAnchor(it.x, it.y) },
                )
            }
            val ordered = orderClockwise(points)
            val anchors = ordered.mapIndexed { i, p ->
                val prev = ordered[(i - 1 + ordered.size) % ordered.size]
                val next = ordered[(i + 1) % ordered.size]
                val dx = next.x - prev.x
                val dy = next.y - prev.y
                val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(0.001f)
                val nx = dx / len
                val ny = dy / len
                val seg = min(
                    hypot((next.x - p.x).toDouble(), (next.y - p.y).toDouble()).toFloat(),
                    hypot((p.x - prev.x).toDouble(), (p.y - prev.y).toDouble()).toFloat(),
                ) * curveStrength
                CurveAnchor(
                    x = p.x.coerceIn(0f, 1f),
                    y = p.y.coerceIn(0f, 1f),
                    handleIn = PointF(p.x - nx * seg, p.y - ny * seg),
                    handleOut = PointF(p.x + nx * seg, p.y + ny * seg),
                )
            }
            return CurveBoundary(anchors = anchors)
        }

        fun orderClockwise(points: List<PointF>): List<PointF> {
            if (points.size <= 2) return points
            val cx = points.map { it.x }.average().toFloat()
            val cy = points.map { it.y }.average().toFloat()
            return points.sortedBy { kotlin.math.atan2((it.y - cy).toDouble(), (it.x - cx).toDouble()) }
        }

        private fun cubicPoint(p0: PointF, p1: PointF, p2: PointF, p3: PointF, t: Float): PointF {
            val u = 1f - t
            val tt = t * t
            val uu = u * u
            val uuu = uu * u
            val ttt = tt * t
            val x = uuu * p0.x + 3f * uu * t * p1.x + 3f * u * tt * p2.x + ttt * p3.x
            val y = uuu * p0.y + 3f * uu * t * p1.y + 3f * u * tt * p2.y + ttt * p3.y
            return PointF(x, y)
        }
    }
}

fun CurveBoundary.toJson(): JSONObject = JSONObject().apply {
    put("closed", closed)
    put(
        "anchors",
        JSONArray().apply {
            anchors.forEach { a ->
                put(
                    JSONObject().apply {
                        put("x", a.x)
                        put("y", a.y)
                        a.handleIn?.let { put("hix", it.x); put("hiy", it.y) }
                        a.handleOut?.let { put("hox", it.x); put("hoy", it.y) }
                    },
                )
            }
        },
    )
}

fun JSONObject.toCurveBoundary(): CurveBoundary? {
    val arr = optJSONArray("anchors") ?: return null
    if (arr.length() < 4) return null
    val anchors = buildList {
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            add(
                CurveAnchor(
                    x = o.getDouble("x").toFloat(),
                    y = o.getDouble("y").toFloat(),
                    handleIn = if (o.has("hix")) {
                        PointF(o.getDouble("hix").toFloat(), o.getDouble("hiy").toFloat())
                    } else {
                        null
                    },
                    handleOut = if (o.has("hox")) {
                        PointF(o.getDouble("hox").toFloat(), o.getDouble("hoy").toFloat())
                    } else {
                        null
                    },
                ),
            )
        }
    }
    return CurveBoundary(anchors = anchors, closed = optBoolean("closed", true))
}
