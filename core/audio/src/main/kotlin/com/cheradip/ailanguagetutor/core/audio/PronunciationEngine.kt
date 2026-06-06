package com.cheradip.ailanguagetutor.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class TeenVoiceGender { MALE, FEMALE }

@Singleton
class PronunciationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private var gender = TeenVoiceGender.FEMALE
    private var speechRate = 0.95f
    private var lastLanguageCode = "en"

    fun init(onReady: (Boolean) -> Unit = {}) {
        if (tts != null) {
            onReady(_ready.value)
            return
        }
        tts = TextToSpeech(context) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) applyVoice(lastLanguageCode)
            _ready.value = ok
            onReady(ok)
        }
    }

    fun setGender(g: TeenVoiceGender) {
        gender = g
        if (_ready.value) applyVoice(lastLanguageCode)
    }

    fun speak(text: String, languageCode: String = "en") {
        val engine = tts ?: return
        lastLanguageCode = languageCode.lowercase()
        engine.language = TeenVoiceResolver.localeFor(lastLanguageCode)
        applyVoice(lastLanguageCode)
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts-${lastLanguageCode}-${text.hashCode()}")
    }

    fun preview(languageCode: String = lastLanguageCode) {
        speak(TeenVoiceResolver.previewPhrase(languageCode), languageCode)
    }

    private fun applyVoice(languageCode: String) {
        val engine = tts ?: return
        val resolved = TeenVoiceResolver.resolve(
            voices = engine.voices.orEmpty(),
            languageCode = languageCode,
            gender = gender,
        )
        resolved.voice?.let { engine.voice = it }
        engine.setPitch(resolved.pitch)
        engine.setSpeechRate(speechRate)
    }
}
