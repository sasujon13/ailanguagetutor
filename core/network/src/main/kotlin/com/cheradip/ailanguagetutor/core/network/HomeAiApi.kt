package com.cheradip.ailanguagetutor.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

interface HomeAiApi {
    @GET("health")
    suspend fun health(): HomeAiHealthResponse

    @GET("ai/modes")
    suspend fun modes(@Query("tier") tier: String): HomeAiModesResponse

    @POST("ask")
    suspend fun ask(@Body body: HomeAiRequest): HomeAiAskResponse

    @POST("translate")
    suspend fun translate(@Body body: HomeAiRequest): HomeAiTranslateResponse

    @POST("clean-ocr")
    suspend fun cleanOcr(@Body body: HomeAiRequest): HomeAiCleanOcrResponse

    @GET("admin/status")
    suspend fun adminStatus(): HomeAiAdminStatusResponse
}

@JsonClass(generateAdapter = true)
data class HomeAiRequest(
    @Json(name = "processing_intent") val processingIntent: String,
    @Json(name = "ai_engine_mode") val aiEngineMode: Int,
    @Json(name = "input_source") val inputSource: String,
    @Json(name = "subscription_tier") val subscriptionTier: String,
    val text: String,
    @Json(name = "language_code") val languageCode: String,
    @Json(name = "target_languages") val targetLanguages: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HomeAiAskResponse(
    val explanation: String,
    val simple: String? = null,
    val mode: Int = 1,
    val cached: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class HomeAiTranslateResponse(
    val translations: Map<String, String>,
    val mode: Int = 2,
    val cached: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class HomeAiCleanOcrResponse(
    @Json(name = "cleaned_text") val cleanedText: String,
    val mode: Int = 4,
    val cached: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class HomeAiHealthResponse(
    val status: String,
    @Json(name = "gpu_available") val gpuAvailable: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class HomeAiModesResponse(
    val tier: String,
    val modes: List<HomeAiModeDto> = emptyList(),
    val note: String? = null,
)

@JsonClass(generateAdapter = true)
data class HomeAiModeDto(
    val id: Int,
    val key: String,
    val label: String,
    val emoji: String,
)

@JsonClass(generateAdapter = true)
data class HomeAiAdminStatusResponse(
    @Json(name = "model_loaded") val modelLoaded: String? = null,
    @Json(name = "resident_models") val residentModels: List<String> = emptyList(),
    @Json(name = "queue_depth") val queueDepth: Int = 0,
    @Json(name = "cache_stats") val cacheStats: HomeAiCacheStats = HomeAiCacheStats(),
    @Json(name = "cache_hit_rate_l1") val cacheHitRateL1: Double? = null,
    @Json(name = "cache_hit_rate_l2") val cacheHitRateL2: Double? = null,
    @Json(name = "cache_hit_rate_l3") val cacheHitRateL3: Double? = null,
    @Json(name = "gpu_available") val gpuAvailable: Boolean = false,
    @Json(name = "inference_backend") val inferenceBackend: String = "stub",
    val router: HomeAiRouterStats = HomeAiRouterStats(),
    @Json(name = "rate_limit") val rateLimit: HomeAiRateLimitStats = HomeAiRateLimitStats(),
)

@JsonClass(generateAdapter = true)
data class HomeAiCacheStats(
    @Json(name = "l1_hits") val l1Hits: Int = 0,
    @Json(name = "l2_hits") val l2Hits: Int = 0,
    @Json(name = "l3_hits") val l3Hits: Int = 0,
    val misses: Int = 0,
    @Json(name = "hit_rate_pct") val hitRatePct: Double = 0.0,
)

@JsonClass(generateAdapter = true)
data class HomeAiRouterStats(
    @Json(name = "routes_total") val routesTotal: Int = 0,
    @Json(name = "routes_by_intent") val routesByIntent: Map<String, Int> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class HomeAiRateLimitStats(
    val allowed: Int = 0,
    val rejected: Int = 0,
    @Json(name = "active_buckets") val activeBuckets: Int = 0,
    @Json(name = "pro_per_hour") val proPerHour: Int = 60,
    @Json(name = "plus_per_hour") val plusPerHour: Int = 120,
)
