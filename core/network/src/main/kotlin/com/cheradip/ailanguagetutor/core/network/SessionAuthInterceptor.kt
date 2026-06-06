package com.cheradip.ailanguagetutor.core.network

import com.cheradip.ailanguagetutor.core.common.SessionTokenHolder
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionAuthInterceptor @Inject constructor(
    private val sessionTokenHolder: SessionTokenHolder,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = sessionTokenHolder.token.value
        val request = if (!token.isNullOrBlank()) {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
