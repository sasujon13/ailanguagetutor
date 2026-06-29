package com.cheradip.ailanguagetutor.core.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.model.AiBackend
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

    val baseUrl: Flow<String> = context.homeAiDataStore.data.map { prefs ->
        prefs[keyBaseUrl] ?: appConfig.homeAiBaseUrl
    }

    val preferredBackend: Flow<AiBackend> = context.homeAiDataStore.data.map { prefs ->
        prefs[keyBackend]?.let {
            runCatching { AiBackend.valueOf(it) }.getOrNull()
        } ?: AiBackend.CLOUD_POOL
    }

    suspend fun getBaseUrl(): String = baseUrl.first().let { url ->
        if (url.endsWith("/")) url else "$url/"
    }

    suspend fun setBaseUrl(url: String) {
        context.homeAiDataStore.edit { it[keyBaseUrl] = url.trim() }
    }

    suspend fun setBackend(backend: AiBackend) {
        context.homeAiDataStore.edit { it[keyBackend] = backend.name }
    }
}
