package com.cheradip.ailanguagetutor.feature.scanner

import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.request.ImageRequest
import com.cheradip.ailanguagetutor.core.image.CropParams
import com.cheradip.ailanguagetutor.core.image.CurveBoundary
import com.cheradip.ailanguagetutor.core.image.QuadPoints
import java.io.File
import kotlin.math.max
import kotlin.math.min

fun effectiveCurveBoundary(crop: CropParams): CurveBoundary =
    crop.curveBoundary?.takeIf { it.isValid } ?: CurveBoundary.fromQuad(crop.corners)

fun imageContentFitRect(container: IntSize, imageWidth: Int, imageHeight: Int): Rect {
    if (container.width <= 0 || container.height <= 0 || imageWidth <= 0 || imageHeight <= 0) {
        return Rect(
            0f,
            0f,
            max(container.width, 1).toFloat(),
            max(container.height, 1).toFloat(),
        )
    }
    val scale = min(
        container.width.toFloat() / imageWidth,
        container.height.toFloat() / imageHeight,
    )
    val w = imageWidth * scale
    val h = imageHeight * scale
    val left = (container.width - w) / 2f
    val top = (container.height - h) / 2f
    return Rect(left, top, left + w, top + h)
}

fun cornerToOffset(point: com.cheradip.ailanguagetutor.core.image.PointF, fit: Rect): Offset =
    Offset(fit.left + point.x * fit.width, fit.top + point.y * fit.height)

@Composable
fun scanPreviewImageModel(path: String, cacheKey: String): Any {
    val context = LocalContext.current
    return androidx.compose.runtime.remember(path, cacheKey) {
        ImageRequest.Builder(context)
            .data(File(path))
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .build()
    }
}
