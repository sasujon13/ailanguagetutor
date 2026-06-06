package com.cheradip.ailanguagetutor.core.speech

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

sealed class ListeningState {
    data object Idle : ListeningState()
    data object Listening : ListeningState()
    data class Partial(val text: String) : ListeningState()
    data class Final(val text: String) : ListeningState()
    data class Error(val message: String) : ListeningState()
}

@Singleton
class SpeechToTextEngine @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private val _state = MutableStateFlow<ListeningState>(ListeningState.Idle)
    val state: StateFlow<ListeningState> = _state.asStateFlow()

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(languageCode: String) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = ListeningState.Error("Speech recognition is not available on this device.")
                return@post
            }
            releaseRecognizer()
            _state.value = ListeningState.Listening
            val recognizer = try {
                SpeechRecognizer.createSpeechRecognizer(context)?.also {
                    recognizer = it
                    it.setRecognitionListener(createListener())
                }
            } catch (e: Exception) {
                _state.value = ListeningState.Error("Microphone unavailable: ${e.message ?: "unknown error"}")
                null
            }
            recognizer?.startListening(buildIntent(languageCode))
        }
    }

    fun stopListening() {
        mainHandler.post {
            recognizer?.stopListening()
            if (_state.value is ListeningState.Listening || _state.value is ListeningState.Partial) {
                _state.value = ListeningState.Idle
            }
        }
    }

    fun destroy() {
        mainHandler.post { releaseRecognizer() }
    }

    private fun releaseRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun buildIntent(languageCode: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag(languageCode))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
        }

    private fun localeTag(languageCode: String): String =
        when (languageCode.lowercase()) {
            "en" -> "en-US"
            "fr" -> "fr-FR"
            "es" -> "es-ES"
            "de" -> "de-DE"
            "it" -> "it-IT"
            "pt" -> "pt-BR"
            "hi" -> "hi-IN"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            "zh" -> "zh-CN"
            else -> Locale.forLanguageTag(languageCode).toLanguageTag()
        }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val text = partialResults?.bestText().orEmpty()
            if (text.isNotBlank()) _state.value = ListeningState.Partial(text)
        }

        override fun onResults(results: android.os.Bundle?) {
            val text = results?.bestText().orEmpty()
            _state.value = if (text.isBlank()) {
                ListeningState.Error("No speech detected. Try again closer to the microphone.")
            } else {
                ListeningState.Final(text)
            }
        }

        override fun onError(error: Int) {
            _state.value = ListeningState.Error(mapSpeechError(error))
        }
    }

    private fun android.os.Bundle.bestText(): String =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun mapSpeechError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech client error."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
        SpeechRecognizer.ERROR_NETWORK -> "Network required for speech recognition on this device."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "Could not match speech. Please try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy."
        SpeechRecognizer.ERROR_SERVER -> "Speech server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap the mic and speak."
        else -> "Speech recognition failed (code $error)."
    }
}
