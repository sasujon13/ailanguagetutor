package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.model.AiBackend
import com.cheradip.ailanguagetutor.core.network.ApiSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Admin Developer Options — AI routing + delegates network settings to [ApiSettingsRepository]. */
@Singleton
class DeveloperOptionsRepository @Inject constructor(
    private val apiSettings: ApiSettingsRepository,
    private val homeAiSettings: HomeAiSettingsRepository,
) {
    val homeAiFallbackTimeoutMs: Flow<Long> = apiSettings.homeAiFallbackTimeoutMs
    val cloudApiTimeoutMs: Flow<Long> = apiSettings.cloudApiTimeoutMs
    val effectiveApiBaseUrl: Flow<String> = apiSettings.effectiveApiBaseUrl

    suspend fun getHomeAiFallbackTimeoutMs(): Long = apiSettings.getHomeAiFallbackTimeoutMs()

    suspend fun getCloudApiTimeoutMs(): Long = apiSettings.getCloudApiTimeoutMs()

    suspend fun getEffectiveApiBaseUrl(): String = apiSettings.getEffectiveApiBaseUrl()

    suspend fun shouldTryHomeAi(): Boolean {
        if (homeAiSettings.preferredBackend.first() != AiBackend.LOCAL_HOME) return false
        return getHomeAiFallbackTimeoutMs() > 0L
    }

    suspend fun setHomeAiFallbackTimeoutMs(ms: Long) = apiSettings.setHomeAiFallbackTimeoutMs(ms)

    suspend fun setCloudApiTimeoutMs(ms: Long) = apiSettings.setCloudApiTimeoutMs(ms)

    suspend fun setApiBaseUrlOverride(url: String?) = apiSettings.setApiBaseUrlOverride(url)

    suspend fun resetToDefaults() = apiSettings.resetToDefaults()
}
