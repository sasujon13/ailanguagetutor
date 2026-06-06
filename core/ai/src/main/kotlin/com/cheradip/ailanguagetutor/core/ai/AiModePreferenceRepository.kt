package com.cheradip.ailanguagetutor.core.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.model.resolveAiEngineMode
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.aiModeDataStore: DataStore<Preferences> by preferencesDataStore(name = "ai_mode_prefs")

data class AiModePreferences(
    val processingIntent: ProcessingIntent = ProcessingIntent.ANSWER,
    val selectedMode: AiEngineMode = AiEngineMode.SMART_TUTOR,
)

@Singleton
class AiModePreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyIntent = stringPreferencesKey("processing_intent")
    private val keyMode = intPreferencesKey("ai_engine_mode")

    val preferences: Flow<AiModePreferences> = context.aiModeDataStore.data.map { prefs ->
        AiModePreferences(
            processingIntent = prefs[keyIntent]?.let {
                runCatching { ProcessingIntent.valueOf(it) }.getOrDefault(ProcessingIntent.ANSWER)
            } ?: ProcessingIntent.ANSWER,
            selectedMode = AiEngineMode.fromId(prefs[keyMode] ?: 1) ?: AiEngineMode.SMART_TUTOR,
        )
    }

    suspend fun current(): AiModePreferences = preferences.first()

    suspend fun save(processingIntent: ProcessingIntent, mode: AiEngineMode) {
        context.aiModeDataStore.edit { prefs ->
            prefs[keyIntent] = processingIntent.name
            prefs[keyMode] = mode.id
        }
    }

    /** Select High Quality when the user upgrades to Plus (they can change it later). */
    suspend fun activateHighQualityForPlus() {
        val prefs = current()
        save(prefs.processingIntent, AiEngineMode.HIGH_ACCURACY)
    }

    /** User-facing selection — High Quality is not selectable on Pro/Free tiers. */
    fun displaySelectedMode(selected: AiEngineMode, tier: SubscriptionTier): AiEngineMode =
        if (tier != SubscriptionTier.PLUS && selected == AiEngineMode.HIGH_ACCURACY) {
            AiEngineMode.SMART_TUTOR
        } else {
            selected
        }

    suspend fun resolvedMode(
        inputSource: InputSource,
        tier: SubscriptionTier,
    ): AiEngineMode {
        val prefs = current()
        return resolveAiEngineMode(prefs.selectedMode, inputSource, tier)
    }
}
