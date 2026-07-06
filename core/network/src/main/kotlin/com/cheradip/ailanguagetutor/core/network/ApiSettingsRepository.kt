package com.cheradip.ailanguagetutor.core.network

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.common.CLOUD_API_TIMEOUT_MS
import com.cheradip.ailanguagetutor.core.common.HOME_AI_REACHABILITY_TIMEOUT_MS
import com.cheradip.ailanguagetutor.core.common.HOME_AI_RESPONSE_TIMEOUT_MS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.apiSettingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "developer_options",
)

/** Persisted admin developer settings (API URLs, timeouts). Used by core/network and core/ai. */
@Singleton
class ApiSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfig: AppConfig,
) {
    private val keyHomeAiReachabilityMs = longPreferencesKey("home_ai_fallback_timeout_ms")
    private val keyCloudApiTimeoutMs = longPreferencesKey("cloud_api_timeout_ms")
    private val keyApiBaseUrl = stringPreferencesKey("api_base_url_override")

    val homeAiReachabilityTimeoutMs: Flow<Long> = context.apiSettingsDataStore.data.map { prefs ->
        prefs[keyHomeAiReachabilityMs] ?: appConfig.homeAiReachabilityTimeoutMs
    }

    /** @deprecated use [homeAiReachabilityTimeoutMs] */
    val homeAiFallbackTimeoutMs: Flow<Long> = homeAiReachabilityTimeoutMs

    val cloudApiTimeoutMs: Flow<Long> = context.apiSettingsDataStore.data.map { prefs ->
        prefs[keyCloudApiTimeoutMs] ?: appConfig.cloudApiTimeoutMs
    }

    val effectiveApiBaseUrl: Flow<String> = context.apiSettingsDataStore.data.map { prefs ->
        val raw = prefs[keyApiBaseUrl]?.trim()?.takeIf { it.isNotEmpty() } ?: appConfig.apiBaseUrl
        ApiBaseUrlNormalizer.normalize(raw)
    }

    suspend fun getHomeAiReachabilityTimeoutMs(): Long = homeAiReachabilityTimeoutMs.first()

    suspend fun getHomeAiResponseTimeoutMs(): Long = HOME_AI_RESPONSE_TIMEOUT_MS

    /** @deprecated use [getHomeAiReachabilityTimeoutMs] */
    suspend fun getHomeAiFallbackTimeoutMs(): Long = getHomeAiReachabilityTimeoutMs()

    suspend fun getCloudApiTimeoutMs(): Long = cloudApiTimeoutMs.first()

    suspend fun getEffectiveApiBaseUrl(): String = effectiveApiBaseUrl.first()

    suspend fun setHomeAiReachabilityTimeoutMs(ms: Long) {
        context.apiSettingsDataStore.edit {
            it[keyHomeAiReachabilityMs] = ms.coerceAtLeast(0L)
        }
    }

    /** @deprecated use [setHomeAiReachabilityTimeoutMs] */
    suspend fun setHomeAiFallbackTimeoutMs(ms: Long) = setHomeAiReachabilityTimeoutMs(ms)

    suspend fun setCloudApiTimeoutMs(ms: Long) {
        context.apiSettingsDataStore.edit {
            it[keyCloudApiTimeoutMs] = ms.coerceAtLeast(1_000L)
        }
    }

    suspend fun setApiBaseUrlOverride(url: String?) {
        context.apiSettingsDataStore.edit { prefs ->
            val trimmed = url?.trim().orEmpty()
            if (trimmed.isEmpty()) {
                prefs.remove(keyApiBaseUrl)
            } else {
                prefs[keyApiBaseUrl] = ApiBaseUrlNormalizer.normalize(trimmed)
            }
        }
    }

    suspend fun resetToDefaults() {
        context.apiSettingsDataStore.edit { it.clear() }
    }

    /** Remove saved override when it still targets the dead ailt.cheradip.com host. */
    suspend fun clearDeprecatedApiHostOverrideIfNeeded() {
        val raw = context.apiSettingsDataStore.data.first()[keyApiBaseUrl] ?: return
        if (ApiBaseUrlNormalizer.isDeprecatedHost(raw)) {
            setApiBaseUrlOverride(null)
        }
    }

    /** One-time: legacy defaults were answer timeouts or 7s probe; reachability probe is now 3s. */
    suspend fun migrateLegacyHomeAiReachabilityTimeoutIfNeeded() {
        val stored = context.apiSettingsDataStore.data.first()[keyHomeAiReachabilityMs] ?: return
        if (stored == 30_000L || stored == 7_000L) {
            setHomeAiReachabilityTimeoutMs(HOME_AI_REACHABILITY_TIMEOUT_MS)
        }
    }

    private fun normalizeBaseUrl(url: String): String = ApiBaseUrlNormalizer.normalize(url)

    companion object {
        const val DEFAULT_CLOUD_API_TIMEOUT_MS = CLOUD_API_TIMEOUT_MS
        const val DEFAULT_HOME_AI_REACHABILITY_TIMEOUT_MS = HOME_AI_REACHABILITY_TIMEOUT_MS
        const val DEFAULT_HOME_AI_RESPONSE_TIMEOUT_MS = HOME_AI_RESPONSE_TIMEOUT_MS

        @Deprecated("Use DEFAULT_HOME_AI_REACHABILITY_TIMEOUT_MS")
        const val DEFAULT_HOME_AI_TIMEOUT_MS = DEFAULT_HOME_AI_REACHABILITY_TIMEOUT_MS
    }
}
