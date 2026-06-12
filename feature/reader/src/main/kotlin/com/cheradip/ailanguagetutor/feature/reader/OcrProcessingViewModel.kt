package com.cheradip.ailanguagetutor.feature.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivitySyncRepository
import com.cheradip.ailanguagetutor.core.model.DocumentPage
import com.cheradip.ailanguagetutor.core.ocr.MlKitOcrEngine
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
    val isRunning: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class OcrProcessingViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val ocrEngine: MlKitOcrEngine,
    private val learningActivityRepository: LearningActivityRepository,
    private val learningActivitySyncRepository: LearningActivitySyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OcrProcessingUiState())
    val uiState: StateFlow<OcrProcessingUiState> = _uiState.asStateFlow()

    fun process(documentId: Long) {
        if (_uiState.value.isRunning) return
        viewModelScope.launch {
            _uiState.update { it.copy(documentId = documentId, isRunning = true, error = null) }
            runCatching {
                val pages = documentRepository.getPages(documentId)
                _uiState.update { it.copy(totalPages = pages.size) }
                pages.forEachIndexed { index, page ->
                    processPage(page)
                    _uiState.update { it.copy(processedPages = index + 1) }
                }
            }.onSuccess {
                recordScanActivity(documentId)
                _uiState.update { it.copy(isRunning = false, isComplete = true) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isRunning = false, error = e.message ?: "OCR failed")
                }
            }
        }
    }

    private suspend fun recordScanActivity(documentId: Long) {
        val doc = documentRepository.getDocument(documentId) ?: return
        val pages = documentRepository.getPages(documentId)
        val preview = pages.mapNotNull { it.ocrText }.joinToString("\n").take(500)
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

    private suspend fun processPage(page: DocumentPage) {
        if (!page.ocrText.isNullOrBlank() && !page.wordMapJson.isNullOrBlank()) return
        val result = ocrEngine.recognize(page.imagePath)
        documentRepository.updatePageOcr(page.id, result.fullText, result.wordMapJson)
    }
}
