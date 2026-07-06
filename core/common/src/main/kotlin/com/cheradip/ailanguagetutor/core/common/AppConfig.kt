package com.cheradip.ailanguagetutor.core.common

interface AppConfig {
    val apiBaseUrl: String
    val adminSeedPassword: String
    val homeAiBaseUrl: String
    /** Max wait for GET /health before falling back to cloud AI. */
    val homeAiReachabilityTimeoutMs: Long
    /** Max wait for a Home AI inference response once the server is reachable. */
    val homeAiResponseTimeoutMs: Long
    val cloudApiTimeoutMs: Long
}

/** Home AI reachability probe (not answer latency). */
const val HOME_AI_REACHABILITY_TIMEOUT_MS = 3_000L

/** Home AI inference response budget after server is reachable. */
const val HOME_AI_RESPONSE_TIMEOUT_MS = 120_000L

@Deprecated("Use HOME_AI_REACHABILITY_TIMEOUT_MS", ReplaceWith("HOME_AI_REACHABILITY_TIMEOUT_MS"))
const val HOME_AI_TIMEOUT_MS = HOME_AI_REACHABILITY_TIMEOUT_MS

/** Cloud API (cheradip.com/ailt/api) read timeout default. */
const val CLOUD_API_TIMEOUT_MS = 30_000L
