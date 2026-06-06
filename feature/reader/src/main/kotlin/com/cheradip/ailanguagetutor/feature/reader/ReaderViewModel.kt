package com.cheradip.ailanguagetutor.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.AIManager
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextPipeline
import com.cheradip.ailanguagetutor.core.ai.UnifiedTextResult
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.database.repository.SavedWordRepository
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.WordDefinition
import com.cheradip.ailanguagetutor.core.model.WordSpan
import com.cheradip.ailanguagetutor.core.ocr.WordMapBuilder
import com.cheradip.ailanguagetutor.core.pack.DictionaryRepository
import com.cheradip.ailanguagetutor.core.translation.OfflineTranslationEngine
import com.cheradip.ailanguagetutor.core.translation.TranslationResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
    val translations: List<TranslationResult> = emptyList(),
    val showTranslation: Boolean = false,
    val aiPanelText: String? = null,
    val aiPanelIntent: ProcessingIntent? = null,
    val aiLoading: Boolean = false,
    val aiBackendLabel: String? = null,
    val isLoading: Boolean = true,
    val saveMessage: String? = null,
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
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    init {
        pronunciationEngine.init()
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
            val lang = doc?.languageCode ?: "en"
            _uiState.update {
                it.copy(
                    title = doc?.title ?: "Document",
                    languageCode = lang,
                    fullText = text.ifBlank { "No text recognized. Try rescanning." },
                    words = words,
                    isLoading = false,
                )
            }
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

    /** AI via home PC (Mode 4 auto for OCR) or cloud fallback; offline pack if trial expired. */
    fun runAiProcessing() {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(aiLoading = true, showTranslation = false) }
            val text = state.fullText.take(800)
            val result = runCatching {
                unifiedTextPipeline.process(
                    text = text,
                    sourceLang = state.languageCode,
                    targetLang = state.targetLanguageCode,
                    inputSource = InputSource.OCR_SCAN,
                )
            }.getOrElse {
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
        val word = wordMapBuilder.findWordAtOffset(_uiState.value.words, offset) ?: return
        viewModelScope.launch {
            val def = dictionaryRepository.lookup(word.text, _uiState.value.languageCode)
                ?: WordDefinition(
                    word = word.text,
                    languageCode = _uiState.value.languageCode,
                    meanings = listOf("No offline definition in pack yet."),
                )
            _uiState.update { it.copy(selectedDefinition = def) }
        }
    }

    fun dismissDefinition() = _uiState.update { it.copy(selectedDefinition = null) }

    fun speakWord(text: String) {
        pronunciationEngine.speak(text, _uiState.value.languageCode)
    }

    fun saveSelectedWord() {
        val def = _uiState.value.selectedDefinition ?: return
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
