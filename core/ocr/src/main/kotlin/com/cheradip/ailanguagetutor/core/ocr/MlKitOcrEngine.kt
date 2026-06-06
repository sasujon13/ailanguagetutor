package com.cheradip.ailanguagetutor.core.ocr

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.cheradip.ailanguagetutor.core.model.WordBoundingBox
import com.cheradip.ailanguagetutor.core.model.WordSpan
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class OcrResult(
    val fullText: String,
    val words: List<WordSpan>,
    val wordMapJson: String,
)

@Singleton
class MlKitOcrEngine @Inject constructor(
    private val wordMapBuilder: WordMapBuilder,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(imagePath: String): OcrResult {
        val bitmap = loadScaledBitmap(imagePath)
        val image = InputImage.fromBitmap(bitmap, 0)
        val visionText = recognizer.process(image).await()
        val fullText = visionText.text
        val words = wordMapBuilder.buildFromMlKit(visionText, fullText)
        val wordMapJson = wordMapBuilder.toJson(words)
        return OcrResult(fullText = fullText, words = words, wordMapJson = wordMapJson)
    }

    private fun loadScaledBitmap(path: String): Bitmap {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        val maxEdge = maxOf(options.outWidth, options.outHeight)
        var sampleSize = 1
        while (maxEdge / sampleSize > 2048) sampleSize *= 2
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeFile(path, decodeOptions)
            ?: error("Cannot decode image: $path")
    }
}
