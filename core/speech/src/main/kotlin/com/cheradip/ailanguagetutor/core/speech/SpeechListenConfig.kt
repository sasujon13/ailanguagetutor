package com.cheradip.ailanguagetutor.core.speech

data class SpeechListenConfig(
    val continuous: Boolean = false,
    /** End the session after this much silence once listening starts or an utterance finishes. */
    val silenceTimeoutMs: Long = 7_000L,
    /** Pause length before Android finalizes one utterance (continuous mode only). */
    val utteranceSilenceMs: Long = 2_000L,
)
