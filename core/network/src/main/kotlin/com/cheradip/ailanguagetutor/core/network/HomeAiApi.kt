package com.cheradip.ailanguagetutor.core.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
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

    @Multipart
    @POST("scan-enhance")
    suspend fun scanEnhance(
        @Part image: MultipartBody.Part,
        @Part("level") level: RequestBody,
        @Part("premium") premium: RequestBody,
        @Part("document_class") documentClass: RequestBody,
    ): ResponseBody

    @Multipart
    @POST("scan-analyze")
    suspend fun scanAnalyze(
        @Part image: MultipartBody.Part,
        @Part("premium") premium: RequestBody,
    ): HomeAiScanAnalyzeResponse

    @POST("prefetch-grammar")
    suspend fun prefetchGrammar(@Body body: HomeAiGrammarPrefetchRequest): HomeAiGrammarPrefetchResponse

    @POST("prefetch-ai")
    suspend fun prefetchAi(@Body body: HomeAiPrefetchRequest): HomeAiPrefetchResponse

    @POST("grammar-book")
    suspend fun grammarBook(@Body body: HomeAiGrammarBookRequest): HomeAiGrammarBookResponse

    @POST("grammar-book/enrich-section")
    suspend fun grammarBookEnrichSection(
        @Body body: HomeAiGrammarBookEnrichRequest,
    ): HomeAiGrammarBookEnrichResponse

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
data class HomeAiScanAnalyzeResponse(
    @Json(name = "document_class") val documentClass: String,
    @Json(name = "recommended_mode") val recommendedMode: String,
    @Json(name = "recommended_level") val recommendedLevel: Int,
    @Json(name = "recommended_label") val recommendedLabel: String,
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarPrefetchItem(
    @Json(name = "context_text") val contextText: String,
    @Json(name = "focus_word") val focusWord: String? = null,
    val offset: Int = 0,
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarPrefetchRequest(
    @Json(name = "grammar_depth") val grammarDepth: Int,
    @Json(name = "language_code") val languageCode: String,
    @Json(name = "target_languages") val targetLanguages: List<String> = emptyList(),
    @Json(name = "ai_engine_mode") val aiEngineMode: Int = 1,
    @Json(name = "input_source") val inputSource: String = "typed",
    @Json(name = "subscription_tier") val subscriptionTier: String = "pro",
    val items: List<HomeAiGrammarPrefetchItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarPrefetchResponse(
    val warmed: Int = 0,
    val cached: Int = 0,
    val total: Int = 0,
    @Json(name = "language_code") val languageCode: String = "en",
    @Json(name = "target_languages") val targetLanguages: List<String> = emptyList(),
    val results: List<HomeAiGrammarResultItem> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarResultItem(
    @Json(name = "focus_word") val focusWord: String? = null,
    val explanation: String = "",
)

@JsonClass(generateAdapter = true)
data class HomeAiPrefetchRequest(
    @Json(name = "grammar_depth") val grammarDepth: Int,
    @Json(name = "language_code") val languageCode: String,
    @Json(name = "target_languages") val targetLanguages: List<String> = emptyList(),
    @Json(name = "ai_engine_mode") val aiEngineMode: Int = 1,
    @Json(name = "input_source") val inputSource: String = "typed",
    @Json(name = "subscription_tier") val subscriptionTier: String = "pro",
    @Json(name = "grammar_items") val grammarItems: List<HomeAiGrammarPrefetchItem> = emptyList(),
    @Json(name = "explain_text") val explainText: String? = null,
    @Json(name = "translate_text") val translateText: String? = null,
)

@JsonClass(generateAdapter = true)
data class HomeAiPrefetchResponse(
    val grammar: HomeAiGrammarPrefetchResponse = HomeAiGrammarPrefetchResponse(),
    @Json(name = "explain_warmed") val explainWarmed: Boolean = false,
    @Json(name = "explain_cached") val explainCached: Boolean = false,
    @Json(name = "translate_warmed") val translateWarmed: Boolean = false,
    @Json(name = "translate_cached") val translateCached: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarBookRequest(
    @Json(name = "language_code") val languageCode: String,
    @Json(name = "language_name") val languageName: String = "",
    @Json(name = "ai_engine_mode") val aiEngineMode: Int = 1,
    @Json(name = "subscription_tier") val subscriptionTier: String = "pro",
    @Json(name = "english_pivot") val englishPivot: Boolean = true,
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarBookSectionDto(
    val heading: String = "",
    val body: String = "",
    val examples: List<String> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarBookChapterDto(
    val number: Int = 1,
    val title: String = "",
    val summary: String = "",
    val sections: List<HomeAiGrammarBookSectionDto> = emptyList(),
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarBookResponse(
    val title: String = "",
    @Json(name = "language_code") val languageCode: String = "en",
    @Json(name = "language_name") val languageName: String = "",
    val chapters: List<HomeAiGrammarBookChapterDto> = emptyList(),
    val cached: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarBookEnrichRequest(
    @Json(name = "language_code") val languageCode: String,
    @Json(name = "language_name") val languageName: String = "",
    @Json(name = "chapter_number") val chapterNumber: Int,
    @Json(name = "chapter_title") val chapterTitle: String = "",
    @Json(name = "section_heading") val sectionHeading: String = "",
    @Json(name = "section_body") val sectionBody: String = "",
    val examples: List<String> = emptyList(),
    @Json(name = "ai_engine_mode") val aiEngineMode: Int = 1,
    @Json(name = "subscription_tier") val subscriptionTier: String = "pro",
)

@JsonClass(generateAdapter = true)
data class HomeAiGrammarBookEnrichResponse(
    @Json(name = "expanded_body") val expandedBody: String = "",
    @Json(name = "extra_examples") val extraExamples: List<String> = emptyList(),
    @Json(name = "learner_tip") val learnerTip: String = "",
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
    val inference: HomeAiInferenceStats = HomeAiInferenceStats(),
)

@JsonClass(generateAdapter = true)
data class HomeAiInferenceStats(
    @Json(name = "last_model_used") val lastModelUsed: String? = null,
    @Json(name = "last_translation_backend") val lastTranslationBackend: String? = null,
    @Json(name = "fallback_count") val fallbackCount: Int = 0,
    @Json(name = "active_llm") val activeLlm: String? = null,
    @Json(name = "models_used") val modelsUsed: Map<String, Int> = emptyMap(),
)

@JsonClass(generateAdapter = true)
data class HomeAiRateLimitStats(
    val allowed: Int = 0,
    val rejected: Int = 0,
    @Json(name = "active_buckets") val activeBuckets: Int = 0,
    @Json(name = "pro_per_hour") val proPerHour: Int = 60,
    @Json(name = "plus_per_hour") val plusPerHour: Int = 120,
)
