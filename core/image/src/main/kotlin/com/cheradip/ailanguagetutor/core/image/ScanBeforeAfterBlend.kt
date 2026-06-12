package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.max
import kotlin.math.min

object ScanBeforeAfterBlend {
    /** blend=0 → original, blend=1 → edited */
    fun blend(original: Bitmap, edited: Bitmap, blend: Float): Bitmap {
        val t = blend.coerceIn(0f, 1f)
        if (t <= 0.01f) return original.copy(Bitmap.Config.ARGB_8888, true)
        if (t >= 0.99f) return edited.copy(Bitmap.Config.ARGB_8888, true)
        val width = max(original.width, edited.width)
        val height = max(original.height, edited.height)
        val scaledOriginal = if (original.width != width || original.height != height) {
            Bitmap.createScaledBitmap(original, width, height, true)
        } else original
        val scaledEdited = if (edited.width != width || edited.height != height) {
            Bitmap.createScaledBitmap(edited, width, height, true)
        } else edited
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(scaledOriginal, 0f, 0f, Paint().apply { alpha = ((1f - t) * 255).toInt() })
        canvas.drawBitmap(scaledEdited, 0f, 0f, Paint().apply { alpha = (t * 255).toInt() })
        return out
    }
}
