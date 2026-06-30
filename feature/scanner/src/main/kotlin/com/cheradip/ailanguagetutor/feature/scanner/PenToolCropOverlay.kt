package com.cheradip.ailanguagetutor.feature.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cheradip.ailanguagetutor.core.image.CurveAnchor
import com.cheradip.ailanguagetutor.core.image.CurveBoundary
import com.cheradip.ailanguagetutor.core.image.PointF
import kotlin.math.hypot
import kotlin.math.roundToInt

private enum class PenDragTarget { NONE, ANCHOR, HANDLE_IN, HANDLE_OUT }

/** Photoshop-style pen tool for curved document crop boundaries. */
@Composable
fun PenToolCropOverlay(
    boundary: CurveBoundary,
    previewPath: String,
    previewRevision: Long,
    selectedPageId: Long?,
    imageWidth: Int,
    imageHeight: Int,
    interactive: Boolean,
    onBoundaryChanged: (CurveBoundary) -> Unit,
    modifier: Modifier = Modifier,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var dragTarget by remember { mutableStateOf(PenDragTarget.NONE) }
    var magnifierCenter by remember { mutableStateOf<Offset?>(null) }
    val boundaryState = rememberUpdatedState(boundary)
    val fitRect = imageContentFitRect(size, imageWidth, imageHeight)
    val hitRadius = 48f

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .then(
                if (interactive) {
                    Modifier.pointerInput(boundary.anchors.size, size, imageWidth, imageHeight) {
                        val fit = imageContentFitRect(size, imageWidth, imageHeight)
                        detectTapGestures { tap ->
                            val b = boundaryState.value
                            val (anchorHit, _) = hitTest(tap, b, fit, hitRadius)
                            if (anchorHit >= 0) {
                                selectedIndex = anchorHit
                            } else {
                                val insert = nearestSegmentIndex(tap, b, fit)
                                if (insert >= 0) {
                                    val nx = ((tap.x - fit.left) / fit.width).coerceIn(0f, 1f)
                                    val ny = ((tap.y - fit.top) / fit.height).coerceIn(0f, 1f)
                                    val anchors = b.anchors.toMutableList()
                                    anchors.add(insert + 1, CurveAnchor(nx, ny))
                                    onBoundaryChanged(b.copy(anchors = anchors))
                                    selectedIndex = insert + 1
                                }
                            }
                        }
                    }.pointerInput(boundary.anchors.size, size, imageWidth, imageHeight) {
                        val fit = imageContentFitRect(size, imageWidth, imageHeight)
                        detectDragGestures(
                            onDragStart = { offset ->
                                val b = boundaryState.value
                                val (anchor, handle) = hitTest(offset, b, fit, hitRadius)
                                when {
                                    handle == PenDragTarget.HANDLE_IN && anchor >= 0 -> {
                                        selectedIndex = anchor
                                        dragTarget = PenDragTarget.HANDLE_IN
                                    }
                                    handle == PenDragTarget.HANDLE_OUT && anchor >= 0 -> {
                                        selectedIndex = anchor
                                        dragTarget = PenDragTarget.HANDLE_OUT
                                    }
                                    anchor >= 0 -> {
                                        selectedIndex = anchor
                                        dragTarget = PenDragTarget.ANCHOR
                                    }
                                    else -> dragTarget = PenDragTarget.NONE
                                }
                                magnifierCenter = offset
                            },
                            onDragEnd = { dragTarget = PenDragTarget.NONE; magnifierCenter = null },
                            onDragCancel = { dragTarget = PenDragTarget.NONE; magnifierCenter = null },
                            onDrag = { change, _ ->
                                if (dragTarget == PenDragTarget.NONE || selectedIndex < 0) return@detectDragGestures
                                val nx = ((change.position.x - fit.left) / fit.width).coerceIn(0f, 1f)
                                val ny = ((change.position.y - fit.top) / fit.height).coerceIn(0f, 1f)
                                magnifierCenter = change.position
                                val b = boundaryState.value
                                val anchors = b.anchors.toMutableList()
                                val current = anchors[selectedIndex]
                                anchors[selectedIndex] = when (dragTarget) {
                                    PenDragTarget.ANCHOR -> current.copy(x = nx, y = ny)
                                    PenDragTarget.HANDLE_IN -> current.copy(handleIn = PointF(nx, ny))
                                    PenDragTarget.HANDLE_OUT -> current.copy(handleOut = PointF(nx, ny))
                                    PenDragTarget.NONE -> current
                                }
                                onBoundaryChanged(b.copy(anchors = anchors))
                                change.consume()
                            },
                        )
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width == 0 || fitRect.width <= 0f || !boundary.isValid) return@Canvas
            val path = buildCurvePath(boundary, fitRect)
            val shadePath = Path.combine(
                PathOperation.Difference,
                Path().apply { addRect(Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())) },
                path,
            )
            drawPath(shadePath, Color.Black.copy(alpha = if (interactive) 0.4f else 0.35f))
            drawPath(path, Color(0xFF00897B), style = Stroke(width = if (interactive) 3f else 2.5f))
            boundary.anchors.forEachIndexed { index, anchor ->
                val center = normToOffset(anchor.x, anchor.y, fitRect)
                val selected = index == selectedIndex
                drawCircle(
                    if (selected) Color(0xFFFF9800) else Color(0xFF00897B),
                    radius = if (selected) 16f else 14f,
                    center = center,
                )
                drawCircle(Color.White, radius = 8f, center = center)
                if (interactive && selected) {
                    anchor.handleIn?.let { hi ->
                        val hp = normToOffset(hi.x, hi.y, fitRect)
                        drawLine(Color(0xFF80CBC4), center, hp, strokeWidth = 2f)
                        drawCircle(Color(0xFF004D40), radius = 10f, center = hp)
                    }
                    anchor.handleOut?.let { ho ->
                        val hp = normToOffset(ho.x, ho.y, fitRect)
                        drawLine(Color(0xFF80CBC4), center, hp, strokeWidth = 2f)
                        drawCircle(Color(0xFF004D40), radius = 10f, center = hp)
                    }
                }
            }
        }
        if (interactive) {
            magnifierCenter?.let { center ->
                Box(
                    modifier = Modifier
                        .offset { IntOffset((center.x - 40).roundToInt(), (center.y - 90).roundToInt()) }
                        .size(80.dp)
                        .clip(CircleShape),
                ) {
                    AsyncImage(
                        model = scanPreviewImageModel(
                            path = previewPath,
                            cacheKey = "scan-magnifier-$selectedPageId-$previewRevision",
                        ),
                        contentDescription = "Magnifier",
                        modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 2.5f; scaleY = 2.5f },
                        contentScale = ContentScale.Crop,
                    )
                }
            }
        }
    }
}

@Composable
fun CurveGuideOverlay(
    boundary: CurveBoundary,
    imageWidth: Int,
    imageHeight: Int,
    modifier: Modifier = Modifier,
) {
    PenToolCropOverlay(
        boundary = boundary,
        previewPath = "",
        previewRevision = 0L,
        selectedPageId = null,
        imageWidth = imageWidth,
        imageHeight = imageHeight,
        interactive = false,
        onBoundaryChanged = {},
        modifier = modifier,
    )
}

private fun buildCurvePath(boundary: CurveBoundary, fit: Rect): Path {
    val path = Path()
    val anchors = boundary.anchors
    if (anchors.isEmpty()) return path
    val first = anchors.first()
    path.moveTo(normToOffset(first.x, first.y, fit).x, normToOffset(first.x, first.y, fit).y)
    for (i in anchors.indices) {
        if (!boundary.closed && i == anchors.lastIndex) break
        val a0 = anchors[i]
        val a1 = anchors[(i + 1) % anchors.size]
        val p0 = normToOffset(a0.x, a0.y, fit)
        val p1 = a0.handleOut?.let { normToOffset(it.x, it.y, fit) } ?: p0
        val p3 = normToOffset(a1.x, a1.y, fit)
        val p2 = a1.handleIn?.let { normToOffset(it.x, it.y, fit) } ?: p3
        path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
    }
    if (boundary.closed) path.close()
    return path
}

private fun hitTest(
    offset: Offset,
    boundary: CurveBoundary,
    fit: Rect,
    radius: Float,
): Pair<Int, PenDragTarget> {
    boundary.anchors.forEachIndexed { index, anchor ->
        anchor.handleIn?.let { hi ->
            if (distance(offset, normToOffset(hi.x, hi.y, fit)) <= radius) {
                return index to PenDragTarget.HANDLE_IN
            }
        }
        anchor.handleOut?.let { ho ->
            if (distance(offset, normToOffset(ho.x, ho.y, fit)) <= radius) {
                return index to PenDragTarget.HANDLE_OUT
            }
        }
        if (distance(offset, normToOffset(anchor.x, anchor.y, fit)) <= radius) {
            return index to PenDragTarget.ANCHOR
        }
    }
    return -1 to PenDragTarget.NONE
}

private fun nearestSegmentIndex(tap: Offset, boundary: CurveBoundary, fit: Rect): Int {
    if (boundary.anchors.size < 2) return -1
    var bestIndex = -1
    var bestDist = Float.MAX_VALUE
    val poly = boundary.toPolygon(8)
    for (i in poly.indices) {
        val a = normToOffset(poly[i].x, poly[i].y, fit)
        val b = normToOffset(poly[(i + 1) % poly.size].x, poly[(i + 1) % poly.size].y, fit)
        val d = distanceToSegment(tap, a, b)
        if (d < bestDist) {
            bestDist = d
            bestIndex = i * boundary.anchors.size / poly.size
        }
    }
    return if (bestDist < 56f) bestIndex.coerceIn(0, boundary.anchors.size - 1) else -1
}

private fun distance(a: Offset, b: Offset): Float = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble()).toFloat()

private fun distanceToSegment(p: Offset, a: Offset, b: Offset): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    if (dx == 0f && dy == 0f) return distance(p, a)
    val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
    val clamped = t.coerceIn(0f, 1f)
    return distance(p, Offset(a.x + clamped * dx, a.y + clamped * dy))
}

private fun normToOffset(nx: Float, ny: Float, fit: Rect): Offset =
    Offset(fit.left + nx * fit.width, fit.top + ny * fit.height)
