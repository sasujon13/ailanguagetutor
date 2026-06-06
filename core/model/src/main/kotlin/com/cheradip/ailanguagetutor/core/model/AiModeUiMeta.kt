package com.cheradip.ailanguagetutor.core.model

data class AiModeUiMeta(
    val mode: AiEngineMode,
    val emoji: String,
    val label: String,
    val description: String,
)

fun aiModeUiMeta(mode: AiEngineMode): AiModeUiMeta = when (mode) {
    AiEngineMode.SMART_TUTOR -> AiModeUiMeta(
        mode,
        "🔵",
        "Smart Tutor",
        "Questions, explanations, learning (Recommended)",
    )
    AiEngineMode.FAST_TRANSLATION -> AiModeUiMeta(
        mode,
        "🟢",
        "Fast Translation",
        "Instant translation for books and documents",
    )
    AiEngineMode.BALANCED -> AiModeUiMeta(
        mode,
        "🟣",
        "Balanced",
        "OCR docs and mixed content with cleanup",
    )
    AiEngineMode.LIGHTWEIGHT -> AiModeUiMeta(
        mode,
        "🔴",
        "Lightweight",
        "Applied automatically when OCR scan is active",
    )
    AiEngineMode.HIGH_ACCURACY -> AiModeUiMeta(
        mode,
        "🟡",
        "High Accuracy",
        "Difficult texts — Plus subscribers only",
    )
}
