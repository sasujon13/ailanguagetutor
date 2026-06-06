package com.cheradip.ailanguagetutor.core.model

/**
 * v2.0.0 — subscription tiers, AI engine modes, and backend routing.
 * See ailanguagetutor.md → Version 2.0.0.
 */
enum class SubscriptionTier {
    FREE,
    PRO,
    PLUS,
}

enum class AiBackend {
    /** v1 default — cheradip.com cloud pool (api/ailt/ai) */
    CLOUD_POOL,
    /** v2 — user's HOME_AI_BASE_URL (Intel Arc FastAPI) */
    LOCAL_HOME,
}

enum class ProcessingIntent {
    ANSWER,
    TRANSLATION,
}

enum class InputSource {
    OCR_SCAN,
    TYPED,
    VOICE,
}

/** Curated AI modes 1–5 (user never sees raw model names). */
enum class AiEngineMode(val id: Int) {
    SMART_TUTOR(1),
    FAST_TRANSLATION(2),
    BALANCED(3),
    LIGHTWEIGHT(4),
    HIGH_ACCURACY(5),
    ;

    companion object {
        fun fromId(id: Int): AiEngineMode? = entries.find { it.id == id }
    }
}

fun availableAiModes(tier: SubscriptionTier): List<AiEngineMode> = when (tier) {
    SubscriptionTier.PLUS -> AiEngineMode.entries
    SubscriptionTier.PRO -> listOf(
        AiEngineMode.SMART_TUTOR,
        AiEngineMode.FAST_TRANSLATION,
        AiEngineMode.BALANCED,
        AiEngineMode.LIGHTWEIGHT,
    )
    SubscriptionTier.FREE -> emptyList()
}

fun resolveAiEngineMode(
    userSelected: AiEngineMode,
    inputSource: InputSource,
    tier: SubscriptionTier,
): AiEngineMode {
    if (inputSource == InputSource.OCR_SCAN) return AiEngineMode.LIGHTWEIGHT
    if (userSelected == AiEngineMode.HIGH_ACCURACY && tier != SubscriptionTier.PLUS) {
        return AiEngineMode.SMART_TUTOR
    }
    return userSelected
}
