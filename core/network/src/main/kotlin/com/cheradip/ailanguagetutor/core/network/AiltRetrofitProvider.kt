package com.cheradip.ailanguagetutor.core.network

import com.squareup.moshi.Moshi
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiltRetrofitProvider @Inject constructor(
    private val baseUrlHolder: ApiBaseUrlHolder,
    private val moshi: Moshi,
    private val client: OkHttpClient,
) {
    @Volatile
    private var cachedRetrofit: Retrofit? = null

    @Volatile
    private var cachedBaseUrl: String? = null

    fun get(): Retrofit {
        val base = ApiBaseUrlNormalizer.normalize(
            baseUrlHolder.baseUrl.ifBlank { ApiBaseUrlNormalizer.PRODUCTION },
        )
        val existing = cachedRetrofit
        if (existing != null && cachedBaseUrl == base) return existing
        return synchronized(this) {
            if (cachedBaseUrl == base && cachedRetrofit != null) {
                return@synchronized cachedRetrofit!!
            }
            Retrofit.Builder()
                .baseUrl(base)
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .also {
                    cachedRetrofit = it
                    cachedBaseUrl = base
                }
        }
    }

    fun refreshBaseUrl(url: String) {
        baseUrlHolder.update(url)
        synchronized(this) {
            cachedRetrofit = null
            cachedBaseUrl = null
        }
    }
}
