package com.cheradip.ailanguagetutor.core.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.model.AiBackend
import com.cheradip.ailanguagetutor.core.network.HomeAiBaseUrlNormalizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.homeAiDataStore: DataStore<Preferences> by preferencesDataStore(name = "home_ai_prefs")

@Singleton
class HomeAiSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appConfig: AppConfig,
) {
    private val keyBaseUrl = stringPreferencesKey("home_ai_base_url")
    private val keyBackend = stringPreferencesKey("ai_backend")
    private val keyDefaultsMigrated = stringPreferencesKey("home_ai_defaults_migrated")

    val baseUrl: Flow<String> = context.homeAiDataStore.data.map { prefs ->
        val raw = prefs[keyBaseUrl] ?: appConfig.homeAiBaseUrl
        HomeAiBaseUrlNormalizer.normalize(raw)
    }

    val preferredBackend: Flow<AiBackend> = context.homeAiDataStore.data.map { prefs ->
        prefs[keyBackend]?.let {
            runCatching { AiBackend.valueOf(it) }.getOrNull()
        } ?: AiBackend.LOCAL_HOME
    }

    suspend fun getBaseUrl(): String = baseUrl.first()

    suspend fun setBaseUrl(url: String) {
        context.homeAiDataStore.edit {
            it[keyBaseUrl] = HomeAiBaseUrlNormalizer.normalize(url).trimEnd('/')
        }
    }

    suspend fun setBackend(backend: AiBackend) {
        context.homeAiDataStore.edit { it[keyBackend] = backend.name }
    }

    suspend fun ensureProductionSettings() {
        val prefs = context.homeAiDataStore.data.first()
        val storedUrl = prefs[keyBaseUrl]
        if (storedUrl != null && HomeAiBaseUrlNormalizer.isDeprecatedHost(storedUrl)) {
            context.homeAiDataStore.edit { it.remove(keyBaseUrl) }
        }
        if (prefs[keyDefaultsMigrated] != MIGRATION_VERSION) {
            context.homeAiDataStore.edit { edit ->
                val backend = prefs[keyBackend]
                if (backend == null || backend == AiBackend.CLOUD_POOL.name) {
                    edit[keyBackend] = AiBackend.LOCAL_HOME.name
                }
                edit[keyDefaultsMigrated] = MIGRATION_VERSION
            }
        }
    }

    private companion object {
        const val MIGRATION_VERSION = "reachability_v1"
    }
}
