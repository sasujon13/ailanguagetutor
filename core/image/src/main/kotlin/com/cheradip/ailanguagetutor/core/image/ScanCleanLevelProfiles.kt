package com.cheradip.ailanguagetutor.core.image

/**
 * Clean / AI Clean levels 0–7 per upgrade.md.
 * Level 0 = original; slider 1–7 maps to progressive enhancement profiles.
 */
object ScanCleanLevelProfiles {

    fun selection(mode: ScanEnhanceMode, level: Int): CleanFilterSelection? {
        if (level <= 0) return null
        return when (mode) {
            ScanEnhanceMode.CLEAN -> cleanSelection(level.coerceIn(1, 7))
            ScanEnhanceMode.AI_CLEAN -> aiCleanSelection(level.coerceIn(1, 7))
        }
    }

    fun label(mode: ScanEnhanceMode, level: Int): String = when (level) {
        0 -> "Original"
        1 -> if (mode == ScanEnhanceMode.CLEAN) "Natural clean" else "AI natural"
        2 -> if (mode == ScanEnhanceMode.CLEAN) "Natural clean+" else "AI light"
        3 -> if (mode == ScanEnhanceMode.CLEAN) "Enhanced natural" else "AI enhanced natural"
        4 -> if (mode == ScanEnhanceMode.CLEAN) "Maximum color" else "AI rich visual"
        5 -> if (mode == ScanEnhanceMode.CLEAN) "Balanced pro" else "AI balanced pro"
        6 -> if (mode == ScanEnhanceMode.CLEAN) "Strong pro" else "AI strong pro"
        7 -> if (mode == ScanEnhanceMode.CLEAN) "Maximum clean" else "AI maximum"
        else -> "Level $level"
    }

    private fun cleanSelection(level: Int): CleanFilterSelection = when (level) {
        1 -> CleanFilterSelection(
            presetIds = listOf("document"),
            adjustments = mapOf(CleanAdjustmentKind.STRAIGHTEN to 1),
        )
        2 -> CleanFilterSelection(
            presetIds = listOf("document"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 2,
                CleanAdjustmentKind.BRIGHTNESS to 1,
                CleanAdjustmentKind.CONTRAST to 1,
            ),
        )
        3 -> CleanFilterSelection(
            presetIds = listOf("document", "shadow_fix"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 3,
                CleanAdjustmentKind.CONTRAST to 2,
                CleanAdjustmentKind.BRIGHTNESS to 1,
            ),
        )
        4 -> CleanFilterSelection(
            presetIds = listOf("document", "magic"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 3,
                CleanAdjustmentKind.CONTRAST to 3,
                CleanAdjustmentKind.BRIGHTNESS to 2,
            ),
        )
        5 -> CleanFilterSelection(
            presetIds = listOf("document", "shadow_fix", "sharp"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 5,
                CleanAdjustmentKind.CONTRAST to 3,
            ),
        )
        6 -> CleanFilterSelection(
            presetIds = listOf("document", "denoise", "shadow_fix", "sharp"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 6,
                CleanAdjustmentKind.CONTRAST to 4,
            ),
        )
        else -> CleanFilterSelection(
            presetIds = listOf("document", "sharp", "denoise", "shadow_fix", "ocr"),
            adjustments = mapOf(CleanAdjustmentKind.STRAIGHTEN to 7),
        )
    }

    /** Offline fallback when Home AI scan-enhance is unreachable. */
    private fun aiCleanSelection(level: Int): CleanFilterSelection = when (level) {
        1 -> CleanFilterSelection(
            presetIds = listOf("magic", "document"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 2,
                CleanAdjustmentKind.CONTRAST to 1,
            ),
        )
        2 -> CleanFilterSelection(
            presetIds = listOf("magic", "document", "shadow_fix"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 3,
                CleanAdjustmentKind.BRIGHTNESS to 1,
                CleanAdjustmentKind.CONTRAST to 2,
            ),
        )
        3 -> CleanFilterSelection(
            presetIds = listOf("magic", "document", "shadow_fix", "ocr"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 4,
                CleanAdjustmentKind.CONTRAST to 3,
            ),
        )
        4 -> CleanFilterSelection(
            presetIds = listOf("magic", "document", "book", "sharp"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 5,
                CleanAdjustmentKind.CONTRAST to 4,
                CleanAdjustmentKind.BRIGHTNESS to 2,
            ),
        )
        5 -> CleanFilterSelection(
            presetIds = listOf("magic", "document", "book", "shadow_fix", "sharp"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 6,
                CleanAdjustmentKind.CONTRAST to 4,
            ),
        )
        6 -> CleanFilterSelection(
            presetIds = listOf("magic", "ocr", "book", "denoise", "shadow_fix", "sharp"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 7,
                CleanAdjustmentKind.CONTRAST to 5,
            ),
        )
        else -> CleanFilterSelection(
            presetIds = listOf("magic", "ocr", "book", "sharp", "denoise", "shadow_fix"),
            adjustments = mapOf(
                CleanAdjustmentKind.STRAIGHTEN to 7,
                CleanAdjustmentKind.CONTRAST to 5,
            ),
        )
    }
}
