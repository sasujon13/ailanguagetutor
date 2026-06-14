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

    private var activeLanguageCode: String = "en"
    private var listenConfig: SpeechListenConfig = SpeechListenConfig()
    private var sessionActive = false
    private var heardSpeechInSession = false
    private var silenceTimeoutRunnable: Runnable? = null

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        languageCode: String,
        config: SpeechListenConfig = SpeechListenConfig(),
    ) {
        mainHandler.post {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = ListeningState.Error("Speech recognition is not available on this device.")
                return@post
            }
            activeLanguageCode = languageCode
            listenConfig = config
            sessionActive = true
            heardSpeechInSession = false
            cancelSilenceTimeout()
            releaseRecognizer()
            _state.value = ListeningState.Listening
            val created = try {
                SpeechRecognizer.createSpeechRecognizer(context)?.also {
                    recognizer = it
                    it.setRecognitionListener(createListener())
                }
            } catch (e: Exception) {
                sessionActive = false
                _state.value = ListeningState.Error("Microphone unavailable: ${e.message ?: "unknown error"}")
                null
            }
            created?.startListening(buildIntent(languageCode, config))
            if (config.continuous) {
                scheduleSilenceTimeout(config.silenceTimeoutMs)
            }
        }
    }

    fun stopListening() {
        mainHandler.post { endSession(emitIdle = true) }
    }

    fun destroy() {
        mainHandler.post {
            sessionActive = false
            cancelSilenceTimeout()
            releaseRecognizer()
            _state.value = ListeningState.Idle
        }
    }

    private fun endSession(emitIdle: Boolean) {
        sessionActive = false
        cancelSilenceTimeout()
        recognizer?.stopListening()
        releaseRecognizer()
        if (emitIdle) {
            _state.value = ListeningState.Idle
        }
    }

    private fun releaseRecognizer() {
        recognizer?.destroy()
        recognizer = null
    }

    private fun scheduleSilenceTimeout(delayMs: Long) {
        if (!listenConfig.continuous || !sessionActive) return
        cancelSilenceTimeout()
        silenceTimeoutRunnable = Runnable {
            if (!sessionActive) return@Runnable
            endSession(emitIdle = true)
        }
        mainHandler.postDelayed(silenceTimeoutRunnable!!, delayMs)
    }

    private fun cancelSilenceTimeout() {
        silenceTimeoutRunnable?.let(mainHandler::removeCallbacks)
        silenceTimeoutRunnable = null
    }

    private fun markSpeechActivity() {
        if (!sessionActive) return
        heardSpeechInSession = true
        if (listenConfig.continuous) {
            cancelSilenceTimeout()
        }
    }

    private fun restartRecognizerOnly() {
        if (!sessionActive) return
        runCatching {
            recognizer?.startListening(buildIntent(activeLanguageCode, listenConfig))
            _state.value = ListeningState.Listening
        }.onFailure {
            endSession(emitIdle = true)
        }
    }

    private fun restartListeningAfterUtterance() {
        if (!sessionActive || !listenConfig.continuous) return
        scheduleSilenceTimeout(listenConfig.silenceTimeoutMs)
        restartRecognizerOnly()
    }

    private fun recoverFromNoSpeechError() {
        if (!sessionActive || !listenConfig.continuous) return
        restartRecognizerOnly()
    }

    private fun buildIntent(languageCode: String, config: SpeechListenConfig): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag(languageCode))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            if (config.continuous) {
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                    config.utteranceSilenceMs,
                )
                putExtra(
                    RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                    config.utteranceSilenceMs,
                )
            }
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
        override fun onBeginningOfSpeech() {
            markSpeechActivity()
        }
        override fun onRmsChanged(rmsdB: Float) {
            if (listenConfig.continuous && sessionActive && rmsdB > 2f) {
                markSpeechActivity()
            }
        }
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit

        override fun onPartialResults(partialResults: android.os.Bundle?) {
            val text = partialResults?.bestText().orEmpty()
            if (text.isNotBlank()) {
                markSpeechActivity()
                _state.value = ListeningState.Partial(text)
            }
        }

        override fun onResults(results: android.os.Bundle?) {
            val text = results?.bestText().orEmpty()
            if (text.isNotBlank()) {
                heardSpeechInSession = true
                _state.value = ListeningState.Final(text)
            }
            if (listenConfig.continuous && sessionActive) {
                restartListeningAfterUtterance()
                return
            }
            if (text.isBlank()) {
                _state.value = ListeningState.Error("No speech detected. Try again closer to the microphone.")
            }
        }

        override fun onError(error: Int) {
            if (listenConfig.continuous && sessionActive &&
                (error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT || error == SpeechRecognizer.ERROR_NO_MATCH)
            ) {
                recoverFromNoSpeechError()
                return
            }
            sessionActive = false
            cancelSilenceTimeout()
            releaseRecognizer()
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
