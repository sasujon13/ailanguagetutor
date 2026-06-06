package com.cheradip.ailanguagetutor.core.ai

data class HomeAiAdminDashboard(
    val inferenceBackend: String = "stub",
    val gpuAvailable: Boolean = false,
    val modelLoaded: String? = null,
    val queueDepth: Int = 0,
    val cacheHitRatePct: Double = 0.0,
    val cacheHitRateL1: Double = 0.0,
    val cacheHitRateL2: Double = 0.0,
    val cacheHitRateL3: Double = 0.0,
    val routesTotal: Int = 0,
    val rateLimitAllowed: Int = 0,
    val rateLimitRejected: Int = 0,
    val residentModels: List<String> = emptyList(),
)
