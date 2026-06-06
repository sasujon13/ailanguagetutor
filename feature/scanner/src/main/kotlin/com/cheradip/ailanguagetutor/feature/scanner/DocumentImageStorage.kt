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

    fun createPageFile(documentId: Long, pageIndex: Int): File {
        val docDir = File(docsDir, documentId.toString()).also { it.mkdirs() }
        return File(docDir, "page_${pageIndex}_${timestamp()}.jpg")
    }

    fun copyFromUriBytes(documentId: Long, pageIndex: Int, bytes: ByteArray): SavedImage {
        val file = createPageFile(documentId, pageIndex)
        file.writeBytes(bytes)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, options)
        return SavedImage(
            path = file.absolutePath,
            width = options.outWidth,
            height = options.outHeight,
        )
    }

    fun saveCapturedBytes(documentId: Long, pageIndex: Int, bytes: ByteArray): SavedImage =
        copyFromUriBytes(documentId, pageIndex, bytes)

    data class SavedImage(val path: String, val width: Int, val height: Int)

    private fun timestamp(): String =
        SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
