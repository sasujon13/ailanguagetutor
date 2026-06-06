package com.cheradip.ailanguagetutor.core.network

import com.cheradip.ailanguagetutor.core.common.AppLocaleHolder
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/** Adds X-Language header for API response translation (user-facing AI routes only). */
class AppLocaleInterceptor @Inject constructor() : Interceptor {
    private val skipPathSegments = setOf("admin", "promo", "referral", "billing", "device", "auth", "languages")

    override fun intercept(chain: Interceptor.Chain): Response {
        val path = chain.request().url.pathSegments
        if (path.any { it in skipPathSegments }) {
            return chain.proceed(chain.request())
        }
        val lang = AppLocaleHolder.languageCode
        val request = if (lang.isNotBlank() && !lang.equals("en", ignoreCase = true)) {
            chain.request().newBuilder()
                .header("X-Language", lang)
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
