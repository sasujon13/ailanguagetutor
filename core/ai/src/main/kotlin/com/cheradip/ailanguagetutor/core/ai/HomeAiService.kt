package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.device.DeviceFingerprintProvider
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.network.HomeAiApi
import com.cheradip.ailanguagetutor.core.network.HomeAiRequest
import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAiService @Inject constructor(
    private val settings: HomeAiSettingsRepository,
    private val appConfig: AppConfig,
    deviceFingerprint: DeviceFingerprintProvider,
    moshi: Moshi,
    baseClient: OkHttpClient,
) {
    private val deviceId = deviceFingerprint.deviceId()

    private val homeClient = baseClient.newBuilder()
        .connectTimeout(appConfig.homeAiTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(appConfig.homeAiTimeoutMs, TimeUnit.MILLISECONDS)
        .callTimeout(appConfig.homeAiTimeoutMs, TimeUnit.MILLISECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("X-Device-Id", deviceId)
                    .build(),
            )
        }
        .build()

    private val retrofitBuilder = Retrofit.Builder()
        .client(homeClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))

    private suspend fun api(): HomeAiApi {
        val base = settings.getBaseUrl()
        return retrofitBuilder.baseUrl(base).build().create(HomeAiApi::class.java)
    }

    suspend fun isReachable(): Boolean = runCatching {
        api().health().status == "ok"
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
