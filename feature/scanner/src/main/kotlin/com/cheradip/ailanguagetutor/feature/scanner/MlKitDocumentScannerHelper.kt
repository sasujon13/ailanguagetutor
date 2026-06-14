package com.cheradip.ailanguagetutor.feature.scanner

import android.app.Activity
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.tasks.await

/** Google ML Kit Document Scanner — premium capture with auto dewarp and edge detection. */
object MlKitDocumentScannerHelper {
    sealed interface ScanActivityOutcome {
        data class Success(val pages: List<ByteArray>) : ScanActivityOutcome
        data object Cancelled : ScanActivityOutcome
        data class Failed(val message: String) : ScanActivityOutcome
    }

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

    /**
     * Parses the ML Kit scanner activity result safely.
     *
     * Note: Play Services may log `GmsDocScanDelAct: Failed to handle scanning result` when the
     * user closes the scanner without saving — that is internal to Google and not an app crash.
     */
    fun handleActivityResult(
        resultCode: Int,
        data: Intent?,
        readUri: (Uri) -> ByteArray?,
    ): ScanActivityOutcome {
        if (resultCode == Activity.RESULT_CANCELED) return ScanActivityOutcome.Cancelled
        if (resultCode != Activity.RESULT_OK) {
            return ScanActivityOutcome.Cancelled
        }
        if (data == null) return ScanActivityOutcome.Cancelled

        val scanResult = runCatching {
            GmsDocumentScanningResult.fromActivityResultIntent(data)
        }.getOrElse { error ->
            return ScanActivityOutcome.Failed(
                error.message?.takeIf { it.isNotBlank() }
                    ?: "Could not read scan result. Try gallery import.",
            )
        }

        val pages = extractPageBytes(scanResult, readUri)
        if (pages.isEmpty()) {
            return ScanActivityOutcome.Failed(
                "No scanned pages were returned. Try again or import from gallery.",
            )
        }
        return ScanActivityOutcome.Success(pages)
    }

    fun extractPageBytes(
        result: GmsDocumentScanningResult?,
        readUri: (Uri) -> ByteArray?,
    ): List<ByteArray> {
        result ?: return emptyList()
        return result.pages?.mapNotNull { page ->
            page.imageUri?.let { uri -> readUri(uri)?.takeIf { it.isNotEmpty() } }
        }.orEmpty()
    }
}
