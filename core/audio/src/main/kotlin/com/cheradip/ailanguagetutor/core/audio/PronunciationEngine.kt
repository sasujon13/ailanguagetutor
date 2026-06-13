package com.cheradip.ailanguagetutor.core.audio

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class TeenVoiceGender { MALE, FEMALE }

enum class TtsPlaybackState { IDLE, PLAYING, PAUSED }

@Singleton
class PronunciationEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private var tts: TextToSpeech? = null
    private val _ready = MutableStateFlow(false)
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    private val _playbackState = MutableStateFlow(TtsPlaybackState.IDLE)
    val playbackState: StateFlow<TtsPlaybackState> = _playbackState.asStateFlow()

    private var gender = TeenVoiceGender.FEMALE
    private var speechRate = 0.95f
    private var lastLanguageCode = "en"
    private var currentText: String? = null
    private var currentLanguageCode: String = "en"
    private var pendingChunks: MutableList<String> = mutableListOf()
    private var chunkIndex = 0
    private var pausedByUser = false

    fun init(onReady: (Boolean) -> Unit = {}) {
        if (tts != null) {
            onReady(_ready.value)
            return
        }
        tts = TextToSpeech(context) { status ->
            val ok = status == TextToSpeech.SUCCESS
            if (ok) {
                applyVoice(lastLanguageCode)
                attachProgressListener()
            }
            _ready.value = ok
            onReady(ok)
        }
    }

    fun setGender(g: TeenVoiceGender) {
        gender = g
        if (_ready.value) applyVoice(lastLanguageCode)
    }

    /** Sound icon — always restart from the beginning. */
    fun speakFromStart(text: String, languageCode: String = "en") {
        stopInternal(resetPaused = true)
        currentText = text
        currentLanguageCode = languageCode.lowercase()
        pendingChunks = splitForLocale(text, currentLanguageCode).toMutableList()
        chunkIndex = 0
        pausedByUser = false
        speakNextChunk()
    }

    /** Legacy alias used across the app. */
    fun speak(text: String, languageCode: String = "en") = speakFromStart(text, languageCode)

    /** Playback button — start, pause, or resume the current passage. */
    fun togglePlayback(text: String, languageCode: String = "en") {
        when (_playbackState.value) {
            TtsPlaybackState.PLAYING -> {
                pausedByUser = true
                tts?.stop()
                _playbackState.value = TtsPlaybackState.PAUSED
            }
            TtsPlaybackState.PAUSED -> {
                if (currentText == text && currentLanguageCode == languageCode.lowercase()) {
                    pausedByUser = false
                    speakNextChunk()
                } else {
                    speakFromStart(text, languageCode)
                }
            }
            TtsPlaybackState.IDLE -> speakFromStart(text, languageCode)
        }
    }

    fun stop() = stopInternal(resetPaused = true)

    fun preview(languageCode: String = lastLanguageCode) {
        speakFromStart(TeenVoiceResolver.previewPhrase(languageCode), languageCode)
    }

    private fun stopInternal(resetPaused: Boolean) {
        pausedByUser = !resetPaused && _playbackState.value == TtsPlaybackState.PAUSED
        tts?.stop()
        pendingChunks.clear()
        chunkIndex = 0
        if (resetPaused) {
            currentText = null
            pausedByUser = false
            _playbackState.value = TtsPlaybackState.IDLE
        }
    }

    private fun speakNextChunk() {
        val engine = tts ?: return
        if (pausedByUser) return
        if (chunkIndex >= pendingChunks.size) {
            _playbackState.value = TtsPlaybackState.IDLE
            chunkIndex = 0
            return
        }
        val chunk = pendingChunks[chunkIndex]
        val locale = localeForChunk(chunk, currentLanguageCode)
        lastLanguageCode = currentLanguageCode
        engine.language = locale
        applyVoice(currentLanguageCode)
        val utteranceId = "tts-chunk-$chunkIndex-${chunk.hashCode()}"
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        @Suppress("DEPRECATION")
        engine.speak(chunk, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
        _playbackState.value = TtsPlaybackState.PLAYING
    }

    private fun attachProgressListener() {
        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onDone(utteranceId: String?) {
                if (pausedByUser) return
                chunkIndex++
                if (chunkIndex < pendingChunks.size) {
                    speakNextChunk()
                } else {
                    _playbackState.value = TtsPlaybackState.IDLE
                    chunkIndex = 0
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                _playbackState.value = TtsPlaybackState.IDLE
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                _playbackState.value = TtsPlaybackState.IDLE
            }
        })
    }

    private fun splitForLocale(text: String, languageCode: String): List<String> {
        if (!languageCode.startsWith("en")) return listOf(text)
        val parts = text.split(Regex("(?<=\\D)(?=\\d)|(?<=\\d)(?=\\D)"))
        return parts.filter { it.isNotBlank() }
    }

    private fun localeForChunk(chunk: String, languageCode: String): Locale {
        val isDigitChunk = chunk.all { it.isDigit() || it in ".,/-+ " }
        return when {
            languageCode.startsWith("en") && isDigitChunk -> Locale.US
            else -> TeenVoiceResolver.localeFor(languageCode)
        }
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
