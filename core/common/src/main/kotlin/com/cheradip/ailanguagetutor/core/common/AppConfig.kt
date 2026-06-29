package com.cheradip.ailanguagetutor.core.common

interface AppConfig {
    val apiBaseUrl: String
    val adminSeedPassword: String
    val homeAiBaseUrl: String
    val homeAiTimeoutMs: Long
    val cloudApiTimeoutMs: Long
}

/** Home PC AI call timeout — fallback to free cloud APIs after this. */
const val HOME_AI_TIMEOUT_MS = 30_000L

/** Cloud API (cheradip.com/ailt/api) read timeout default. */
const val CLOUD_API_TIMEOUT_MS = 30_000L
