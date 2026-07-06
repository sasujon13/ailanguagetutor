package com.cheradip.ailanguagetutor.core.image

/**
 * Global scan-enhancement constants per upgrade.md.
 * Language-agnostic — image metrics only; same for all users worldwide.
 */
object ScanEnhanceStandards {
    const val MIN_LEVEL = 0
    const val MAX_LEVEL = 7
    const val COMPARE_PREVIEW_LOW = 1
    const val COMPARE_PREVIEW_HIGH = 7
    const val ANALYZE_MAX_EDGE_PX = 640
    const val READABILITY_ROLLBACK_RATIO = 0.92f
    const val EXPORT_JPEG_QUALITY = 92

    object Classify {
        const val OFFICIAL_ID_ASPECT_MIN = 0.55f
        const val OFFICIAL_ID_ASPECT_MAX = 1.8f
        const val OFFICIAL_ID_EDGE_MIN = 0.08f
        const val OFFICIAL_ID_COLOR_MAX = 0.25f
        const val DAMAGE_SCORE_MIN = 0.35f
        const val WRINKLE_SCORE_MIN = 0.45f
        const val VISUAL_COLOR_MIN = 0.35f
        const val VISUAL_EDGE_MAX = 0.12f
        const val TEXT_EDGE_MIN = 0.14f
        const val TEXT_COLOR_MAX = 0.2f
        const val HANDWRITTEN_EDGE_MAX = 0.06f
    }

    object Recommend {
        const val DEFAULT_LEVEL = 3
        const val DEFAULT_DEWARP_CAP = 0.7f
        const val SHADOW_BOOST_THRESHOLD = 0.4f
        const val BLUR_BOOST_THRESHOLD = 0.15f
    }

    /** Per document-class routing — fixed worldwide defaults. */
    data class Route(
        val level: Int,
        val dewarpCap: Float,
        val forceClean: Boolean = false,
    )

    fun route(docClass: ScanDocumentClass, premium: Boolean): Route = when (docClass) {
        ScanDocumentClass.TEXT_HEAVY -> Route(5, 0.85f)
        ScanDocumentClass.VISUAL_HEAVY -> Route(4, 0.5f)
        ScanDocumentClass.MIXED -> Route(if (premium) 5 else 4, 0.65f)
        ScanDocumentClass.OFFICIAL_ID -> Route(2, 0.25f, forceClean = true)
        ScanDocumentClass.MACHINE_READABLE -> Route(3, 0.2f, forceClean = true)
        ScanDocumentClass.HANDWRITTEN -> Route(3, 0.45f)
        ScanDocumentClass.DAMAGED -> Route(if (premium) 7 else 6, 0.95f)
    }

    fun recommendMode(docClass: ScanDocumentClass, premium: Boolean, forceClean: Boolean): ScanEnhanceMode =
        when {
            forceClean -> ScanEnhanceMode.CLEAN
            !premium -> ScanEnhanceMode.CLEAN
            docClass == ScanDocumentClass.OFFICIAL_ID -> ScanEnhanceMode.CLEAN
            docClass == ScanDocumentClass.MACHINE_READABLE -> ScanEnhanceMode.CLEAN
            else -> ScanEnhanceMode.AI_CLEAN
        }
}
