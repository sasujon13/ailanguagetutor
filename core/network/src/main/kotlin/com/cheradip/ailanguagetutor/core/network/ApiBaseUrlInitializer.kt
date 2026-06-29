package com.cheradip.ailanguagetutor.core.network

import com.cheradip.ailanguagetutor.core.common.AppConfig
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ApiBaseUrlInitializer @Inject constructor(
    private val holder: ApiBaseUrlHolder,
    private val retrofitProvider: AiltRetrofitProvider,
    private val apiSettings: ApiSettingsRepository,
    appConfig: AppConfig,
) {
    init {
        runBlocking {
            holder.update(
                runCatching { apiSettings.getEffectiveApiBaseUrl() }
                    .getOrElse { normalize(appConfig.apiBaseUrl) },
            )
        }
        retrofitProvider.get()
    }

    suspend fun applyFromSettings() {
        retrofitProvider.refreshBaseUrl(apiSettings.getEffectiveApiBaseUrl())
    }

    private fun normalize(url: String): String =
        url.trim().let { if (it.endsWith("/")) it else "$it/" }
}
