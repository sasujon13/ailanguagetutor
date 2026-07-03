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
import com.cheradip.ailanguagetutor.core.common.HOME_AI_TIMEOUT_MS
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
    private val keyHomeAiFallbackMs = longPreferencesKey("home_ai_fallback_timeout_ms")
    private val keyCloudApiTimeoutMs = longPreferencesKey("cloud_api_timeout_ms")
    private val keyApiBaseUrl = stringPreferencesKey("api_base_url_override")

    val homeAiFallbackTimeoutMs: Flow<Long> = context.apiSettingsDataStore.data.map { prefs ->
        prefs[keyHomeAiFallbackMs] ?: appConfig.homeAiTimeoutMs
    }

    val cloudApiTimeoutMs: Flow<Long> = context.apiSettingsDataStore.data.map { prefs ->
        prefs[keyCloudApiTimeoutMs] ?: appConfig.cloudApiTimeoutMs
    }

    val effectiveApiBaseUrl: Flow<String> = context.apiSettingsDataStore.data.map { prefs ->
        val raw = prefs[keyApiBaseUrl]?.trim()?.takeIf { it.isNotEmpty() } ?: appConfig.apiBaseUrl
        ApiBaseUrlNormalizer.normalize(raw)
    }

    suspend fun getHomeAiFallbackTimeoutMs(): Long = homeAiFallbackTimeoutMs.first()

    suspend fun getCloudApiTimeoutMs(): Long = cloudApiTimeoutMs.first()

    suspend fun getEffectiveApiBaseUrl(): String = effectiveApiBaseUrl.first()

    suspend fun setHomeAiFallbackTimeoutMs(ms: Long) {
        context.apiSettingsDataStore.edit {
            it[keyHomeAiFallbackMs] = ms.coerceAtLeast(0L)
        }
    }

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

    private fun normalizeBaseUrl(url: String): String = ApiBaseUrlNormalizer.normalize(url)

    companion object {
        const val DEFAULT_CLOUD_API_TIMEOUT_MS = CLOUD_API_TIMEOUT_MS
        const val DEFAULT_HOME_AI_TIMEOUT_MS = HOME_AI_TIMEOUT_MS
    }
}
