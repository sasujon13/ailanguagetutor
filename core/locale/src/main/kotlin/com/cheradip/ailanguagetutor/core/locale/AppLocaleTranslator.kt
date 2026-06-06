package com.cheradip.ailanguagetutor.core.locale

import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class TranslateStringsRequest(
    @Json(name = "target_language") val targetLanguage: String,
    @Json(name = "source_language") val sourceLanguage: String = "en",
    val strings: Map<String, String>,
)

@JsonClass(generateAdapter = true)
data class TranslateStringsResponse(
    val translations: Map<String, String> = emptyMap(),
    val cached: Boolean = false,
    val backend: String? = null,
)

@Singleton
class AppLocaleTranslator @Inject constructor(
    private val appConfig: AppConfig,
    baseClient: OkHttpClient,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val requestAdapter = moshi.adapter(TranslateStringsRequest::class.java)
    private val responseAdapter = moshi.adapter(TranslateStringsResponse::class.java)

    private val client = baseClient.newBuilder()
        .connectTimeout(appConfig.homeAiTimeoutMs, TimeUnit.MILLISECONDS)
        .readTimeout(appConfig.homeAiTimeoutMs, TimeUnit.MILLISECONDS)
        .build()

    suspend fun translateUiStrings(targetLanguage: String): Map<String, String> = withContext(Dispatchers.IO) {
        if (targetLanguage.equals(AppStrings.DEFAULT_LANG, ignoreCase = true)) {
            return@withContext AppStrings.english
        }
        val base = appConfig.homeAiBaseUrl.trimEnd('/') + "/"
        val body = requestAdapter.toJson(
            TranslateStringsRequest(
                targetLanguage = targetLanguage,
                sourceLanguage = AppStrings.DEFAULT_LANG,
                strings = AppStrings.english,
            ),
        ) ?: return@withContext AppStrings.english

        val request = Request.Builder()
            .url("${base}translate-strings")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching AppStrings.english
                val json = response.body?.string() ?: return@runCatching AppStrings.english
                responseAdapter.fromJson(json)?.translations?.takeIf { it.isNotEmpty() }
                    ?: AppStrings.english
            }
        }.getOrDefault(AppStrings.english)
    }
}
