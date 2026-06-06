package com.cheradip.ailanguagetutor.core.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
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

    fun init(onReady: (Boolean) -> Unit = {}) {
        if (tts != null) {
            onReady(_ready.value)
            return
        }
        tts = TextToSpeech(context) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) applyTeenVoice()
            _ready.value = ok
            onReady(ok)
        }
    }

    fun setGender(g: TeenVoiceGender) {
        gender = g
        if (_ready.value) applyTeenVoice()
    }

    fun speak(text: String, languageCode: String = "en") {
        val engine = tts ?: return
        engine.language = localeFor(languageCode)
        applyTeenVoice()
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "word-${text.hashCode()}")
    }

    private fun applyTeenVoice() {
        val engine = tts ?: return
        val voices = engine.voices.orEmpty()
        val teen = voices.firstOrNull { voice ->
            val name = voice.name.lowercase()
            val matchesGender = when (gender) {
                TeenVoiceGender.MALE -> "male" in name || name.contains("-m-")
                TeenVoiceGender.FEMALE -> "female" in name || name.contains("-f-")
            }
            matchesGender && (voice.quality >= Voice.QUALITY_NORMAL)
        } ?: voices.firstOrNull()
        teen?.let { engine.voice = it }
        engine.setSpeechRate(0.95f)
    }

    private fun localeFor(code: String): Locale =
        when (code.lowercase()) {
            "en" -> Locale.US
            "fr" -> Locale.FRENCH
            "es" -> Locale("es")
            "de" -> Locale.GERMAN
            else -> Locale.forLanguageTag(code)
        }
}
