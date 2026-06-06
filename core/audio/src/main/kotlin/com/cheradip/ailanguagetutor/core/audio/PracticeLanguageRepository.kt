package com.cheradip.ailanguagetutor.core.audio

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class PracticeLanguageConfig(
    val inputLanguage: String = "en",
    val outputLanguage: String = "en",
)

private val Context.practiceLanguageDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "practice_language_prefs",
)

@Singleton
class PracticeLanguageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyInput = stringPreferencesKey("input_language")
    private val keyOutput = stringPreferencesKey("output_language")

    val config: Flow<PracticeLanguageConfig> = context.practiceLanguageDataStore.data.map { prefs ->
        PracticeLanguageConfig(
            inputLanguage = prefs[keyInput] ?: "en",
            outputLanguage = prefs[keyOutput] ?: "en",
        )
    }

    suspend fun save(inputLanguage: String, outputLanguage: String) {
        context.practiceLanguageDataStore.edit {
            it[keyInput] = inputLanguage.lowercase()
            it[keyOutput] = outputLanguage.lowercase()
        }
    }

    suspend fun ensureDefaults(activeLanguageCodes: List<String>) {
        if (activeLanguageCodes.isEmpty()) return
        val existing = config.first()
        val active = activeLanguageCodes.map { it.lowercase() }
        val input = existing.inputLanguage.takeIf { it in active } ?: active.first()
        val output = existing.outputLanguage.takeIf { it in active } ?: active.drop(1).firstOrNull() ?: input
        if (input.equals(existing.inputLanguage, ignoreCase = true) &&
            output.equals(existing.outputLanguage, ignoreCase = true)
        ) {
            return
        }
        save(input, output)
    }
}
