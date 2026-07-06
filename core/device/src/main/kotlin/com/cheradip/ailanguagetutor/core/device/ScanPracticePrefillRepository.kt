package com.cheradip.ailanguagetutor.core.device

import com.cheradip.ailanguagetutor.core.model.ScannedContentType
import javax.inject.Inject
import javax.inject.Singleton

/** OCR + AI structure result passed from scan processing into Practice hub (one-shot). */
data class ScanPracticePrefill(
    val structuredText: String,
    val rawOcrText: String,
    val contentType: ScannedContentType,
    val documentClass: String?,
    val previewImagePath: String?,
    val ocrConfidence: Float,
    val structureBackend: String,
    val documentId: Long,
)

@Singleton
class ScanPracticePrefillRepository @Inject constructor() {
    @Volatile
    private var pending: ScanPracticePrefill? = null

    fun set(prefill: ScanPracticePrefill) {
        pending = prefill
    }

    fun consume(): ScanPracticePrefill? {
        val value = pending
        pending = null
        return value
    }
}
