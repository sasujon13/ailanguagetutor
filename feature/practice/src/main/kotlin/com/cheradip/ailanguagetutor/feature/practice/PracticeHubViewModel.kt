package com.cheradip.ailanguagetutor.feature.practice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.AiModePreferenceRepository
import com.cheradip.ailanguagetutor.core.ai.AiPrefetchCoordinator
import com.cheradip.ailanguagetutor.core.ai.GrammarPreferenceRepository
import com.cheradip.ailanguagetutor.core.ai.GrammarExplainer
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextPipeline
import com.cheradip.ailanguagetutor.core.database.repository.SavedWordRepository
import com.cheradip.ailanguagetutor.core.audio.TtsPlaybackState
import com.cheradip.ailanguagetutor.core.pack.DictionaryLookupHelper.placeholderDefinition
import com.cheradip.ailanguagetutor.core.model.WordSheetState
import com.cheradip.ailanguagetutor.core.model.WordSpan
import com.cheradip.ailanguagetutor.core.ocr.WordMapBuilder
import com.cheradip.ailanguagetutor.core.pack.DictionaryRepository
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageConfig
import com.cheradip.ailanguagetutor.core.audio.PracticeLanguageRepository
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivitySyncRepository
import com.cheradip.ailanguagetutor.core.device.NetworkConnectivityMonitor
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.PracticeLanguageRules
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextResult
import com.cheradip.ailanguagetutor.core.model.GuestAiLimitReachedException
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.LanguageFlagMarker
import com.cheradip.ailanguagetutor.core.pack.LanguageCatalogRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import com.cheradip.ailanguagetutor.core.speech.CalibrationTier
import com.cheradip.ailanguagetutor.core.speech.LanguageCalibrationStatus
import com.cheradip.ailanguagetutor.core.speech.ListeningState
import com.cheradip.ailanguagetutor.core.speech.SpeechListenConfig
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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

enum class PracticeGrammarTarget { INPUT, OUTPUT }

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
    val inputWords: List<WordSpan> = emptyList(),
    val outputWords: List<WordSpan> = emptyList(),
    val wordSheet: WordSheetState? = null,
    val lastInputSource: InputSource = InputSource.TYPED,
    val grammarStudyMessage: String? = null,
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
    private val aiModePrefs: AiModePreferenceRepository,
    private val offlinePracticeProcessor: OfflinePracticeProcessor,
    private val networkConnectivityMonitor: NetworkConnectivityMonitor,
    private val grammarPreferenceRepository: GrammarPreferenceRepository,
    private val grammarExplainer: GrammarExplainer,
    private val dictionaryRepository: DictionaryRepository,
    private val wordMapBuilder: WordMapBuilder,
    private val savedWordRepository: SavedWordRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PracticeHubUiState())
    val uiState: StateFlow<PracticeHubUiState> = _uiState.asStateFlow()

    val grammarDepth = grammarPreferenceRepository.depth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarDepth.WORD)

    val practiceLanguages = practiceLanguageRepository.config
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PracticeLanguageConfig())

    val processingIntent = aiModePrefs.preferences
        .map { it.processingIntent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProcessingIntent.ANSWER)

    val playbackState = pronunciationEngine.playbackState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsPlaybackState.IDLE)

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
        viewModelScope.launch {
            processingIntent.collectLatest { intent ->
                if (intent != ProcessingIntent.TRANSLATION) return@collectLatest
                val langs = practiceLanguages.value
                val active = _languageOptions.value.map { it.code }
                if (active.isEmpty()) return@collectLatest
                val output = PracticeLanguageRules.reconcileOutput(
                    intent = intent,
                    activeCodes = active,
                    input = langs.inputLanguage,
                    output = langs.outputLanguage,
                )
                if (!output.equals(langs.outputLanguage, ignoreCase = true)) {
                    practiceLanguageRepository.save(langs.inputLanguage, output)
                }
            }
        }
    }

    override fun onCleared() {
        pronunciationEngine.stop()
        speechEngine.destroy()
        super.onCleared()
    }

    fun setInputLanguage(code: String) {
        viewModelScope.launch {
            val input = code.lowercase()
            val intent = aiModePrefs.current().processingIntent
            val active = _languageOptions.value.map { it.code }
            val output = PracticeLanguageRules.reconcileOutput(
                intent = intent,
                activeCodes = active,
                input = input,
                output = practiceLanguages.value.outputLanguage,
            )
            practiceLanguageRepository.save(input, output)
        }
    }

    fun setOutputLanguage(code: String) {
        viewModelScope.launch {
            val intent = aiModePrefs.current().processingIntent
            val input = practiceLanguages.value.inputLanguage
            val output = if (intent == ProcessingIntent.TRANSLATION &&
                !PracticeLanguageRules.isValidTranslationPair(input, code)
            ) {
                PracticeLanguageRules.resolveOutputForTranslationInput(
                    _languageOptions.value.map { it.code },
                    input,
                    code,
                )
            } else {
                code.lowercase()
            }
            practiceLanguageRepository.save(input, output)
        }
    }

    fun outputLanguageOptions(): List<PracticeLanguageOption> {
        val intent = processingIntent.value
        val input = practiceLanguages.value.inputLanguage
        val options = _languageOptions.value
        return if (intent == ProcessingIntent.TRANSLATION) {
            val allowed = PracticeLanguageRules.translationOutputCodes(options.map { it.code }, input)
            options.filter { opt -> allowed.any { it.equals(opt.code, ignoreCase = true) } }
        } else {
            options
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
        pronunciationEngine.speakFromStart(text, lang)
    }

    fun togglePlayback(text: String) {
        val lang = practiceLanguages.value.outputLanguage
        pronunciationEngine.togglePlayback(text, lang)
    }

    fun stopPlayback() {
        pronunciationEngine.stop()
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

    /** Cancels the post-voice 7s auto-AI timer when the user edits the field or starts voice again. */
    fun cancelVoiceAutoAiTimer() {
        voiceAutoAiJob?.cancel()
        voiceAutoAiJob = null
    }

    fun updateTypedInput(text: String) {
        val langs = practiceLanguages.value
        cancelVoiceAutoAiTimer()
        if (_uiState.value.isListening) {
            stopVoiceInput()
        }
        _uiState.update {
            it.copy(
                typedInput = text,
                resultSaved = false,
                saveMessage = null,
                processError = null,
                inputWords = wordMapBuilder.buildFromPlainText(text),
                wordSheet = null,
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

    fun clearTypedInput() {
        cancelVoiceAutoAiTimer()
        offlineTypingJob?.cancel()
        lastTypedInput = ""
        if (_uiState.value.isListening || _uiState.value.speechMode == SpeechCaptureMode.VOICE_INPUT) {
            speechEngine.stopListening()
        }
        _uiState.update {
            it.copy(
                typedInput = "",
                partialSpeech = "",
                isListening = false,
                speechMode = SpeechCaptureMode.NONE,
                resultSaved = false,
                saveMessage = null,
                processError = null,
                inputWords = emptyList(),
                wordSheet = null,
            )
        }
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
                inputWords = wordMapBuilder.buildFromPlainText(trimmed),
                lastInputSource = inputSource,
                wordSheet = null,
            )
        }
        cancelVoiceAutoAiTimer()
        lastTypedInput = trimmed
        runProcess { processAuto(inputSource) }
    }

    fun processOfflineInput() {
        cancelVoiceAutoAiTimer()
        runProcess { processOffline(recordActivity = true) }
    }

    fun processTypedInputWithSavedLanguages() {
        cancelVoiceAutoAiTimer()
        _uiState.update { it.copy(lastInputSource = InputSource.TYPED) }
        runProcess { processWithAi(inputSource = InputSource.TYPED) }
    }

    private fun shouldRunOfflineWhileTyping(previous: String, current: String): Boolean {
        if (current.isBlank()) return false
        return current.length > previous.length && current.last() == ' '
    }

    private suspend fun currentProcessingIntent(): ProcessingIntent =
        aiModePrefs.current().processingIntent

    private fun runIncrementalOffline(text: String) {
        offlineTypingJob?.cancel()
        offlineTypingJob = viewModelScope.launch {
            val langs = practiceLanguages.value
            val trimmed = text.trim()
            if (trimmed.isBlank()) return@launch
            val intent = currentProcessingIntent()
            val output = runCatching {
                offlinePracticeProcessor.process(
                    text = trimmed,
                    sourceLang = langs.inputLanguage,
                    targetLang = langs.outputLanguage,
                    intent = intent,
                )
            }.getOrElse { "Offline process failed: ${it.message ?: "unknown error"}" }
            if (_uiState.value.typedInput.trim() != trimmed) return@launch
            _uiState.update {
                it.copy(
                    aiOutput = output,
                    aiIntent = intent,
                    outputOffline = true,
                    aiLoading = false,
                    processError = null,
                    outputWords = wordMapBuilder.buildFromPlainText(output),
                    wordSheet = null,
                )
            }
        }
    }

    private fun scheduleVoiceAutoAi() {
        cancelVoiceAutoAiTimer()
        voiceAutoAiJob = viewModelScope.launch {
            delay(VOICE_AUTO_AI_DELAY_MS)
            val text = _uiState.value.typedInput.trim()
            if (text.isBlank()) return@launch
            processWithAi(inputSource = InputSource.VOICE)
        }
    }

    private fun runProcess(block: suspend () -> Unit) {
        offlineTypingJob?.cancel()
        cancelVoiceAutoAiTimer()
        activeProcessJob?.cancel()
        activeProcessJob = viewModelScope.launch {
            block()
        }
    }

    private suspend fun processAuto(inputSource: InputSource = InputSource.TYPED) {
        val text = _uiState.value.typedInput.trim()
        if (text.isBlank() || _uiState.value.isListening) return
        val langs = practiceLanguages.value
        val intent = currentProcessingIntent()
        if (intent == ProcessingIntent.TRANSLATION &&
            !PracticeLanguageRules.isValidTranslationPair(langs.inputLanguage, langs.outputLanguage)
        ) {
            _uiState.update { it.copy(processError = TRANSLATION_LANG_MISMATCH) }
            return
        }
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
                    title = "Learning: typed",
                    sourceLang = langs.inputLanguage,
                    targetLang = langs.outputLanguage,
                    inputSource = inputSource,
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
        val intent = currentProcessingIntent()
        if (intent == ProcessingIntent.TRANSLATION &&
            !PracticeLanguageRules.isValidTranslationPair(langs.inputLanguage, langs.outputLanguage)
        ) {
            _uiState.update {
                it.copy(
                    aiLoading = false,
                    processError = TRANSLATION_LANG_MISMATCH,
                )
            }
            return
        }
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
                title = "Learning: typed",
                sourceLang = langs.inputLanguage,
                targetLang = langs.outputLanguage,
                inputSource = inputSource,
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
                intent = currentProcessingIntent(),
            )
        }.getOrElse { "Offline process failed: ${it.message ?: "unknown error"}" }
        val intent = currentProcessingIntent()
        if (recordActivity) {
            publishProcessResult(
                text = text,
                output = output,
                intent = intent,
                offline = true,
                activityType = "practice_offline",
                title = "Learning: offline process",
                sourceLang = langs.inputLanguage,
                targetLang = langs.outputLanguage,
                inputSource = _uiState.value.lastInputSource,
            )
        } else {
            _uiState.update {
                it.copy(
                    aiLoading = false,
                    aiOutput = output,
                    aiIntent = intent,
                    outputOffline = true,
                    processError = null,
                    outputWords = wordMapBuilder.buildFromPlainText(output),
                    wordSheet = null,
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
        val result = try {
            unifiedTextPipeline.process(
                text = text,
                sourceLang = sourceLang,
                targetLang = targetLang,
                inputSource = inputSource,
            )
        } catch (_: GuestAiLimitReachedException) {
            _uiState.update {
                it.copy(
                    aiLoading = false,
                    processError = GUEST_AI_LOGIN_REQUIRED,
                )
            }
            return null
        } catch (_: Exception) {
            return null
        }
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
        inputSource: InputSource = _uiState.value.lastInputSource,
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
                inputWords = wordMapBuilder.buildFromPlainText(text),
                outputWords = wordMapBuilder.buildFromPlainText(output),
                lastInputSource = inputSource,
                wordSheet = null,
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
        if (_uiState.value.isListening && _uiState.value.speechMode == SpeechCaptureMode.VOICE_INPUT) {
            stopVoiceInput()
            return
        }
        cancelVoiceAutoAiTimer()
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
        speechEngine.startListening(
            inputLang,
            SpeechListenConfig(
                continuous = true,
                silenceTimeoutMs = VOICE_SESSION_SILENCE_MS,
            ),
        )
    }

    fun stopVoiceInput() {
        cancelVoiceAutoAiTimer()
        speechEngine.stopListening()
        _uiState.update {
            it.copy(
                isListening = false,
                speechMode = SpeechCaptureMode.NONE,
                partialSpeech = "",
            )
        }
        if (_uiState.value.typedInput.isNotBlank()) {
            scheduleVoiceAutoAi()
        }
    }

    fun saveCurrentResult() {
        val state = _uiState.value
        val langs = practiceLanguages.value
        if (state.typedInput.isBlank() && state.aiOutput.isNullOrBlank()) return
        viewModelScope.launch {
            val activityId = state.lastActivityId ?: learningActivityRepository.record(
                title = "Learning: saved",
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
                    saveMessage = "Saved to History",
                )
            }
        }
    }

    fun clearSaveMessage() {
        _uiState.update { it.copy(saveMessage = null) }
    }

    fun onWordTap(target: PracticeGrammarTarget, offset: Int) {
        val state = _uiState.value
        val langs = practiceLanguages.value
        val fullText: String
        val words: List<WordSpan>
        val languageCode: String
        val inputSource: InputSource
        when (target) {
            PracticeGrammarTarget.INPUT -> {
                fullText = state.typedInput
                words = state.inputWords
                languageCode = langs.inputLanguage
                inputSource = state.lastInputSource
            }
            PracticeGrammarTarget.OUTPUT -> {
                fullText = state.aiOutput.orEmpty()
                words = state.outputWords
                languageCode = langs.outputLanguage
                inputSource = state.lastInputSource
            }
        }
        if (fullText.isBlank()) return
        val word = wordMapBuilder.findWordAtOffset(words, offset) ?: return
        val depth = grammarDepth.value
        viewModelScope.launch {
            val activePacks = languagePackRepository.observeActive().first()
            val lookupLang = resolvePracticeLanguage(languageCode)
            val def = dictionaryRepository.lookup(word.text, lookupLang)
                ?: placeholderDefinition(
                    word.text,
                    lookupLang,
                    activePacks.isNotEmpty(),
                )
            val contextSnippet = grammarExplainer.contextForDepth(fullText, offset, depth)
            _uiState.update {
                it.copy(
                    wordSheet = WordSheetState(
                        definition = def,
                        grammarLoading = true,
                        grammarDepth = depth,
                        contextSnippet = contextSnippet,
                    ),
                )
            }
            val grammar = try {
                grammarExplainer.explain(
                    fullText = fullText,
                    tapOffset = offset,
                    focusWord = word.text,
                    languageCode = languageCode,
                    targetLang = langs.outputLanguage,
                    depth = depth,
                    inputSource = inputSource,
                )
            } catch (_: GuestAiLimitReachedException) {
                "Sign in to continue using AI grammar."
            } catch (_: Exception) {
                "Grammar unavailable offline."
            }
            _uiState.update { current ->
                current.copy(
                    wordSheet = current.wordSheet?.copy(
                        grammarText = grammar,
                        grammarLoading = false,
                    ),
                )
            }
        }
    }

    fun dismissWordSheet() {
        _uiState.update { it.copy(wordSheet = null) }
    }

    fun setGrammarDepth(depth: GrammarDepth) {
        viewModelScope.launch {
            grammarPreferenceRepository.save(depth)
            val state = _uiState.value
            if (state.typedInput.isNotBlank()) {
                val langs = practiceLanguages.value
                aiPrefetchCoordinator.schedulePracticeWarm(
                    text = state.typedInput,
                    languageCode = langs.inputLanguage,
                    targetLang = langs.outputLanguage,
                    grammarDepth = depth,
                )
            }
            state.aiOutput?.takeIf { it.isNotBlank() }?.let { output ->
                val langs = practiceLanguages.value
                aiPrefetchCoordinator.schedulePracticeWarm(
                    text = output,
                    languageCode = langs.outputLanguage,
                    targetLang = langs.inputLanguage,
                    grammarDepth = depth,
                )
            }
        }
    }

    fun speakWord(text: String) {
        val lang = _uiState.value.wordSheet?.definition?.languageCode
            ?: practiceLanguages.value.inputLanguage
        pronunciationEngine.speakFromStart(text, lang)
    }

    fun toggleWordPlayback(text: String) {
        val lang = _uiState.value.wordSheet?.definition?.languageCode
            ?: practiceLanguages.value.inputLanguage
        pronunciationEngine.togglePlayback(text, lang)
    }

    fun saveSelectedWord() {
        val def = _uiState.value.wordSheet?.definition ?: return
        viewModelScope.launch {
            savedWordRepository.save(
                word = def.word,
                languageCode = def.languageCode,
                meaning = def.meanings.firstOrNull() ?: "",
            )
            _uiState.update { it.copy(grammarStudyMessage = "Saved to study list") }
        }
    }

    fun clearGrammarStudyMessage() {
        _uiState.update { it.copy(grammarStudyMessage = null) }
    }

    private suspend fun resolvePracticeLanguage(preferred: String): String {
        val active = languagePackRepository.observeActive().first().map { it.languageCode }
        if (active.any { it.equals(preferred, ignoreCase = true) }) return preferred
        return active.firstOrNull() ?: preferred
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
                    inputWords = wordMapBuilder.buildFromPlainText(activity.inputText.orEmpty()),
                    outputWords = wordMapBuilder.buildFromPlainText(
                        activity.outputText ?: activity.summary ?: "",
                    ),
                    wordSheet = null,
                )
            }
        }
    }

    private fun handleListeningState(state: ListeningState) {
        when (state) {
            ListeningState.Idle -> {
                if (_uiState.value.speechMode == SpeechCaptureMode.VOICE_INPUT) {
                    _uiState.update {
                        it.copy(
                            isListening = false,
                            speechMode = SpeechCaptureMode.NONE,
                            partialSpeech = "",
                        )
                    }
                    if (_uiState.value.typedInput.isNotBlank()) {
                        scheduleVoiceAutoAi()
                    }
                }
            }
            ListeningState.Listening -> {
                if (_uiState.value.speechMode == SpeechCaptureMode.VOICE_INPUT) {
                    cancelVoiceAutoAiTimer()
                }
                _uiState.update {
                    it.copy(isListening = it.speechMode == SpeechCaptureMode.VOICE_INPUT)
                }
            }
            is ListeningState.Partial -> {
                if (_uiState.value.speechMode == SpeechCaptureMode.VOICE_INPUT) {
                    cancelVoiceAutoAiTimer()
                    _uiState.update {
                        it.copy(partialSpeech = state.text, isListening = true)
                    }
                }
            }
            is ListeningState.Final -> {
                if (_uiState.value.speechMode == SpeechCaptureMode.VOICE_INPUT) {
                    val merged = appendVoiceSegment(_uiState.value.typedInput, state.text)
                    _uiState.update {
                        it.copy(
                            typedInput = merged,
                            partialSpeech = "",
                            isListening = true,
                            speechMode = SpeechCaptureMode.VOICE_INPUT,
                            processError = null,
                            inputWords = wordMapBuilder.buildFromPlainText(merged),
                            lastInputSource = InputSource.VOICE,
                            wordSheet = null,
                        )
                    }
                    val langs = practiceLanguages.value
                    aiPrefetchCoordinator.schedulePracticeWarm(
                        text = merged,
                        languageCode = langs.inputLanguage,
                        targetLang = langs.outputLanguage,
                        grammarDepth = grammarDepth.value,
                    )
                    lastTypedInput = merged
                }
            }
            is ListeningState.Error -> {
                _uiState.update {
                    it.copy(
                        speechError = state.message,
                        speechErrorLinksCalibration = false,
                        isListening = false,
                        speechMode = SpeechCaptureMode.NONE,
                        partialSpeech = "",
                    )
                }
            }
        }
    }

    companion object {
        const val AI_INTERNET_REQUIRED = "Check Internet Connection"
        const val AI_PROCESS_FAILED = "Could not process with AI. Try again."
        const val TRANSLATION_LANG_MISMATCH =
            "Translation mode requires different input and output languages. Change languages in AI Mode settings."
        const val GUEST_AI_LOGIN_REQUIRED =
            "You've used your 99 free AI requests. Sign in or create an account to continue."
        private const val VOICE_AUTO_AI_DELAY_MS = 7_000L
        private const val VOICE_SESSION_SILENCE_MS = 7_000L

        internal fun appendVoiceSegment(base: String, segment: String): String {
            val spoken = segment.trim()
            if (spoken.isBlank()) return base
            if (base.isBlank()) return spoken
            return if (base.last().isWhitespace()) base + spoken else "$base $spoken"
        }

        fun micCalibrationRequiredMessage(languageCode: String): String =
            "Mic not calibrated for ${languageCode.uppercase()}. Click for Calibration."
    }
}
