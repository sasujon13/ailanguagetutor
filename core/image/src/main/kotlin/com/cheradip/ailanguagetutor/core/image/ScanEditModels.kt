package com.cheradip.ailanguagetutor.core.image

enum class ScanTool {
    ORIGINAL,
    CROP,
    TRANSITION,
    CLEAN,
    GRAY,
    SAVE,
}

enum class DocumentScanType {
    AUTO,
    BOOK,
    RECEIPT,
    WHITEBOARD,
    CONTRACT,
    FORM,
}

enum class CropPreset {
    FREEFORM,
    RECTANGLE,
    A4,
    LETTER,
    LEGAL,
    BUSINESS_CARD,
    ID_CARD,
    PASSPORT,
}

enum class GrayMode {
    STANDARD,
    HIGH_CONTRAST,
    NEWSPAPER,
    SOFT,
    OCR_OPTIMIZED,
    RECEIPT,
    BOOK,
    HANDWRITTEN,
    HISTORICAL,
}

enum class ExportFormat {
    PDF,
    IMAGES,
    LONG_IMAGE,
}

enum class ExportQuality { LOW, MEDIUM, HIGH, ORIGINAL }

enum class ExportCompression { SMALL, BALANCED, MAXIMUM }

enum class ExportPageSize { ORIGINAL, A4, LETTER, LEGAL }

enum class ExportMargins { NONE, SMALL, MEDIUM, LARGE }

enum class ExportOrientation { PORTRAIT, LANDSCAPE, AUTO }

/** Normalized corner coordinates (0..1) in reading order: TL, TR, BR, BL. */
data class QuadPoints(
    val topLeft: PointF = PointF(0.05f, 0.05f),
    val topRight: PointF = PointF(0.95f, 0.05f),
    val bottomRight: PointF = PointF(0.95f, 0.95f),
    val bottomLeft: PointF = PointF(0.05f, 0.95f),
) {
    fun toFloatArray(): FloatArray = floatArrayOf(
        topLeft.x, topLeft.y,
        topRight.x, topRight.y,
        bottomRight.x, bottomRight.y,
        bottomLeft.x, bottomLeft.y,
    )

    fun toAxisAlignedRectangle(): QuadPoints {
        val xs = listOf(topLeft.x, topRight.x, bottomRight.x, bottomLeft.x)
        val ys = listOf(topLeft.y, topRight.y, bottomRight.y, bottomLeft.y)
        val minX = xs.min().coerceIn(0f, 1f)
        val maxX = xs.max().coerceIn(0f, 1f)
        val minY = ys.min().coerceIn(0f, 1f)
        val maxY = ys.max().coerceIn(0f, 1f)
        return QuadPoints(
            topLeft = PointF(minX, minY),
            topRight = PointF(maxX, minY),
            bottomRight = PointF(maxX, maxY),
            bottomLeft = PointF(minX, maxY),
        )
    }

    companion object {
        fun fullFrame(): QuadPoints = QuadPoints(
            topLeft = PointF(0f, 0f),
            topRight = PointF(1f, 0f),
            bottomRight = PointF(1f, 1f),
            bottomLeft = PointF(0f, 1f),
        )

        fun fromFloatArray(values: FloatArray): QuadPoints {
            require(values.size >= 8)
            return QuadPoints(
                topLeft = PointF(values[0], values[1]),
                topRight = PointF(values[2], values[3]),
                bottomRight = PointF(values[4], values[5]),
                bottomLeft = PointF(values[6], values[7]),
            )
        }
    }
}

data class PointF(val x: Float, val y: Float)

data class CropParams(
    val corners: QuadPoints = QuadPoints.fullFrame(),
    val preset: CropPreset = CropPreset.RECTANGLE,
    val rotationDegrees: Float = 0f,
    val autoStraighten: Boolean = false,
    val perspectiveCorrection: Boolean = false,
    val keystoneCorrection: Boolean = false,
    val horizontalAlignment: Boolean = false,
    val verticalAlignment: Boolean = false,
)

data class TransitionParams(
    val corners: QuadPoints = QuadPoints(),
    val perspectiveStrength: Int = 80,
    val rotationDegrees: Float = 0f,
    val verticalCorrection: Int = 50,
    val horizontalCorrection: Int = 50,
    val pageFlattening: Int = 50,
    val autoDetect: Boolean = true,
    val autoStraightenText: Boolean = false,
    val curvedPageCorrection: Boolean = false,
    val scanType: DocumentScanType = DocumentScanType.AUTO,
)

data class CleanParams(
    val brightness: Int = 50,
    val contrast: Int = 50,
    val sharpness: Int = 50,
    val noiseReduction: Int = 30,
    val shadowRemoval: Int = 40,
    val paperWhitening: Int = 35,
    val inkEnhancement: Int = 45,
    val autoEnhance: Boolean = false,
    val adaptiveThreshold: Boolean = false,
    val preserveSignatures: Boolean = true,
    val preserveStamps: Boolean = true,
    val preserveLogos: Boolean = true,
)

data class GrayParams(
    val mode: GrayMode = GrayMode.STANDARD,
    val brightness: Int = 50,
    val contrast: Int = 50,
    val exposure: Int = 50,
    val gamma: Int = 50,
    val blackPoint: Int = 5,
    val whitePoint: Int = 95,
    val darkenText: Boolean = false,
    val lightenPaper: Boolean = false,
    val improveOcrAccuracy: Boolean = false,
)

enum class WatermarkMode { NONE, CUSTOM, TIMESTAMP }

data class ExportOptions(
    val format: ExportFormat = ExportFormat.PDF,
    val documentName: String = "",
    val quality: ExportQuality = ExportQuality.HIGH,
    val compression: ExportCompression = ExportCompression.BALANCED,
    val pageSize: ExportPageSize = ExportPageSize.ORIGINAL,
    val margins: ExportMargins = ExportMargins.SMALL,
    val orientation: ExportOrientation = ExportOrientation.AUTO,
    val passwordEnabled: Boolean = false,
    val password: String = "",
    val watermark: String = "",
    val author: String = "",
    val title: String = "",
    val subject: String = "",
    val keywords: String = "",
    val useTimestampWatermark: Boolean = false,
    val watermarkMode: WatermarkMode = WatermarkMode.NONE,
)

enum class EditStage { ORIGINAL, CROP, TRANSITION, CLEAN, GRAY }

data class EditHistorySnapshot(
    val appliedCrop: CropParams? = null,
    val appliedTransition: TransitionParams? = null,
    val appliedClean: CleanParams? = null,
    val appliedGray: GrayParams? = null,
)

data class EditHistoryEntry(
    val stage: EditStage,
    val label: String,
    val timestampMs: Long = System.currentTimeMillis(),
    val snapshot: EditHistorySnapshot? = null,
)

data class PageEditState(
    val pageId: Long,
    val originalPath: String,
    val workingPath: String,
    val appliedCrop: CropParams? = null,
    val appliedTransition: TransitionParams? = null,
    val appliedClean: CleanParams? = null,
    val appliedGray: GrayParams? = null,
    val history: List<EditHistoryEntry> = emptyList(),
    val historyIndex: Int = -1,
    val draftCrop: CropParams = CropParams(),
    val draftTransition: TransitionParams = TransitionParams(),
    val draftClean: CleanParams = CleanParams(),
    val draftGray: GrayParams = GrayParams(),
) {
    fun appliedStages(): List<EditStage> = buildList {
        if (appliedCrop != null) add(EditStage.CROP)
        if (appliedTransition != null) add(EditStage.TRANSITION)
        if (appliedClean != null) add(EditStage.CLEAN)
        if (appliedGray != null) add(EditStage.GRAY)
    }
}

fun CropPreset.aspectRatio(): Float? = when (this) {
    CropPreset.A4 -> 210f / 297f
    CropPreset.LETTER -> 8.5f / 11f
    CropPreset.LEGAL -> 8.5f / 14f
    CropPreset.BUSINESS_CARD -> 3.5f / 2f
    CropPreset.ID_CARD -> 85.6f / 54f
    CropPreset.PASSPORT -> 125f / 88f
    else -> null
}
