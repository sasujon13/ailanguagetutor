package com.cheradip.ailanguagetutor.core.image

/** Document classification per upgrade.md routing rules. */
enum class ScanDocumentClass {
    TEXT_HEAVY,
    VISUAL_HEAVY,
    MIXED,
    OFFICIAL_ID,
    MACHINE_READABLE,
    HANDWRITTEN,
    DAMAGED,
}

data class ScanAnalysisMetrics(
    val blurScore: Float,
    val brightness: Float,
    val contrast: Float,
    val shadowSeverity: Float,
    val colorRichness: Float,
    val edgeDensity: Float,
    val wrinkleScore: Float,
    val damageScore: Float,
    val hasMachineReadable: Boolean,
    val aspectRatio: Float,
)

data class ScanEnhanceRecommendation(
    val documentClass: ScanDocumentClass,
    val recommendedMode: ScanEnhanceMode,
    val recommendedLevel: Int,
    val dewarpCap: Float,
    val label: String,
)
