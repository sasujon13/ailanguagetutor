package com.cheradip.ailanguagetutor.core.image

/** Offline Clean vs premium AI Clean (server when available; offline stack until then). */
enum class ScanEnhanceMode {
    CLEAN,
    AI_CLEAN,
}

data class PageEnhanceChoice(
    val mode: ScanEnhanceMode = ScanEnhanceMode.CLEAN,
    /** Export level: 0 = original, 1 = natural, 7 = maximum. */
    val exportLevel: Int = 0,
)

fun PageEditState.enhanceChoice(): PageEnhanceChoice {
    val mode = enhanceModeName?.let { name ->
        runCatching { ScanEnhanceMode.valueOf(name) }.getOrDefault(ScanEnhanceMode.CLEAN)
    } ?: ScanEnhanceMode.CLEAN
    return PageEnhanceChoice(mode = mode, exportLevel = enhanceExportLevel ?: 0)
}

fun PageEditState.withEnhanceChoice(choice: PageEnhanceChoice): PageEditState = copy(
    enhanceModeName = choice.mode.name,
    enhanceExportLevel = choice.exportLevel,
)
