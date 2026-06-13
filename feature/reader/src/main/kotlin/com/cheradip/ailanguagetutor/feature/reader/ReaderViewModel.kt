package com.cheradip.ailanguagetutor.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.AiPrefetchCoordinator
import com.cheradip.ailanguagetutor.core.ai.GrammarExplainer
import com.cheradip.ailanguagetutor.core.ai.GrammarPreferenceRepository
import com.cheradip.ailanguagetutor.core.ai.AIManager
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextPipeline
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextResult
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.audio.TtsPlaybackState
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.database.repository.SavedWordRepository
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.GuestAiLimitReachedException
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.ScannedContentType
import com.cheradip.ailanguagetutor.core.model.WordDefinition
import com.cheradip.ailanguagetutor.core.model.WordSheetState
import com.cheradip.ailanguagetutor.core.model.WordSpan
import com.cheradip.ailanguagetutor.core.ocr.WordMapBuilder
import com.cheradip.ailanguagetutor.core.pack.DictionaryLookupHelper.placeholderDefinition
import com.cheradip.ailanguagetutor.core.pack.DictionaryRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import com.cheradip.ailanguagetutor.core.translation.OfflineTranslationEngine
import com.cheradip.ailanguagetutor.core.translation.TranslationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray
import javax.inject.Inject

data class ReaderUiState(
    val documentId: Long = 0,
    val title: String = "",
    val languageCode: String = "en",
    val targetLanguageCode: String = "fr",
    val fullText: String = "",
    val words: List<WordSpan> = emptyList(),
    val selectedDefinition: WordDefinition? = null,
    val wordSheet: WordSheetState? = null,
    val grammarDepth: GrammarDepth = GrammarDepth.WORD,
    val translations: List<TranslationResult> = emptyList(),
    val showTranslation: Boolean = false,
    val aiPanelText: String? = null,
    val aiPanelIntent: ProcessingIntent? = null,
    val aiLoading: Boolean = false,
    val aiBackendLabel: String? = null,
    val isLoading: Boolean = true,
    val aiPrefetching: Boolean = false,
    val saveMessage: String? = null,
    val guestAiLoginRequired: Boolean = false,
    val primaryContentType: ScannedContentType = ScannedContentType.PROSE,
    val structureBackendLabel: String? = null,
)

@HiltViewModel
class ReaderViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val dictionaryRepository: DictionaryRepository,
    private val wordMapBuilder: WordMapBuilder,
    private val pronunciationEngine: PronunciationEngine,
    private val translationEngine: OfflineTranslationEngine,
    private val savedWordRepository: SavedWordRepository,
    private val learningActivityRepository: LearningActivityRepository,
    private val aiManager: AIManager,
    private val unifiedTextPipeline: UnifiedTextPipeline,
    private val grammarPreferenceRepository: GrammarPreferenceRepository,
    private val grammarExplainer: GrammarExplainer,
    private val aiPrefetchCoordinator: AiPrefetchCoordinator,
    private val languagePackRepository: LanguagePackRepository,
) : ViewModel() {

    val grammarDepth = grammarPreferenceRepository.depth
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GrammarDepth.WORD)

    val playbackState = pronunciationEngine.playbackState
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TtsPlaybackState.IDLE)

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        pronunciationEngine.init()
        viewModelScope.launch {
            grammarPreferenceRepository.depth.collect { depth ->
                _uiState.update { it.copy(grammarDepth = depth) }
                scheduleAiPrefetch(depth)
            }
        }
        viewModelScope.launch {
            aiPrefetchCoordinator.isWarming.collect { warming ->
                _uiState.update { it.copy(aiPrefetching = warming) }
            }
        }
    }

    override fun onCleared() {
        pronunciationEngine.stop()
        aiPrefetchCoordinator.cancel()
        super.onCleared()
    }

    fun load(documentId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, documentId = documentId) }
            val doc = documentRepository.getDocument(documentId)
            val pages = documentRepository.getPages(documentId)
            val text = pages.mapNotNull { it.ocrText }.joinToString("\n\n")
            val words = pages.firstNotNullOfOrNull { page ->
                page.wordMapJson?.let { parseWords(it) }
            }.orEmpty()
            val activePacks = languagePackRepository.observeActive().first()
            val docLang = doc?.languageCode ?: "en"
            val lang = resolveReaderLanguage(docLang, activePacks.map { it.languageCode })
            val targetLang = activePacks
                .map { it.languageCode }
                .firstOrNull { !it.equals(lang, ignoreCase = true) }
                ?: "en"
            val primaryType = pages
                .mapNotNull { it.ocrContentType }
                .map { ScannedContentType.fromStored(it) }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.key ?: ScannedContentType.PROSE
            _uiState.update {
                it.copy(
                    title = doc?.title ?: "Document",
                    languageCode = lang,
                    targetLanguageCode = targetLang,
                    fullText = text.ifBlank { "No text recognized. Try rescanning." },
                    words = words,
                    isLoading = false,
                    primaryContentType = primaryType,
                )
            }
            scheduleAiPrefetch(grammarDepth.value)
            viewModelScope.launch {
                val meta = aiManager.generateActivityMetadata(text.take(500), lang)
                learningActivityRepository.record(
                    title = meta.title,
                    activityType = "read",
                    languageCode = lang,
                    documentId = documentId,
                    summary = meta.summary,
                    tags = meta.tags,
                )
            }
        }
    }

    /** AI via home PC (Mode 4 auto for OCR) or cloud fallback; offline pack if trial expired. */
    fun runAiProcessing() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(aiLoading = true, showTranslation = false) }
            val text = state.fullText.take(800)
            val result = try {
                unifiedTextPipeline.process(
                    text = text,
                    sourceLang = state.languageCode,
                    targetLang = state.targetLanguageCode,
                    inputSource = InputSource.OCR_SCAN,
                )
            } catch (_: GuestAiLimitReachedException) {
                _uiState.update {
                    it.copy(
                        aiLoading = false,
                        guestAiLoginRequired = true,
                    )
                }
                return@launch
            } catch (_: Exception) {
                val offline = if (_uiState.value.aiPanelIntent == ProcessingIntent.TRANSLATION) {
                    translationEngine.translateParagraph(
                        text,
                        state.languageCode,
                        state.targetLanguageCode,
                    ).joinToString("\n") { it.translatedText }
                } else {
                    "Offline: ${text.take(160)}…"
                }
                UnifiedTextResult(
                    output = offline,
                    intent = ProcessingIntent.ANSWER,
                    backend = null,
                )
            }
            _uiState.update {
                it.copy(
                    aiLoading = false,
                    aiPanelText = result.output,
                    aiPanelIntent = result.intent,
                    aiBackendLabel = result.backend?.name,
                )
            }
        }
    }

    fun dismissAiPanel() {
        _uiState.update {
            it.copy(aiPanelText = null, aiPanelIntent = null, aiBackendLabel = null)
        }
    }

    fun toggleTranslation() {
        val state = _uiState.value
        if (state.showTranslation) {
            _uiState.update { it.copy(showTranslation = false) }
            return
        }
        viewModelScope.launch {
            val results = translationEngine.translateParagraph(
                state.fullText.take(800),
                state.languageCode,
                state.targetLanguageCode,
            )
            _uiState.update {
                it.copy(
                    translations = results,
                    showTranslation = true,
                    aiPanelText = null,
                )
            }
        }
    }

    fun onWordTap(offset: Int) {
        val state = _uiState.value
        val word = wordMapBuilder.findWordAtOffset(state.words, offset) ?: return
        val depth = grammarDepth.value
        viewModelScope.launch {
            val activePacks = activePackCodes()
            val lookupLang = resolveReaderLanguage(state.languageCode, activePacks)
            val def = dictionaryRepository.lookup(word.text, lookupLang)
                ?: placeholderDefinition(
                    word.text,
                    lookupLang,
                    activePacks.isNotEmpty(),
                )
            val contextSnippet = grammarExplainer.contextForDepth(state.fullText, offset, depth)
            _uiState.update {
                it.copy(
                    selectedDefinition = def,
                    grammarDepth = depth,
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
                    fullText = state.fullText,
                    tapOffset = offset,
                    focusWord = word.text,
                    languageCode = state.languageCode,
                    targetLang = state.targetLanguageCode,
                    depth = depth,
                    inputSource = InputSource.OCR_SCAN,
                )
            } catch (_: GuestAiLimitReachedException) {
                _uiState.update { it.copy(guestAiLoginRequired = true) }
                return@launch
            } catch (_: Exception) {
                "Grammar unavailable offline."
            }
            _uiState.update { current ->
                val sheet = current.wordSheet?.copy(
                    grammarText = grammar,
                    grammarLoading = false,
                )
                current.copy(wordSheet = sheet)
            }
        }
    }

    fun setGrammarDepth(depth: GrammarDepth) {
        viewModelScope.launch {
            grammarPreferenceRepository.save(depth)
        }
    }

    private fun scheduleAiPrefetch(depth: GrammarDepth) {
        val state = _uiState.value
        if (state.fullText.isBlank() || state.isLoading) return
        aiPrefetchCoordinator.scheduleReaderWarm(
            fullText = state.fullText,
            words = state.words,
            documentLanguageCode = state.languageCode,
            grammarDepth = depth,
            targetLang = state.targetLanguageCode,
            inputSource = InputSource.OCR_SCAN,
        )
    }

    fun dismissDefinition() = _uiState.update {
        it.copy(selectedDefinition = null, wordSheet = null)
    }

    fun speakWord(text: String) {
        pronunciationEngine.speakFromStart(text, _uiState.value.languageCode)
    }

    fun toggleWordPlayback(text: String) {
        pronunciationEngine.togglePlayback(text, _uiState.value.languageCode)
    }

    fun stopPlayback() {
        pronunciationEngine.stop()
    }

    fun saveSelectedWord() {
        val def = _uiState.value.wordSheet?.definition ?: _uiState.value.selectedDefinition ?: return
        viewModelScope.launch {
            savedWordRepository.save(
                word = def.word,
                languageCode = def.languageCode,
                meaning = def.meanings.firstOrNull() ?: "",
            )
            _uiState.update { it.copy(saveMessage = "Saved to study list") }
        }
    }

    fun clearSaveMessage() = _uiState.update { it.copy(saveMessage = null) }

    private suspend fun activePackCodes(): List<String> =
        languagePackRepository.observeActive().first().map { it.languageCode }

    private fun resolveReaderLanguage(documentLang: String, activeCodes: List<String>): String {
        if (activeCodes.any { it.equals(documentLang, ignoreCase = true) }) return documentLang
        return activeCodes.firstOrNull() ?: documentLang
    }

    private fun parseWords(json: String): List<WordSpan> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    WordSpan(
                        text = obj.getString("text"),
                        startOffset = obj.getInt("start"),
                        endOffset = obj.getInt("end"),
                    ),
                )
            }
        }
    }
}
