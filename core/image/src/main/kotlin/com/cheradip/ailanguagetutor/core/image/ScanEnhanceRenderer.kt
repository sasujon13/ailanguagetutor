package com.cheradip.ailanguagetutor.core.image

import android.graphics.Bitmap
import java.io.File

object ScanEnhanceRenderer {

    fun render(
        originalPath: String,
        mode: ScanEnhanceMode,
        level: Int,
        premium: Boolean = true,
    ): Bitmap {
        val source = BitmapUtils.load(originalPath)
        if (level <= 0) return source
        val result = ScanEnhancePipeline.enhance(source, mode, level, premium)
        if (result.bitmap !== source) source.recycle()
        return result.bitmap
    }

    fun renderToFile(
        originalPath: String,
        mode: ScanEnhanceMode,
        level: Int,
        outFile: File,
        quality: Int = 92,
        premium: Boolean = true,
    ): String {
        if (level <= 0) {
            val original = File(originalPath)
            if (original.absolutePath != outFile.absolutePath) {
                original.copyTo(outFile, overwrite = true)
            }
            return outFile.absolutePath
        }
        val source = BitmapUtils.load(originalPath)
        val result = ScanEnhancePipeline.enhance(source, mode, level, premium)
        BitmapUtils.save(result.bitmap, outFile.absolutePath, quality)
        if (result.bitmap !== source) result.bitmap.recycle()
        source.recycle()
        return outFile.absolutePath
    }
}
