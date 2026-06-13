package com.cheradip.ailanguagetutor.core.image

/** Hints for picking the right document quad on cluttered backgrounds (e.g. ID card on a letter). */
data class DocumentDetectionHints(
    val scanType: DocumentScanType = DocumentScanType.AUTO,
    val cropPreset: CropPreset? = null,
) {
    fun targetAspectRatio(): Float? = cropPreset?.aspectRatio() ?: scanType.preferredAspectRatio()

    /** Prefer a smaller isolated document instead of the full page / desk. */
    fun preferSmallDocument(): Boolean = when (cropPreset) {
        CropPreset.ID_CARD,
        CropPreset.BUSINESS_CARD,
        CropPreset.PASSPORT,
        -> true
        else -> scanType == DocumentScanType.RECEIPT
    }
}

fun DocumentScanType.preferredAspectRatio(): Float? = when (this) {
    DocumentScanType.RECEIPT -> 0.35f
    else -> null
}

fun DocumentScanType.maxAreaRatio(): Float = when (this) {
    DocumentScanType.RECEIPT -> 0.55f
    DocumentScanType.BOOK -> 0.95f
    else -> 0.88f
}

fun DocumentScanType.minAreaRatio(): Float = when (this) {
    DocumentScanType.RECEIPT -> 0.04f
    else -> 0.06f
}
