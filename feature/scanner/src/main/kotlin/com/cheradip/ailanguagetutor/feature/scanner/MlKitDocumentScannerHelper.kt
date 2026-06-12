package com.cheradip.ailanguagetutor.feature.scanner

import android.app.Activity
import android.content.IntentSender
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.tasks.await

/** Google ML Kit Document Scanner — premium capture with auto dewarp and edge detection. */
object MlKitDocumentScannerHelper {
    fun buildOptions(pageLimit: Int = 20): GmsDocumentScannerOptions =
        GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()

    suspend fun getScanIntentSender(activity: Activity, pageLimit: Int = 20): IntentSender {
        val scanner = GmsDocumentScanning.getClient(buildOptions(pageLimit))
        return scanner.getStartScanIntent(activity).await()
    }

    fun extractPageBytes(result: GmsDocumentScanningResult?, readUri: (android.net.Uri) -> ByteArray): List<ByteArray> {
        result ?: return emptyList()
        return result.pages?.mapNotNull { page ->
            page.imageUri?.let { uri -> runCatching { readUri(uri) }.getOrNull() }
        }.orEmpty()
    }
}
