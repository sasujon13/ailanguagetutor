package com.cheradip.ailanguagetutor.feature.reader

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.OcrStructureService
import com.cheradip.ailanguagetutor.core.ai.ScanEnhanceAnalyzeService
import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivitySyncRepository
import com.cheradip.ailanguagetutor.core.device.ScanPracticePrefill
import com.cheradip.ailanguagetutor.core.device.ScanPracticePrefillRepository
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowRepository
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowSession
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowStage
import com.cheradip.ailanguagetutor.core.model.DocumentPage
import com.cheradip.ailanguagetutor.core.model.ScannedContentType
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
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
    val routeToPractice: Boolean = false,
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
    savedStateHandle: SavedStateHandle,
    private val documentRepository: DocumentRepository,
    private val ocrEngine: MlKitOcrEngine,
    private val contentClassifier: ScannedContentClassifier,
    private val ocrStructureService: OcrStructureService,
    private val scanEnhanceAnalyzeService: ScanEnhanceAnalyzeService,
    private val checkAppAccess: CheckAppAccessUseCase,
    private val wordMapBuilder: WordMapBuilder,
    private val learningActivityRepository: LearningActivityRepository,
    private val learningActivitySyncRepository: LearningActivitySyncRepository,
    private val scanWorkflowRepository: ScanWorkflowRepository,
    private val scanPracticePrefillRepository: ScanPracticePrefillRepository,
) : ViewModel() {

    private val routeToPractice: Boolean = savedStateHandle.get<Boolean>("toPractice") ?: false

    private val _uiState = MutableStateFlow(OcrProcessingUiState(routeToPractice = routeToPractice))
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
                scanWorkflowRepository.save(
                    ScanWorkflowSession(
                        documentId = documentId,
                        stage = ScanWorkflowStage.OCR,
                    ),
                )
                val doc = documentRepository.getDocument(documentId)
                    ?: error("Document not found")
                val pages = documentRepository.getPages(documentId)
                _uiState.update { it.copy(totalPages = pages.size) }

                val pageResults = mutableListOf<ProcessedPage>()
                pages.forEachIndexed { index, page ->
                    pageResults += processPage(page, doc.languageCode)
                    _uiState.update { it.copy(processedPages = index + 1) }
                }

                if (routeToPractice && pageResults.isNotEmpty()) {
                    val combined = pageResults.joinToString("\n\n") { it.finalText }
                    val primary = pageResults.first()
                    scanPracticePrefillRepository.set(
                        ScanPracticePrefill(
                            structuredText = combined,
                            rawOcrText = pageResults.joinToString("\n\n") { it.rawText },
                            contentType = primary.contentType,
                            documentClass = primary.documentClass,
                            previewImagePath = pages.firstOrNull()?.imagePath,
                            ocrConfidence = pageResults.map { it.ocrConfidence }.average().toFloat(),
                            structureBackend = primary.backendLabel,
                            documentId = documentId,
                        ),
                    )
                }
            }.onSuccess {
                recordScanActivity(documentId)
                scanWorkflowRepository.clear()
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

    private data class ProcessedPage(
        val finalText: String,
        val rawText: String,
        val contentType: ScannedContentType,
        val documentClass: String?,
        val ocrConfidence: Float,
        val backendLabel: String,
    )

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

    private suspend fun processPage(page: DocumentPage, languageCode: String): ProcessedPage {
        val alreadyStructured = !page.ocrText.isNullOrBlank() &&
            !page.wordMapJson.isNullOrBlank() &&
            !page.ocrContentType.isNullOrBlank()
        if (alreadyStructured) {
            return ProcessedPage(
                finalText = page.ocrText.orEmpty(),
                rawText = page.ocrText.orEmpty(),
                contentType = ScannedContentType.valueOf(page.ocrContentType!!),
                documentClass = null,
                ocrConfidence = 1f,
                backendLabel = "cached",
            )
        }

        _uiState.update { it.copy(currentStep = OcrPipelineStep.RECOGNIZE) }
        val raw = ocrEngine.recognize(page.imagePath)

        val documentClass = resolveDocumentClass(page.imagePath)

        _uiState.update { it.copy(currentStep = OcrPipelineStep.CLASSIFY) }
        val contentType = contentClassifier.classify(raw.fullText)

        _uiState.update { it.copy(currentStep = OcrPipelineStep.STRUCTURE) }
        val structured = ocrStructureService.structure(
            rawOcrText = raw.fullText,
            contentType = contentType,
            languageCode = languageCode,
            scanDocumentClass = documentClass,
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

        return ProcessedPage(
            finalText = finalText,
            rawText = raw.fullText,
            contentType = structured.contentType,
            documentClass = documentClass,
            ocrConfidence = raw.confidence,
            backendLabel = structured.backendLabel,
        )
    }

    private suspend fun resolveDocumentClass(imagePath: String): String? {
        return runCatching {
            val premium = checkAppAccess.subscriptionTier() != SubscriptionTier.FREE
            scanEnhanceAnalyzeService.analyze(imagePath, premium).recommendation.documentClass.name
                .lowercase()
                .replace('_', '-')
        }.getOrNull()
    }
}
