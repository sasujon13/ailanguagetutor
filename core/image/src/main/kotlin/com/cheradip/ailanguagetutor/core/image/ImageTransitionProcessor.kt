package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import kotlin.math.atan2

/** Perspective / straighten transforms applied during Clean filter stacks. */
object ImageTransitionProcessor {

    fun apply(bitmap: Bitmap, params: TransitionParams): Bitmap {
        var corners = if (params.autoDetect) {
            DocumentEdgeDetector.detectCorners(
                bitmap,
                DocumentDetectionHints(scanType = params.scanType),
            )
        } else {
            params.corners
        }
        corners = PerspectiveTransform.applyKeystoneCorrection(
            corners,
            params.verticalCorrection,
            params.horizontalCorrection,
        )
        if (params.curvedPageCorrection) {
            val curve = params.pageFlattening / 200f
            corners = corners.copy(
                bottomLeft = corners.bottomLeft.copy(x = corners.bottomLeft.x + curve),
                bottomRight = corners.bottomRight.copy(x = corners.bottomRight.x - curve),
            )
        }
        var result = bitmap
        var rotation = params.rotationDegrees
        if (params.autoStraightenText) {
            rotation += computeStraightenDegrees(corners)
        }
        if (rotation != 0f) result = BitmapUtils.rotate(result, rotation)
        val px = PerspectiveTransform.cornersToPixels(corners, result.width, result.height)
        val strength = params.perspectiveStrength / 100f * (params.pageFlattening / 100f + 0.5f)
        return PerspectiveTransform.warp(result, px, strength.coerceIn(0.1f, 1f))
    }

    private fun computeStraightenDegrees(corners: QuadPoints): Float {
        val dx = corners.topRight.x - corners.topLeft.x
        val dy = corners.topRight.y - corners.topLeft.y
        return Math.toDegrees(-atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }
}
