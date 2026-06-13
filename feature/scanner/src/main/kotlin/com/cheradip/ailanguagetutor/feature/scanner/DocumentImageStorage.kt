package com.cheradip.ailanguagetutor.feature.scanner

import android.content.Context
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentImageStorage @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val docsDir: File
        get() = File(context.filesDir, "documents").also { it.mkdirs() }

    fun createPageFile(documentId: Long, pageIndex: Int, prefix: String = "page"): File {
        val docDir = File(docsDir, documentId.toString()).also { it.mkdirs() }
        return File(docDir, "${prefix}_${pageIndex}_${timestamp()}.jpg")
    }

    fun copyFromUriBytes(documentId: Long, pageIndex: Int, bytes: ByteArray): SavedImage {
        val docDir = File(docsDir, documentId.toString()).also { it.mkdirs() }
        val ts = timestamp()
        val originalFile = File(docDir, "original_${pageIndex}_$ts.jpg")
        originalFile.writeBytes(bytes)
        val workingFile = File(docDir, "page_${pageIndex}_$ts.jpg")
        originalFile.copyTo(workingFile, overwrite = true)
        val (w, h) = decodeBounds(workingFile.absolutePath)
        return SavedImage(
            path = workingFile.absolutePath,
            originalPath = originalFile.absolutePath,
            width = w,
            height = h,
        )
    }

    fun saveCapturedBytes(documentId: Long, pageIndex: Int, bytes: ByteArray): SavedImage =
        copyFromUriBytes(documentId, pageIndex, bytes)

    fun createWorkingCopy(documentId: Long, pageKey: Long): File =
        createPageFile(documentId, pageKey.toInt(), prefix = "edited")

    private fun decodeBounds(path: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        return options.outWidth to options.outHeight
    }

    data class SavedImage(
        val path: String,
        val originalPath: String,
        val width: Int,
        val height: Int,
    )

    private fun timestamp(): String =
        "${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}_${System.nanoTime()}"
}
