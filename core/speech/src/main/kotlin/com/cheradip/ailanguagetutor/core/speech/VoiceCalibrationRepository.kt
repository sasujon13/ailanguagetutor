package com.cheradip.ailanguagetutor.core.speech

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

data class LanguageCalibrationStatus(
    val languageCode: String,
    val wordCompleted: Boolean = false,
    val sentenceCompleted: Boolean = false,
    val paragraphCompleted: Boolean = false,
) {
    val sayAllowed: Boolean get() = paragraphCompleted
}

private val Context.calibrationDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "voice_calibration_prefs",
)

@Singleton
class VoiceCalibrationRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun observe(languageCodes: List<String>): Flow<List<LanguageCalibrationStatus>> =
        context.calibrationDataStore.data.map { prefs ->
            languageCodes.map { code ->
                LanguageCalibrationStatus(
                    languageCode = code.lowercase(),
                    wordCompleted = prefs[key(code, CalibrationTier.WORD)] == true,
                    sentenceCompleted = prefs[key(code, CalibrationTier.SENTENCE)] == true,
                    paragraphCompleted = prefs[key(code, CalibrationTier.PARAGRAPH)] == true,
                )
            }
        }

    suspend fun isSayAllowed(languageCode: String): Boolean {
        val prefs = context.calibrationDataStore.data.map {
            it[key(languageCode, CalibrationTier.PARAGRAPH)] == true
        }
        return prefs.first()
    }

    suspend fun markTierComplete(languageCode: String, tier: CalibrationTier) {
        context.calibrationDataStore.edit {
            it[key(languageCode, tier)] = true
        }
    }

    private fun key(languageCode: String, tier: CalibrationTier) = booleanPreferencesKey(
        "cal_${languageCode.lowercase()}_${tier.name.lowercase()}",
    )
}
