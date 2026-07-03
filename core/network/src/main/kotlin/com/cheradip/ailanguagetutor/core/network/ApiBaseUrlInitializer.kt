package com.cheradip.ailanguagetutor.core.network

import com.cheradip.ailanguagetutor.core.common.AppConfig
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiBaseUrlInitializer @Inject constructor(
    private val retrofitProvider: AiltRetrofitProvider,
    private val apiSettings: ApiSettingsRepository,
    private val appConfig: AppConfig,
) {
    /** Call from [android.app.Application.onCreate] so Retrofit is never left on a dead host. */
    fun ensureProductionBaseUrl() {
        runBlocking {
            apiSettings.clearDeprecatedApiHostOverrideIfNeeded()
            val url = runCatching { apiSettings.getEffectiveApiBaseUrl() }
                .getOrElse { ApiBaseUrlNormalizer.normalize(appConfig.apiBaseUrl) }
            retrofitProvider.refreshBaseUrl(url)
        }
    }

    suspend fun applyFromSettings() {
        retrofitProvider.refreshBaseUrl(apiSettings.getEffectiveApiBaseUrl())
    }
}
