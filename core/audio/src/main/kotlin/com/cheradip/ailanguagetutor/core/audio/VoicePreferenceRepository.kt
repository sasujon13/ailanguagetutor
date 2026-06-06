package com.cheradip.ailanguagetutor.core.audio

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.voiceDataStore: DataStore<Preferences> by preferencesDataStore(name = "voice_prefs")

@Singleton
class VoicePreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyGender = stringPreferencesKey("teen_voice_gender")

    val gender: Flow<TeenVoiceGender> = context.voiceDataStore.data.map { prefs ->
        when (prefs[keyGender]) {
            "MALE" -> TeenVoiceGender.MALE
            else -> TeenVoiceGender.FEMALE
        }
    }

    suspend fun setGender(gender: TeenVoiceGender) {
        context.voiceDataStore.edit { it[keyGender] = gender.name }
    }
}
