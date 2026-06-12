package com.cheradip.ailanguagetutor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.AiPrefetchCoordinator
import com.cheradip.ailanguagetutor.core.ai.GrammarPreferenceRepository
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextPipeline
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageConfig
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageRepository
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivitySyncRepository
import com.cheradip.ailanguagetutor.core.device.NetworkConnectivityMonitor
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextResult
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.LanguageFlagMarker
import com.cheradip.ailanguagetutor.core.pack.LanguageCatalogRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import com.cheradip.ailanguagetutor.core.speech.CalibrationTier
import com.cheradip.ailanguagetutor.core.speech.LanguageCalibrationStatus
import com.cheradip.ailanguagetutor.core.speech.ListeningState
import com.cheradip.ailanguagetutor.core.speech.SpeechToTextEngine
import com.cheradip.ailanguagetutor.core.speech.VoiceCalibrationRepository
import com.cheradip.ailanguagetutor.core.translation.OfflinePracticeProcessor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SpeechCaptureMode { NONE, VOICE_INPUT, CALIBRATION }

data class CalibrationUiState(
    val languageCode: String = "en",
    val tier: CalibrationTier = CalibrationTier.WORD,
    val itemIndex: Int = 0,
    val listening: Boolean = false,
    val partialText: String = "",
    val lastScore: Float? = null,
    val message: String? = null,
)

data class PracticeHubUiState(
    val typedInput: String = "",
    val aiOutput: String? = null,
    val aiLoading: Boolean = false,
    val aiIntent: ProcessingIntent? = null,
    val outputOffline: Boolean = false,
    val isListening: Boolean = false,
    val partialSpeech: String = "",
    val speechError: String? = null,
    val speechErrorLinksCalibration: Boolean = false,
    val activeLanguageCodes: List<String> = emptyList(),
    val calibrationStatuses: List<LanguageCalibrationStatus> = emptyList(),
    val speechMode: SpeechCaptureMode = SpeechCaptureMode.NONE,
    val lastActivityId: Long? = null,
    val resultSaved: Boolean = false,
    val saveMessage: String? = null,
    val processError: String? = null,
)

@HiltViewModel
class PracticeHubViewModel @Inject constructor(
    private val pronunciationEngine: PronunciationEngine,
    private val speechEngine: SpeechToTextEngine,
    private val voiceCalibrationRepository: VoiceCalibrationRepository,
    private val languagePackRepository: LanguagePackRepository,
    private val catalogRepository: LanguageCatalogRepository,
    private val learningActivityRepository: LearningActivityRepository,
    private val learningActivitySyncRepository: LearningActivitySyncRepository,
    private val unifiedTextPipeline: UnifiedTextPipeline,
    private val aiPrefetchCoordinator: AiPrefetchCoordinator,
    private val practiceLanguageRepository: PracticeLanguageRepository,
    private val offlinePracticeProcessor: OfflinePracticeProcessor,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    grammarPreferenceRepository: GrammarPreferenceRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PracticeHubUiState())
    val uiState: StateFlow<PracticeHubUiState> = _uiState.asStateFlow()

    private val grammarDepth = grammarPreferenceRepository.depth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarDepth.WORD)

    val practiceLanguages = practiceLanguageRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeLanguageConfig())

    private val _languageOptions = MutableStateFlow<List<PracticeLanguageOption>>(emptyList())
    val languageOptions: StateFlow<List<PracticeLanguageOption>> = _languageOptions.asStateFlow()

    private var dismissCalibrationErrorOnReturn = false
    private var offlineTypingJob: Job? = null
    private var voiceAutoAiJob: Job? = null
    private var activeProcessJob: Job? = null
    private var lastTypedInput: String = ""

    init {
        pronunciationEngine.init()
        viewModelScope.launch {
            speechEngine.state.collect(::handleListeningState)
        }
        viewModelScope.launch {
            languagePackRepository.observeActive()
                .map { packs -> packs.map { it.languageCode.lowercase() }.distinct() }
                .distinctUntilChanged()
                .collectLatest { codes ->
                    val catalog = catalogRepository.getAll()
                    _languageOptions.value = codes.mapNotNull { code ->
                        toLanguageOption(code, catalog)
                    }
                    if (codes.isNotEmpty()) {
                        practiceLanguageRepository.ensureDefaults(codes)
                    }
                    _uiState.update { state ->
                        state.copy(activeLanguageCodes = codes)
                    }
                    if (codes.isNotEmpty()) {
                        voiceCalibrationRepository.observe(codes).collect { statuses ->
                            _uiState.update { it.copy(calibrationStatuses = statuses) }
                        }
                    } else {
                        _uiState.update { it.copy(calibrationStatuses = emptyList()) }
                    }
                }
        }
    }

    override fun onCleared() {
        speechEngine.destroy()
        super.onCleared()
    }

    fun setInputLanguage(code: String) {
        viewModelScope.launch {
            val output = practiceLanguages.value.outputLanguage
            practiceLanguageRepository.save(code.lowercase(), output)
        }
    }

    fun setOutputLanguage(code: String) {
        viewModelScope.launch {
            val input = practiceLanguages.value.inputLanguage
            practiceLanguageRepository.save(input, code.lowercase())
        }
    }

    private fun toLanguageOption(code: String, catalog: List<LanguageCatalogEntry>): PracticeLanguageOption? {
        val entry = catalog.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: return null
        return PracticeLanguageOption(
            code = entry.code.lowercase(),
            label = LanguageFlagMarker.compact(entry),
            flagEmoji = LanguageFlagMarker.emoji(entry),
        )
    }

    fun speak(text: String) {
        val lang = practiceLanguages.value.outputLanguage
        pronunciationEngine.speak(text, lang)
    }

    fun clearSpeechError() {
        _uiState.update { it.copy(speechError = null, speechErrorLinksCalibration = false) }
    }

    fun openVoiceCalibrationSettings() {
        if (_uiState.value.speechErrorLinksCalibration) {
            dismissCalibrationErrorOnReturn = true
        }
    }

    fun onPracticeResumed() {
        if (!dismissCalibrationErrorOnReturn) return
        dismissCalibrationErrorOnReturn = false
        dismissCalibrationSpeechError()
    }

    private fun dismissCalibrationSpeechError() {
        _uiState.update {
            if (it.speechErrorLinksCalibration) {
                it.copy(speechError = null, speechErrorLinksCalibration = false)
            } else {
                it
            }
        }
    }

    fun setSpeechError(message: String) {
        _uiState.update { it.copy(speechError = message, speechErrorLinksCalibration = false) }
    }

    fun updateTypedInput(text: String) {
        val langs = practiceLanguages.value
        voiceAutoAiJob?.cancel()
        _uiState.update {
            it.copy(
                typedInput = text,
                resultSaved = false,
                saveMessage = null,
                processError = null,
            )
        }
        aiPrefetchCoordinator.schedulePracticeWarm(
            text = text,
            languageCode = langs.inputLanguage,
            targetLang = langs.outputLanguage,
            grammarDepth = grammarDepth.value,
        )
        if (text.isBlank()) {
            offlineTypingJob?.cancel()
            lastTypedInput = ""
            return
        }
        if (shouldRunOfflineWhileTyping(previous = lastTypedInput, current = text)) {
            runIncrementalOffline(text)
        }
        lastTypedInput = text
    }

    /** For scan/OCR text injected into Practice — processes immediately (AI first, offline fallback). */
    fun applyExternalInput(text: String, inputSource: InputSource = InputSource.OCR_SCAN) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _uiState.update {
            it.copy(
                typedInput = trimmed,
                resultSaved = false,
                saveMessage = null,
                processError = null,
            )
        }
        voiceAutoAiJob?.cancel()
        lastTypedInput = trimmed
        runProcess { processAuto(inputSource) }
    }

    fun processOfflineInput() {
        voiceAutoAiJob?.cancel()
        runProcess { processOffline(recordActivity = true) }
    }

    fun processTypedInputWithSavedLanguages() {
        voiceAutoAiJob?.cancel()
        runProcess { processWithAi(inputSource = InputSource.TYPED) }
    }

    private fun shouldRunOfflineWhileTyping(previous: String, current: String): Boolean {
        if (current.isBlank()) return false
        return current.length > previous.length && current.last() == ' '
    }

    private fun runIncrementalOffline(text: String) {
        offlineTypingJob?.cancel()
        offlineTypingJob = viewModelScope.launch {
            val langs = practiceLanguages.value
            val trimmed = text.trim()
            if (trimmed.isBlank()) return@launch
            val output = runCatching {
                offlinePracticeProcessor.process(
                    text = trimmed,
                    sourceLang = langs.inputLanguage,
                    targetLang = langs.outputLanguage,
                )
            }.getOrElse { "Offline process failed: ${it.message ?: "unknown error"}" }
            if (_uiState.value.typedInput.trim() != trimmed) return@launch
            _uiState.update {
                it.copy(
                    aiOutput = output,
                    aiIntent = ProcessingIntent.TRANSLATION,
                    outputOffline = true,
                    aiLoading = false,
                    processError = null,
                )
            }
        }
    }

    private fun scheduleVoiceAutoAi() {
        voiceAutoAiJob?.cancel()
        voiceAutoAiJob = viewModelScope.launch {
            delay(VOICE_AUTO_AI_DELAY_MS)
            val text = _uiState.value.typedInput.trim()
            if (text.isBlank()) return@launch
            processWithAi(inputSource = InputSource.VOICE)
        }
    }

    private fun runProcess(block: suspend () -> Unit) {
        offlineTypingJob?.cancel()
        voiceAutoAiJob?.cancel()
        activeProcessJob?.cancel()
        activeProcessJob = viewModelScope.launch {
            block()
        }
    }

    private suspend fun processAuto(inputSource: InputSource = InputSource.TYPED) {
        val text = _uiState.value.typedInput.trim()
        if (text.isBlank() || _uiState.value.isListening) return
        val langs = practiceLanguages.value
        _uiState.update { it.copy(aiLoading = true, processError = null) }

        if (networkConnectivityMonitor.isOnline()) {
            val aiResult = tryAiProcess(
                text = text,
                sourceLang = langs.inputLanguage,
                targetLang = langs.outputLanguage,
                inputSource = inputSource,
            )
            if (aiResult != null) {
                publishProcessResult(
                    text = text,
                    output = aiResult.output,
                    intent = aiResult.intent,
                    offline = false,
                    activityType = "practice_typed",
                    title = "Practice: typed",
                    sourceLang = langs.inputLanguage,
                    targetLang = langs.outputLanguage,
                )
                return
            }
        }

        processOffline(recordActivity = false, skipLoadingStart = true)
    }

    private suspend fun processWithAi(inputSource: InputSource = InputSource.TYPED) {
        val text = _uiState.value.typedInput.trim()
        if (text.isBlank()) return
        val langs = practiceLanguages.value
        _uiState.update { it.copy(aiLoading = true, processError = null) }

        if (!networkConnectivityMonitor.isOnline()) {
            _uiState.update {
                it.copy(
                    aiLoading = false,
                    processError = AI_INTERNET_REQUIRED,
                )
            }
            return
        }

        val aiResult = tryAiProcess(
            text = text,
            sourceLang = langs.inputLanguage,
            targetLang = langs.outputLanguage,
            inputSource = inputSource,
        )
        if (aiResult != null) {
            publishProcessResult(
                text = text,
                output = aiResult.output,
                intent = aiResult.intent,
                offline = false,
                activityType = "practice_typed",
                title = "Practice: typed",
                sourceLang = langs.inputLanguage,
                targetLang = langs.outputLanguage,
            )
        } else {
            _uiState.update {
                it.copy(
                    aiLoading = false,
                    processError = AI_PROCESS_FAILED,
                )
            }
        }
    }

    private suspend fun processOffline(recordActivity: Boolean, skipLoadingStart: Boolean = false) {
        val text = _uiState.value.typedInput.trim()
        if (text.isBlank()) return
        val langs = practiceLanguages.value
        if (!skipLoadingStart) {
            _uiState.update { it.copy(aiLoading = true, processError = null) }
        }
        val output = runCatching {
            offlinePracticeProcessor.process(
                text = text,
                sourceLang = langs.inputLanguage,
                targetLang = langs.outputLanguage,
            )
        }.getOrElse { "Offline process failed: ${it.message ?: "unknown error"}" }
        if (recordActivity) {
            publishProcessResult(
                text = text,
                output = output,
                intent = ProcessingIntent.TRANSLATION,
                offline = true,
                activityType = "practice_offline",
                title = "Practice: offline process",
                sourceLang = langs.inputLanguage,
                targetLang = langs.outputLanguage,
            )
        } else {
            _uiState.update {
                it.copy(
                    aiLoading = false,
                    aiOutput = output,
                    aiIntent = ProcessingIntent.TRANSLATION,
                    outputOffline = true,
                    processError = null,
                )
            }
        }
    }

    private suspend fun tryAiProcess(
        text: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource,
    ): UnifiedTextResult? {
        val result = runCatching {
            unifiedTextPipeline.process(
                text = text,
                sourceLang = sourceLang,
                targetLang = targetLang,
                inputSource = inputSource,
            )
        }.getOrNull() ?: return null
        return result.takeUnless { isAiFallbackOutput(it.output) }
    }

    private suspend fun publishProcessResult(
        text: String,
        output: String,
        intent: ProcessingIntent?,
        offline: Boolean,
        activityType: String,
        title: String,
        sourceLang: String,
        targetLang: String,
    ) {
        val activityId = learningActivityRepository.record(
            title = title,
            activityType = activityType,
            languageCode = sourceLang,
            summary = output.take(120),
            inputText = text,
            outputText = output,
            outputLanguageCode = targetLang,
        )
        _uiState.update {
            it.copy(
                aiLoading = false,
                aiOutput = output,
                aiIntent = intent,
                outputOffline = offline,
                lastActivityId = activityId,
                resultSaved = false,
                saveMessage = null,
                processError = null,
            )
        }
        learningActivitySyncRepository.syncIfLoggedIn()
    }

    private fun isAiFallbackOutput(output: String): Boolean {
        val trimmed = output.trim()
        return trimmed.startsWith("Offline summary:") ||
            trimmed.startsWith("[NLLB stub") ||
            trimmed.startsWith("[NLLB openvino stub")
    }

    fun startVoiceInput() {
        voiceAutoAiJob?.cancel()
        val inputLang = practiceLanguages.value.inputLanguage.lowercase()
        val allowed = _uiState.value.calibrationStatuses
            .firstOrNull { it.languageCode == inputLang }
            ?.sayAllowed == true
        if (!allowed) {
            _uiState.update {
                it.copy(
                    speechError = micCalibrationRequiredMessage(inputLang),
                    speechErrorLinksCalibration = true,
                )
            }
            return
        }
        if (!speechEngine.isAvailable()) {
            _uiState.update {
                it.copy(
                    speechError = "Speech recognition is not available on this device.",
                    speechErrorLinksCalibration = false,
                )
            }
            return
        }
        _uiState.update {
            it.copy(
                speechMode = SpeechCaptureMode.VOICE_INPUT,
                isListening = true,
                speechError = null,
                partialSpeech = "",
            )
        }
        speechEngine.startListening(inputLang)
    }

    fun stopVoiceInput() {
        speechEngine.stopListening()
        _uiState.update {
            it.copy(
                isListening = false,
                speechMode = SpeechCaptureMode.NONE,
            )
        }
    }

    fun saveCurrentResult() {
        val state = _uiState.value
        val langs = practiceLanguages.value
        if (state.typedInput.isBlank() && state.aiOutput.isNullOrBlank()) return
        viewModelScope.launch {
            val activityId = state.lastActivityId ?: learningActivityRepository.record(
                title = "Practice: saved",
                activityType = if (state.outputOffline) "practice_offline" else "practice_typed",
                languageCode = langs.inputLanguage,
                summary = state.aiOutput?.take(120),
                inputText = state.typedInput.takeIf { it.isNotBlank() },
                outputText = state.aiOutput,
                outputLanguageCode = langs.outputLanguage,
                isSaved = true,
            )
            if (state.lastActivityId != null) {
                learningActivityRepository.markSaved(state.lastActivityId)
            }
            learningActivitySyncRepository.syncIfLoggedIn()
            _uiState.update {
                it.copy(
                    lastActivityId = activityId,
                    resultSaved = true,
                    saveMessage = "Saved to Learning",
                )
            }
        }
    }

    fun clearSaveMessage() {
        _uiState.update { it.copy(saveMessage = null) }
    }

    fun restoreActivity(activityId: Long) {
        if (activityId <= 0L) return
        viewModelScope.launch {
            val activity = learningActivityRepository.getById(activityId) ?: return@launch
            val outputLang = activity.outputLanguageCode?.lowercase()
                ?: practiceLanguages.value.outputLanguage
            practiceLanguageRepository.save(activity.languageCode.lowercase(), outputLang)
            _uiState.update {
                it.copy(
                    typedInput = activity.inputText.orEmpty(),
                    aiOutput = activity.outputText ?: activity.summary,
                    aiLoading = false,
                    outputOffline = activity.activityType == "practice_offline",
                    aiIntent = null,
                    speechError = null,
                    speechErrorLinksCalibration = false,
                    partialSpeech = "",
                    isListening = false,
                    speechMode = SpeechCaptureMode.NONE,
                    lastActivityId = activityId,
                    resultSaved = activity.isSaved,
                    saveMessage = null,
                    processError = null,
                )
            }
        }
    }

    private fun handleListeningState(state: ListeningState) {
        when (state) {
            ListeningState.Idle -> {
                _uiState.update { it.copy(isListening = false) }
            }
            ListeningState.Listening -> {
                _uiState.update {
                    it.copy(isListening = it.speechMode == SpeechCaptureMode.VOICE_INPUT)
                }
            }
            is ListeningState.Partial -> {
                if (_uiState.value.speechMode == SpeechCaptureMode.VOICE_INPUT) {
                    _uiState.update {
                        it.copy(partialSpeech = state.text, isListening = true)
                    }
                }
            }
            is ListeningState.Final -> {
                if (_uiState.value.speechMode == SpeechCaptureMode.VOICE_INPUT) {
                    _uiState.update {
                        it.copy(
                            typedInput = state.text,
                            partialSpeech = "",
                            isListening = false,
                            speechMode = SpeechCaptureMode.NONE,
                            processError = null,
                        )
                    }
                    val langs = practiceLanguages.value
                    aiPrefetchCoordinator.schedulePracticeWarm(
                        text = state.text,
                        languageCode = langs.inputLanguage,
                        targetLang = langs.outputLanguage,
                        grammarDepth = grammarDepth.value,
                    )
                    lastTypedInput = state.text
                    scheduleVoiceAutoAi()
                }
            }
            is ListeningState.Error -> {
                _uiState.update {
                    it.copy(
                        speechError = state.message,
                        speechErrorLinksCalibration = false,
                        isListening = false,
                        speechMode = SpeechCaptureMode.NONE,
                    )
                }
            }
        }
    }

    companion object {
        const val AI_INTERNET_REQUIRED = "You must be connected to Internet to process with AI."
        const val AI_PROCESS_FAILED = "Could not process with AI. Try again."
        private const val VOICE_AUTO_AI_DELAY_MS = 7_000L

        fun micCalibrationRequiredMessage(languageCode: String): String =
            "Mic not calibrated for ${languageCode.uppercase()}. Click for Calibration."
    }
}
