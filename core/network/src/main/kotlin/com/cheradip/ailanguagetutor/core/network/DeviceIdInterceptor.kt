package com.cheradip.ailanguagetutor.core.network

import com.cheradip.ailanguagetutor.core.common.DeviceIdHolder
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceIdInterceptor @Inject constructor(
    private val deviceIdHolder: DeviceIdHolder,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val deviceId = deviceIdHolder.deviceId
        val request = if (!deviceId.isNullOrBlank()) {
            chain.request().newBuilder()
                .header("X-Device-Id", deviceId)
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
