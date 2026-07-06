package com.cheradip.ailanguagetutor.core.image

/** Export profiles per upgrade.md — presets for ScanExportOptionsPanel. */
enum class ScanExportProfile(val label: String) {
    DOCUMENT("Document"),
    PRINT("Print"),
    ARCHIVE("Archive"),
    OCR("OCR"),
}

fun ExportOptions.withProfile(profile: ScanExportProfile): ExportOptions = when (profile) {
    ScanExportProfile.DOCUMENT -> copy(
        format = ExportFormat.PDF,
        quality = ExportQuality.HIGH,
        compression = ExportCompression.BALANCED,
        pageSize = ExportPageSize.ORIGINAL,
        margins = ExportMargins.SMALL,
    )
    ScanExportProfile.PRINT -> copy(
        format = ExportFormat.PDF,
        quality = ExportQuality.HIGH,
        compression = ExportCompression.MAXIMUM,
        pageSize = ExportPageSize.A4,
        margins = ExportMargins.MEDIUM,
        orientation = ExportOrientation.PORTRAIT,
    )
    ScanExportProfile.ARCHIVE -> copy(
        format = ExportFormat.PDF,
        quality = ExportQuality.ORIGINAL,
        compression = ExportCompression.MAXIMUM,
        pageSize = ExportPageSize.ORIGINAL,
        margins = ExportMargins.NONE,
    )
    ScanExportProfile.OCR -> copy(
        format = ExportFormat.PDF,
        quality = ExportQuality.HIGH,
        compression = ExportCompression.BALANCED,
        pageSize = ExportPageSize.ORIGINAL,
        margins = ExportMargins.SMALL,
    )
}
