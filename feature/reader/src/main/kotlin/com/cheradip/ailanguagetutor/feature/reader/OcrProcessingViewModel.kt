package com.cheradip.ailanguagetutor.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.OcrStructureService
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivitySyncRepository
import com.cheradip.ailanguagetutor.core.model.DocumentPage
import com.cheradip.ailanguagetutor.core.model.ScannedContentType
import com.cheradip.ailanguagetutor.core.ocr.MlKitOcrEngine
import com.cheradip.ailanguagetutor.core.ocr.ScannedContentClassifier
import com.cheradip.ailanguagetutor.core.ocr.WordMapBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class OcrProcessingUiState(
    val documentId: Long = 0,
    val totalPages: Int = 0,
    val processedPages: Int = 0,
    val currentStep: OcrPipelineStep = OcrPipelineStep.IDLE,
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
)

enum class OcrPipelineStep {
    IDLE,
    PREPROCESS,
    RECOGNIZE,
    CLASSIFY,
    STRUCTURE,
    SAVE,
}

@HiltViewModel
class OcrProcessingViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val ocrEngine: MlKitOcrEngine,
    private val contentClassifier: ScannedContentClassifier,
    private val ocrStructureService: OcrStructureService,
    private val wordMapBuilder: WordMapBuilder,
    private val learningActivityRepository: LearningActivityRepository,
    private val learningActivitySyncRepository: LearningActivitySyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrProcessingUiState())
    val uiState: StateFlow<OcrProcessingUiState> = _uiState.asStateFlow()

    fun process(documentId: Long) {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    documentId = documentId,
                    isRunning = true,
                    isComplete = false,
                    error = null,
                    currentStep = OcrPipelineStep.PREPROCESS,
                )
            }
            runCatching {
                val doc = documentRepository.getDocument(documentId)
                    ?: error("Document not found")
                val pages = documentRepository.getPages(documentId)
                _uiState.update { it.copy(totalPages = pages.size) }
                pages.forEachIndexed { index, page ->
                    processPage(page, doc.languageCode)
                    _uiState.update { it.copy(processedPages = index + 1) }
                }
            }.onSuccess {
                recordScanActivity(documentId)
                _uiState.update {
                    it.copy(isRunning = false, isComplete = true, currentStep = OcrPipelineStep.SAVE)
                }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(
                        isRunning = false,
                        error = e.message ?: "OCR failed",
                        currentStep = OcrPipelineStep.IDLE,
                    )
                }
            }
        }
    }

    private suspend fun recordScanActivity(documentId: Long) {
        val doc = documentRepository.getDocument(documentId) ?: return
        val pages = documentRepository.getPages(documentId)
        val preview = pages.mapNotNull { it.ocrText }.joinToString("\n\n").take(500)
        val activityType = if (doc.sourceType == "import") "import" else "scan"
        learningActivityRepository.record(
            title = doc.title,
            activityType = activityType,
            languageCode = doc.languageCode,
            documentId = documentId,
            summary = preview.take(120).ifBlank { doc.title },
            inputText = preview.ifBlank { doc.title },
            outputText = preview.ifBlank { null },
        )
        learningActivitySyncRepository.syncIfLoggedIn()
    }

    private suspend fun processPage(page: DocumentPage, languageCode: String) {
        val alreadyStructured = !page.ocrText.isNullOrBlank() &&
            !page.wordMapJson.isNullOrBlank() &&
            !page.ocrContentType.isNullOrBlank()
        if (alreadyStructured) return

        _uiState.update { it.copy(currentStep = OcrPipelineStep.RECOGNIZE) }
        val raw = ocrEngine.recognize(page.imagePath)

        _uiState.update { it.copy(currentStep = OcrPipelineStep.CLASSIFY) }
        val contentType = contentClassifier.classify(raw.fullText)

        _uiState.update { it.copy(currentStep = OcrPipelineStep.STRUCTURE) }
        val structured = ocrStructureService.structure(
            rawOcrText = raw.fullText,
            contentType = contentType,
            languageCode = languageCode,
        )

        val finalText = structured.structuredText.ifBlank { raw.fullText }
        val words = wordMapBuilder.buildFromPlainText(finalText)
        val wordMapJson = wordMapBuilder.toJson(words)

        _uiState.update { it.copy(currentStep = OcrPipelineStep.SAVE) }
        documentRepository.updatePageOcr(
            pageId = page.id,
            ocrText = finalText,
            wordMapJson = wordMapJson,
            ocrContentType = structured.contentType.name,
        )
    }
}
