package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.device.DeviceFingerprintProvider
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.GrammarPrefetchTarget
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.network.HomeAiApi
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookEnrichRequest
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookEnrichResponse
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookRequest
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookResponse
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarPrefetchItem
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarPrefetchRequest
import com.cheradip.ailanguagetutor.core.network.HomeAiPrefetchRequest
import com.cheradip.ailanguagetutor.core.network.HomeAiPrefetchResponse
import com.cheradip.ailanguagetutor.core.network.HomeAiRequest
import com.cheradip.ailanguagetutor.core.network.HomeAiScanAnalyzeResponse
import com.squareup.moshi.Moshi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.OkHttpClient
import java.io.File
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAiService @Inject constructor(
    private val settings: HomeAiSettingsRepository,
    private val developerOptions: DeveloperOptionsRepository,
    deviceFingerprint: DeviceFingerprintProvider,
    private val moshi: Moshi,
    private val baseClient: OkHttpClient,
) {
    private val deviceId = deviceFingerprint.deviceId()

    private val retrofitBuilder = Retrofit.Builder()
        .addConverterFactory(MoshiConverterFactory.create(moshi))

    private fun clientWithTimeouts(timeoutMs: Long): OkHttpClient =
        baseClient.newBuilder()
            .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .callTimeout(timeoutMs, TimeUnit.MILLISECONDS)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("X-Device-Id", deviceId)
                        .build(),
                )
            }
            .build()

    private suspend fun healthApi(): HomeAiApi {
        val reachMs = developerOptions.getHomeAiReachabilityTimeoutMs()
        val base = settings.getBaseUrl()
        return retrofitBuilder
            .client(clientWithTimeouts(reachMs))
            .baseUrl(base)
            .build()
            .create(HomeAiApi::class.java)
    }

    private suspend fun api(): HomeAiApi {
        val responseMs = developerOptions.getHomeAiResponseTimeoutMs()
        val base = settings.getBaseUrl()
        return retrofitBuilder
            .client(clientWithTimeouts(responseMs))
            .baseUrl(base)
            .build()
            .create(HomeAiApi::class.java)
    }

    /**
     * Probes GET /health within the reachability budget, then runs [block] with up to 120s for inference.
     */
    suspend fun <T> withHomeAi(block: suspend () -> T): T {
        val reachMs = developerOptions.getHomeAiReachabilityTimeoutMs()
        val responseMs = developerOptions.getHomeAiResponseTimeoutMs()
        val reachable = runCatching {
            withTimeout(reachMs) {
                healthApi().health().status == "ok"
            }
        }.getOrDefault(false)
        if (!reachable) {
            throw HomeAiUnreachableException()
        }
        return try {
            withTimeout(responseMs) { block() }
        } catch (e: TimeoutCancellationException) {
            throw HomeAiResponseTimeoutException(responseMs, e)
        }
    }

    suspend fun isReachable(): Boolean = runCatching {
        val reachMs = developerOptions.getHomeAiReachabilityTimeoutMs()
        withTimeout(reachMs) {
            healthApi().health().status == "ok"
        }
    }.getOrDefault(false)

    suspend fun fetchAdminStatus(): HomeAiAdminDashboard? =
        runCatching { api().adminStatus() }.getOrNull()?.let { resp ->
            HomeAiAdminDashboard(
                inferenceBackend = resp.inferenceBackend,
                gpuAvailable = resp.gpuAvailable,
                modelLoaded = resp.modelLoaded,
                queueDepth = resp.queueDepth,
                cacheHitRatePct = resp.cacheStats.hitRatePct,
                cacheHitRateL1 = resp.cacheHitRateL1 ?: 0.0,
                cacheHitRateL2 = resp.cacheHitRateL2 ?: 0.0,
                cacheHitRateL3 = resp.cacheHitRateL3 ?: 0.0,
                routesTotal = resp.router.routesTotal,
                routesByIntent = resp.router.routesByIntent,
                modelsUsed = resp.router.inference.modelsUsed,
                lastModelUsed = resp.router.inference.lastModelUsed,
                lastTranslationBackend = resp.router.inference.lastTranslationBackend,
                inferenceFallbackCount = resp.router.inference.fallbackCount,
                rateLimitAllowed = resp.rateLimit.allowed,
                rateLimitRejected = resp.rateLimit.rejected,
                residentModels = resp.residentModels,
            )
        }

    suspend fun ask(
        text: String,
        mode: AiEngineMode,
        intent: ProcessingIntent,
        inputSource: InputSource,
        tier: SubscriptionTier,
        languageCode: String,
        targetLang: String,
    ): String {
        val body = buildRequest(text, mode, intent, inputSource, tier, languageCode, listOf(targetLang))
        return api().ask(body).explanation
    }

    suspend fun translateParagraph(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        mode: AiEngineMode,
        tier: SubscriptionTier,
        inputSource: InputSource = InputSource.TYPED,
    ): String {
        val body = buildRequest(
            paragraph,
            mode,
            ProcessingIntent.TRANSLATION,
            inputSource,
            tier,
            sourceLang,
            listOf(targetLang),
        )
        val response = api().translate(body)
        return response.translations[targetLang]
            ?: response.translations.values.firstOrNull()
            ?: paragraph
    }

    suspend fun explainParagraph(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        mode: AiEngineMode,
        tier: SubscriptionTier,
        inputSource: InputSource = InputSource.TYPED,
    ): String = ask(
        text = paragraph,
        mode = mode,
        intent = ProcessingIntent.ANSWER,
        inputSource = inputSource,
        tier = tier,
        languageCode = sourceLang,
        targetLang = targetLang,
    )

    suspend fun prefetchGrammar(
        targets: List<GrammarPrefetchTarget>,
        sourceLang: String,
        targetLangs: List<String>,
        depth: GrammarDepth,
        mode: AiEngineMode,
        tier: SubscriptionTier,
        inputSource: InputSource = InputSource.TYPED,
    ) {
        if (targets.isEmpty()) return
        val body = HomeAiGrammarPrefetchRequest(
            grammarDepth = depth.id,
            languageCode = sourceLang,
            targetLanguages = targetLangs,
            aiEngineMode = mode.id,
            inputSource = when (inputSource) {
                InputSource.OCR_SCAN -> "ocr"
                InputSource.VOICE -> "voice"
                InputSource.TYPED -> "typed"
            },
            subscriptionTier = tier.name.lowercase(),
            items = targets.map {
                HomeAiGrammarPrefetchItem(
                    contextText = it.contextText,
                    focusWord = it.focusWord,
                    offset = it.offset,
                )
            },
        )
        api().prefetchGrammar(body)
    }

    suspend fun prefetchAi(
        targets: List<GrammarPrefetchTarget>,
        sourceLang: String,
        targetLangs: List<String>,
        depth: GrammarDepth,
        mode: AiEngineMode,
        tier: SubscriptionTier,
        inputSource: InputSource = InputSource.TYPED,
        explainChunk: String? = null,
        translateChunk: String? = null,
    ): HomeAiPrefetchResponse? {
        if (targets.isEmpty() && explainChunk.isNullOrBlank() && translateChunk.isNullOrBlank()) return null
        val body = HomeAiPrefetchRequest(
            grammarDepth = depth.id,
            languageCode = sourceLang,
            targetLanguages = targetLangs,
            aiEngineMode = mode.id,
            inputSource = when (inputSource) {
                InputSource.OCR_SCAN -> "ocr"
                InputSource.VOICE -> "voice"
                InputSource.TYPED -> "typed"
            },
            subscriptionTier = tier.name.lowercase(),
            grammarItems = targets.map {
                HomeAiGrammarPrefetchItem(
                    contextText = it.contextText,
                    focusWord = it.focusWord,
                    offset = it.offset,
                )
            },
            explainText = explainChunk?.take(480),
            translateText = if (explainChunk.isNullOrBlank()) translateChunk?.take(480) else null,
        )
        return runCatching { api().prefetchAi(body) }.getOrNull()
    }

    suspend fun fetchGrammarBook(
        languageCode: String,
        languageName: String,
        mode: AiEngineMode,
        tier: SubscriptionTier,
    ): HomeAiGrammarBookResponse {
        val body = HomeAiGrammarBookRequest(
            languageCode = languageCode,
            languageName = languageName,
            aiEngineMode = mode.id,
            subscriptionTier = tier.name.lowercase(),
        )
        return api().grammarBook(body)
    }

    suspend fun enrichGrammarBookSection(
        languageCode: String,
        languageName: String,
        chapterNumber: Int,
        chapterTitle: String,
        sectionHeading: String,
        sectionBody: String,
        examples: List<String>,
        mode: AiEngineMode,
        tier: SubscriptionTier,
    ): HomeAiGrammarBookEnrichResponse {
        val body = HomeAiGrammarBookEnrichRequest(
            languageCode = languageCode,
            languageName = languageName,
            chapterNumber = chapterNumber,
            chapterTitle = chapterTitle,
            sectionHeading = sectionHeading,
            sectionBody = sectionBody,
            examples = examples,
            aiEngineMode = mode.id,
            subscriptionTier = tier.name.lowercase(),
        )
        return api().grammarBookEnrichSection(body)
    }

    suspend fun cleanOcr(
        text: String,
        languageCode: String,
        tier: SubscriptionTier,
    ): String {
        val body = buildRequest(
            text = text,
            mode = AiEngineMode.LIGHTWEIGHT,
            intent = ProcessingIntent.ANSWER,
            inputSource = InputSource.OCR_SCAN,
            tier = tier,
            languageCode = languageCode,
            targets = emptyList(),
        )
        return api().cleanOcr(body).cleanedText
    }

    suspend fun scanEnhanceToFile(
        imagePath: String,
        level: Int,
        outFile: File,
        premium: Boolean = true,
        documentClass: String? = null,
    ): String = withHomeAi {
        val imageFile = File(imagePath)
        val imagePart = MultipartBody.Part.createFormData(
            "image",
            imageFile.name,
            imageFile.asRequestBody("image/jpeg".toMediaType()),
        )
        val levelPart = level.coerceIn(0, 7).toString().toRequestBody("text/plain".toMediaType())
        val premiumPart = premium.toString().toRequestBody("text/plain".toMediaType())
        val docClassPart = (documentClass ?: "").toRequestBody("text/plain".toMediaType())
        api().scanEnhance(imagePart, levelPart, premiumPart, docClassPart).use { body ->
            outFile.outputStream().use { out -> body.byteStream().copyTo(out) }
        }
        outFile.absolutePath
    }

    suspend fun scanAnalyze(imagePath: String, premium: Boolean): HomeAiScanAnalyzeResponse = withHomeAi {
        val imageFile = File(imagePath)
        val imagePart = MultipartBody.Part.createFormData(
            "image",
            imageFile.name,
            imageFile.asRequestBody("image/jpeg".toMediaType()),
        )
        val premiumPart = premium.toString().toRequestBody("text/plain".toMediaType())
        api().scanAnalyze(imagePart, premiumPart)
    }

    private fun buildRequest(
        text: String,
        mode: AiEngineMode,
        intent: ProcessingIntent,
        inputSource: InputSource,
        tier: SubscriptionTier,
        languageCode: String,
        targets: List<String>,
    ) = HomeAiRequest(
        processingIntent = intent.name.lowercase(),
        aiEngineMode = mode.id,
        inputSource = when (inputSource) {
            InputSource.OCR_SCAN -> "ocr"
            InputSource.VOICE -> "voice"
            InputSource.TYPED -> "typed"
        },
        subscriptionTier = tier.name.lowercase(),
        text = text,
        languageCode = languageCode,
        targetLanguages = targets,
    )
}
