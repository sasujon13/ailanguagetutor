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
    private var pendingStartRunnable: Runnable? = null
    private var startRetryCount = 0

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun startListening(
        languageCode: String,
        config: SpeechListenConfig = SpeechListenConfig(),
    ) {
        mainHandler.post {
            cancelPendingStart()
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                _state.value = ListeningState.Error("Speech recognition is not available on this device.")
                return@post
            }
            activeLanguageCode = languageCode
            listenConfig = config
            sessionActive = true
            heardSpeechInSession = false
            startRetryCount = 0
            cancelSilenceTimeout()
            beginListeningSession()
        }
    }

    fun stopListening() {
        mainHandler.post { endSession(emitIdle = true) }
    }

    fun destroy() {
        mainHandler.post {
            sessionActive = false
            cancelPendingStart()
            cancelSilenceTimeout()
            releaseRecognizer()
            _state.value = ListeningState.Idle
        }
    }

    private fun beginListeningSession() {
        if (!sessionActive) return
        cancelRecognizerSession()
        _state.value = ListeningState.Listening
        ensureRecognizer()
        val rec = recognizer
        if (rec == null) {
            sessionActive = false
            _state.value = ListeningState.Error("Microphone unavailable.")
            return
        }
        runCatching {
            rec.startListening(buildIntent(activeLanguageCode, listenConfig))
        }.onFailure {
            retryStartOrFail(it.message ?: "Could not start microphone.")
        }
    }

    private fun ensureRecognizer() {
        if (recognizer != null) return
        recognizer = runCatching {
            SpeechRecognizer.createSpeechRecognizer(context)?.also {
                it.setRecognitionListener(createListener())
            }
        }.getOrNull()
    }

    private fun cancelRecognizerSession() {
        runCatching { recognizer?.cancel() }
    }

    private fun endSession(emitIdle: Boolean) {
        sessionActive = false
        cancelPendingStart()
        cancelSilenceTimeout()
        cancelRecognizerSession()
        if (emitIdle) {
            _state.value = ListeningState.Idle
        }
    }

    private fun releaseRecognizer() {
        cancelRecognizerSession()
        recognizer?.destroy()
        recognizer = null
    }

    private fun cancelPendingStart() {
        pendingStartRunnable?.let(mainHandler::removeCallbacks)
        pendingStartRunnable = null
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
            retryStartOrFail(it.message ?: "Could not restart microphone.")
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

    private fun retryStartOrFail(detail: String) {
        if (!sessionActive) return
        if (startRetryCount < MAX_START_RETRIES) {
            startRetryCount++
            cancelPendingStart()
            releaseRecognizer()
            pendingStartRunnable = Runnable {
                pendingStartRunnable = null
                if (sessionActive) beginListeningSession()
            }
            mainHandler.postDelayed(pendingStartRunnable!!, RETRY_DELAY_MS)
            return
        }
        sessionActive = false
        cancelSilenceTimeout()
        releaseRecognizer()
        _state.value = ListeningState.Error("Microphone unavailable. Tap again to retry.")
    }

    private fun handleRecoverableError(error: Int): Boolean {
        if (!sessionActive) return false
        return when (error) {
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED,
            SpeechRecognizer.ERROR_CLIENT,
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            -> {
                if (startRetryCount < MAX_START_RETRIES) {
                    startRetryCount++
                    cancelSilenceTimeout()
                    releaseRecognizer()
                    pendingStartRunnable = Runnable {
                        pendingStartRunnable = null
                        if (sessionActive) beginListeningSession()
                    }
                    mainHandler.postDelayed(pendingStartRunnable!!, RETRY_DELAY_MS)
                    true
                } else {
                    false
                }
            }
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            SpeechRecognizer.ERROR_NO_MATCH,
            -> {
                if (listenConfig.continuous) {
                    recoverFromNoSpeechError()
                    true
                } else if (!heardSpeechInSession && startRetryCount < MAX_START_RETRIES) {
                    startRetryCount++
                    pendingStartRunnable = Runnable {
                        pendingStartRunnable = null
                        if (sessionActive) restartRecognizerOnly()
                    }
                    mainHandler.postDelayed(pendingStartRunnable!!, RETRY_DELAY_MS)
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun buildIntent(languageCode: String, config: SpeechListenConfig): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag(languageCode))
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
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
            "bn" -> "bn-IN"
            "ja" -> "ja-JP"
            "ko" -> "ko-KR"
            "zh" -> "zh-CN"
            else -> Locale.forLanguageTag(languageCode).toLanguageTag()
        }

    private fun createListener(): RecognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: android.os.Bundle?) {
            if (listenConfig.continuous && sessionActive) {
                scheduleSilenceTimeout(listenConfig.silenceTimeoutMs)
            }
        }

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
                startRetryCount = 0
                _state.value = ListeningState.Final(text)
            }
            if (listenConfig.continuous && sessionActive) {
                restartListeningAfterUtterance()
                return
            }
            if (text.isBlank()) {
                _state.value = ListeningState.Error("No speech detected. Try again closer to the microphone.")
            } else {
                endSession(emitIdle = true)
            }
        }

        override fun onError(error: Int) {
            if (handleRecoverableError(error)) return
            sessionActive = false
            cancelPendingStart()
            cancelSilenceTimeout()
            cancelRecognizerSession()
            _state.value = ListeningState.Error(mapSpeechError(error))
        }
    }

    private fun android.os.Bundle.bestText(): String =
        getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()

    private fun mapSpeechError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> "Microphone audio error."
        SpeechRecognizer.ERROR_CLIENT -> "Speech client error. Tap the mic and try again."
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission required."
        SpeechRecognizer.ERROR_NETWORK -> "Network required for speech recognition on this device."
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Speech recognition timed out."
        SpeechRecognizer.ERROR_NO_MATCH -> "Could not match speech. Please try again."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech recognizer is busy. Tap the mic again."
        SpeechRecognizer.ERROR_SERVER -> "Speech server error."
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech heard. Tap the mic and speak."
        11 -> "Speech service disconnected. Tap the mic again."
        12 -> "Speech recognition is not supported for this language on your device."
        13 -> "Speech language pack not downloaded. Connect to the internet or install voice data in Settings."
        else -> "Speech recognition failed (code $error)."
    }

    companion object {
        private const val MAX_START_RETRIES = 2
        private const val RETRY_DELAY_MS = 250L
    }
}
