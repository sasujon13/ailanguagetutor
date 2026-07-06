package com.cheradip.ailanguagetutor.feature.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import com.cheradip.ailanguagetutor.core.image.PointF
import com.cheradip.ailanguagetutor.ui.theme.CheradipTeal

/**
 * Draws detected document boundary over camera preview or still image.
 * When [imageAspectRatio] is set (width/height), maps through ContentScale.Fit letterboxing.
 */
@Composable
fun DocumentBoundaryOverlay(
    polygonNorm: List<PointF>,
    isCurved: Boolean,
    modifier: Modifier = Modifier,
    imageAspectRatio: Float? = null,
    strokeColor: Color = CheradipTeal,
) {
    if (polygonNorm.size < 3) return
    Canvas(modifier = modifier.fillMaxSize()) {
        val fit = fitCenterRect(size.width, size.height, imageAspectRatio)
        val path = Path()
        polygonNorm.forEachIndexed { index, p ->
            val x = fit.left + p.x * fit.width
            val y = fit.top + p.y * fit.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()

        clipPath(path, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
            drawRect(Color.Black.copy(alpha = 0.35f))
        }

        val stroke = Stroke(
            width = if (isCurved) 3.5f else 3f,
            pathEffect = if (isCurved) PathEffect.dashPathEffect(floatArrayOf(16f, 10f)) else null,
        )
        drawPath(path, color = strokeColor, style = stroke)

        polygonNorm.forEach { p ->
            drawCircle(
                color = strokeColor,
                radius = 6f,
                center = Offset(fit.left + p.x * fit.width, fit.top + p.y * fit.height),
            )
        }
    }
}

private data class FitRect(val left: Float, val top: Float, val width: Float, val height: Float)

private fun fitCenterRect(containerW: Float, containerH: Float, imageAspectRatio: Float?): FitRect {
    if (imageAspectRatio == null || imageAspectRatio <= 0f) {
        return FitRect(0f, 0f, containerW, containerH)
    }
    val containerAspect = containerW / containerH
    return if (imageAspectRatio > containerAspect) {
        val drawW = containerW
        val drawH = containerW / imageAspectRatio
        FitRect(0f, (containerH - drawH) / 2f, drawW, drawH)
    } else {
        val drawH = containerH
        val drawW = containerH * imageAspectRatio
        FitRect((containerW - drawW) / 2f, 0f, drawW, drawH)
    }
}
