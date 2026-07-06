package com.cheradip.ailanguagetutor.feature.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowRepository
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowSession
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowStage
import com.cheradip.ailanguagetutor.core.image.BitmapUtils
import com.cheradip.ailanguagetutor.core.image.CleanParams
import com.cheradip.ailanguagetutor.core.image.CropParams
import com.cheradip.ailanguagetutor.core.image.CropPreset
import com.cheradip.ailanguagetutor.core.image.DocumentDetectionHints
import com.cheradip.ailanguagetutor.core.image.EditHistoryEntry
import com.cheradip.ailanguagetutor.core.image.EditHistorySnapshot
import com.cheradip.ailanguagetutor.core.image.CleanFilterSelection
import com.cheradip.ailanguagetutor.core.image.CleanAdjustmentKind
import com.cheradip.ailanguagetutor.core.image.SavedCustomFilterSlot
import com.cheradip.ailanguagetutor.core.image.DocumentFilterPresets
import com.cheradip.ailanguagetutor.core.image.EditStage
import com.cheradip.ailanguagetutor.core.image.ExportConflictResolution
import com.cheradip.ailanguagetutor.core.image.ExportOptions
import com.cheradip.ailanguagetutor.core.image.GrayParams
import com.cheradip.ailanguagetutor.core.image.PageEditState
import com.cheradip.ailanguagetutor.core.image.QuadPoints
import com.cheradip.ailanguagetutor.core.image.ScanEditEngine
import com.cheradip.ailanguagetutor.core.image.ScanExportService
import com.cheradip.ailanguagetutor.core.image.ScanTool
import com.cheradip.ailanguagetutor.core.image.TransitionParams
import com.cheradip.ailanguagetutor.core.image.parsePageEditStateJson
import com.cheradip.ailanguagetutor.core.image.toJson
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class PreviewCompareMode {
    AFTER,
    ORIGINAL,
    BEFORE,
}

data class CustomFilterSlot(
    val slotId: String,
    val displayName: String,
    val savedSelection: CleanFilterSelection = CleanFilterSelection(),
    val hasSavedSettings: Boolean = false,
)

private fun defaultCustomFilterSlots(): List<CustomFilterSlot> = emptyList()

data class ScannerPageItem(
    val id: Long,
    /** Last applied / working image shown in thumbnail after Apply. */
    val imagePath: String,
    val originalPath: String,
    val pageIndex: Int,
    val width: Int = 0,
    val height: Int = 0,
) {
    /** Thumbnail source: original until first apply, then working image. */
    fun thumbnailPath(applied: Boolean): String = if (applied) imagePath else originalPath
}

data class ScannerUiState(
    val documentId: Long? = null,
    val pageCount: Int = 0,
    val pages: List<ScannerPageItem> = emptyList(),
    val selectedPageId: Long? = null,
    val isSaving: Boolean = false,
    val isProcessingPreview: Boolean = false,
    val error: String? = null,
    val activeTool: ScanTool? = null,
    val previewPath: String? = null,
    val previewRevision: Long = 0L,
    val previewCompareMode: PreviewCompareMode = PreviewCompareMode.AFTER,
    val showExportDialog: Boolean = false,
    val showExportConflictDialog: Boolean = false,
    val exportConflictExisting: List<String> = emptyList(),
    val exportConflictRenameHint: String = "",
    val showExportPreview: Boolean = false,
    val exportPreviewPaths: List<String> = emptyList(),
    val exportMessage: String? = null,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val editHistory: List<Pair<Int, String>> = emptyList(),
    val selectedHistoryIndex: Int = -1,
    val draftCrop: CropParams = CropParams(),
    val draftTransition: TransitionParams = TransitionParams(),
    val draftClean: CleanParams = CleanParams(),
    val draftGray: GrayParams = GrayParams(),
    val exportOptions: ExportOptions = ExportOptions(),
    val cropSkewDegrees: Float = 0f,
    val appliedStages: Set<EditStage> = emptySet(),
    val showVersionCompare: Boolean = false,
    val versionCompareBeforePath: String? = null,
    val versionCompareAfterPath: String? = null,
    val versionCompareLabel: String = "",
    val selectedPageOriginalPath: String? = null,
    val cropSourceWidth: Int = 0,
    val cropSourceHeight: Int = 0,
    val selectedFilterPresetId: String? = null,
    val editingCustomFilter: Boolean = false,
    val draftFilterSelection: CleanFilterSelection = CleanFilterSelection(),
    val expandedCleanAdjustment: CleanAdjustmentKind? = null,
    val customFilters: List<CustomFilterSlot> = defaultCustomFilterSlots(),
    val showCustomFilterRenameDialog: Boolean = false,
    val renamingCustomSlotId: String? = null,
    val isDocumentReady: Boolean = false,
    /** Scan-only: true after ML Kit Next — show export UI instead of forcing capture. */
    val scanInReview: Boolean = false,
)

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val imageStorage: DocumentImageStorage,
    private val languagePackRepository: LanguagePackRepository,
    private val scanEditEngine: ScanEditEngine,
    private val scanExportService: ScanExportService,
    private val scanWorkflowRepository: ScanWorkflowRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val editStates = mutableMapOf<Long, PageEditState>()
    private var previewJob: Job? = null
    private var previewGeneration = 0L
    private val captureMutex = Mutex()
    private var documentSourceType: String = "scan"
    private var launchMode: String = "camera"
    private var scanOnly: Boolean = false

    fun initDocument(
        existingId: Long?,
        sourceType: String = "scan",
        mode: String = "camera",
        scanOnlyMode: Boolean = false,
        launchCapture: Boolean = true,
    ) {
        documentSourceType = sourceType
        launchMode = mode
        scanOnly = scanOnlyMode
        viewModelScope.launch {
            var saved = scanWorkflowRepository.currentSession()
            if (scanOnlyMode && launchCapture && saved != null) {
                saved = saved.copy(inReview = false)
                scanWorkflowRepository.save(saved)
            }
            val resolvedId = existingId ?: resolveResumeDocumentId(saved, scanOnlyMode)
            if (resolvedId != null) {
                applySessionMeta(saved?.takeIf { it.documentId == resolvedId })
                loadDocument(resolvedId)
                restoreWorkflowUi(saved?.takeIf { it.documentId == resolvedId })
            } else if (_uiState.value.documentId == null) {
                val activeLang = languagePackRepository.observeActive().first()
                    .firstOrNull()?.languageCode ?: "en"
                val titlePrefix = if (documentSourceType == "import") "Upload" else "Scan"
                val id = documentRepository.createDocument(
                    title = "$titlePrefix ${System.currentTimeMillis()}",
                    languageCode = activeLang,
                    sourceType = documentSourceType,
                )
                _uiState.update { it.copy(documentId = id) }
                persistWorkflow(stage = ScanWorkflowStage.SCANNER)
            }
            _uiState.update { it.copy(isDocumentReady = true) }
        }
    }

    /** Scan-only: user finished or dismissed ML Kit — show export if pages exist. */
    fun onScanCaptureFinished() {
        if (!scanOnly || _uiState.value.pages.isEmpty()) return
        _uiState.update { it.copy(scanInReview = true) }
        persistWorkflow()
    }

    fun prepareForOcr() {
        viewModelScope.launch {
            persistWorkflow(stage = ScanWorkflowStage.OCR)
        }
    }

    fun markScanOnlyComplete() {
        viewModelScope.launch { scanWorkflowRepository.clear() }
    }

    private suspend fun resolveResumeDocumentId(
        saved: ScanWorkflowSession?,
        requestedScanOnly: Boolean,
    ): Long? {
        if (saved == null || saved.stage != ScanWorkflowStage.SCANNER) return null
        if (saved.scanOnly != requestedScanOnly) return null
        if (documentRepository.getDocument(saved.documentId) == null) {
            scanWorkflowRepository.clear()
            return null
        }
        if (documentRepository.isOcrComplete(saved.documentId)) {
            scanWorkflowRepository.clear()
            return null
        }
        return saved.documentId
    }

    private fun applySessionMeta(session: ScanWorkflowSession?) {
        if (session == null) return
        launchMode = session.mode
        documentSourceType = if (session.mode == "import") "import" else "scan"
    }

    private suspend fun restoreWorkflowUi(session: ScanWorkflowSession?) {
        if (session == null || session.documentId != _uiState.value.documentId) return
        _uiState.update {
            it.copy(
                selectedPageId = session.selectedPageId ?: it.selectedPageId,
                scanInReview = session.inReview,
            )
        }
        (session.selectedPageId ?: _uiState.value.selectedPageId)?.let { refreshPreview(it) }
    }

    private fun persistWorkflow(stage: ScanWorkflowStage = ScanWorkflowStage.SCANNER) {
        val docId = _uiState.value.documentId ?: return
        viewModelScope.launch {
            scanWorkflowRepository.save(
                ScanWorkflowSession(
                    documentId = docId,
                    stage = stage,
                    mode = launchMode,
                    scanOnly = scanOnly,
                    selectedPageId = _uiState.value.selectedPageId,
                    activeTool = _uiState.value.activeTool?.name,
                    inReview = _uiState.value.scanInReview,
                ),
            )
        }
    }

    private suspend fun loadDocument(documentId: Long) {
        val pages = documentRepository.getPages(documentId)
        val items = pages.map { page ->
            val original = page.originalImagePath ?: page.imagePath
            editStates[page.id] = parsePageEditStateJson(
                pageId = page.id,
                json = page.editStateJson,
                originalPath = original,
                workingPath = page.imagePath,
            )
            ScannerPageItem(page.id, page.imagePath, original, page.pageIndex, page.width, page.height)
        }
        _uiState.update {
            it.copy(
                documentId = documentId,
                pageCount = pages.size,
                pages = items,
                selectedPageId = items.lastOrNull()?.id,
            )
        }
        items.lastOrNull()?.id?.let { pageId ->
            syncDraftFromState(pageId)
            refreshPreview(pageId)
        }
        restoreCustomFiltersFromEditStates()
        persistWorkflow(stage = ScanWorkflowStage.SCANNER)
    }

    fun onPhotoCaptured(bytes: ByteArray) {
        viewModelScope.launch {
            captureMutex.withLock {
                addCapturedPage(bytes)
            }
        }
    }

    fun onPhotosCaptured(bytesList: List<ByteArray>) {
        if (bytesList.isEmpty()) return
        viewModelScope.launch {
            captureMutex.withLock {
                _uiState.update { it.copy(isSaving = true, error = null) }
                runCatching {
                    bytesList.forEach { bytes -> addCapturedPage(bytes, batchMode = true) }
                    _uiState.value.pages.lastOrNull()?.id?.let { refreshPreview(it) }
                    if (scanOnly) {
                        _uiState.update { it.copy(scanInReview = true) }
                        persistWorkflow()
                    }
                }.onFailure { e ->
                    _uiState.update { it.copy(error = e.message) }
                }
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun onGalleryImage(bytes: ByteArray) = onPhotoCaptured(bytes)

    fun replaceSelectedPage(bytes: ByteArray) {
        val pageId = _uiState.value.selectedPageId ?: return
        val docId = _uiState.value.documentId ?: return
        val item = _uiState.value.pages.find { it.id == pageId } ?: return
        viewModelScope.launch {
            captureMutex.withLock {
                _uiState.update { it.copy(isSaving = true, error = null) }
                runCatching {
                    val saved = imageStorage.saveCapturedBytes(docId, item.pageIndex, bytes)
                    val freshState = PageEditState(
                        pageId = pageId,
                        originalPath = saved.originalPath,
                        workingPath = saved.path,
                        customFilterSlots = _uiState.value.customFilters.map { it.toSaved() },
                        history = listOf(
                            EditHistoryEntry(
                                stage = EditStage.ORIGINAL,
                                label = "Original",
                                snapshot = EditHistorySnapshot(),
                            ),
                        ),
                        historyIndex = 0,
                    )
                    editStates[pageId] = freshState
                    documentRepository.updatePageImage(
                        pageId = pageId,
                        imagePath = saved.path,
                        width = saved.width,
                        height = saved.height,
                        editStateJson = freshState.toJson(),
                        originalImagePath = saved.originalPath,
                    )
                    _uiState.update { ui ->
                        ui.copy(
                            isSaving = false,
                            pages = ui.pages.map { page ->
                                if (page.id == pageId) {
                                    page.copy(
                                        imagePath = saved.path,
                                        originalPath = saved.originalPath,
                                        width = saved.width,
                                        height = saved.height,
                                    )
                                } else {
                                    page
                                }
                            },
                        )
                    }
                    syncDraftFromState(pageId)
                    refreshPreview(pageId)
                    persistWorkflow()
                }.onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
            }
        }
    }

    fun remainingPageSlots(): Int = (MAX_SCAN_PAGES - _uiState.value.pageCount).coerceAtLeast(0)

    fun pageThumbnailPath(page: ScannerPageItem): String = page.imagePath

    companion object {
        private const val MAX_SCAN_PAGES = 20
    }

    private suspend fun addCapturedPage(
        bytes: ByteArray,
        batchMode: Boolean = false,
    ): Long? {
        val docId = _uiState.value.documentId ?: return null
        if (!batchMode) {
            _uiState.update { it.copy(isSaving = true, error = null) }
        }
        return try {
            val pageIndex = _uiState.value.pageCount
            val saved = imageStorage.saveCapturedBytes(docId, pageIndex, bytes)
            val pageId = documentRepository.addPage(
                documentId = docId,
                imagePath = saved.path,
                width = saved.width,
                height = saved.height,
                originalImagePath = saved.originalPath,
            )
            editStates[pageId] = PageEditState(
                pageId = pageId,
                originalPath = saved.originalPath,
                workingPath = saved.path,
                customFilterSlots = _uiState.value.customFilters.map { it.toSaved() },
                history = listOf(
                    EditHistoryEntry(
                        stage = EditStage.ORIGINAL,
                        label = "Original",
                        snapshot = EditHistorySnapshot(),
                    ),
                ),
                historyIndex = 0,
            )
            val item = ScannerPageItem(
                id = pageId,
                imagePath = saved.path,
                originalPath = saved.originalPath,
                pageIndex = pageIndex,
                width = saved.width,
                height = saved.height,
            )
            _uiState.update {
                it.copy(
                    isSaving = if (batchMode) it.isSaving else false,
                    pageCount = it.pageCount + 1,
                    pages = it.pages + item,
                    selectedPageId = item.id,
                    activeTool = null,
                )
            }
            syncDraftFromState(item.id)
            if (!batchMode) {
                refreshPreview(item.id)
            }
            persistWorkflow()
            item.id
        } catch (e: Exception) {
            if (!batchMode) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            } else {
                throw e
            }
            null
        }
    }

    fun openSaveExportDialog() {
        _uiState.update { it.copy(showExportDialog = true, activeTool = ScanTool.SAVE) }
    }

    fun selectPage(pageId: Long) {
        _uiState.update { it.copy(selectedPageId = pageId, activeTool = null) }
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistWorkflow()
    }

    fun openTool(tool: ScanTool) {
        val resolved = if (tool == ScanTool.GRAY) ScanTool.CLEAN else tool
        if (resolved == ScanTool.SAVE) {
            _uiState.update { it.copy(showExportDialog = true, activeTool = ScanTool.SAVE) }
            persistWorkflow()
            return
        }
        val pageId = _uiState.value.selectedPageId
        _uiState.update {
            it.copy(
                activeTool = resolved,
                previewCompareMode = PreviewCompareMode.AFTER,
            )
        }
        persistWorkflow()
        pageId ?: return
        if (resolved == ScanTool.CLEAN) {
            val state = editStates[pageId] ?: return
            editStates[pageId] = state.copy(
                draftClean = CleanParams(),
                draftGray = GrayParams(),
                draftFilterSelection = scanEditEngine.cleanDraftFromApplied(state),
            )
        }
        syncDraftFromState(pageId)
        if (resolved == ScanTool.ORIGINAL) {
            refreshPreview(pageId, forceOriginal = true)
        } else {
            refreshPreview(pageId)
        }
    }

    fun closeToolPanel() {
        val pageId = _uiState.value.selectedPageId
        if (_uiState.value.activeTool == ScanTool.CLEAN && pageId != null) {
            val state = editStates[pageId]
            if (state != null) {
                editStates[pageId] = state.copy(
                    draftClean = CleanParams(),
                    draftGray = GrayParams(),
                    draftFilterSelection = scanEditEngine.cleanDraftFromApplied(state),
                )
                syncDraftFromState(pageId)
            }
        }
        _uiState.update { it.copy(activeTool = null, previewCompareMode = PreviewCompareMode.AFTER) }
        pageId?.let { refreshPreview(it) }
        persistWorkflow()
    }

    fun setPreviewCompareMode(mode: PreviewCompareMode) {
        _uiState.update { it.copy(previewCompareMode = mode) }
        _uiState.value.selectedPageId?.let { refreshPreview(it) }
    }

    fun deleteSelectedPage() {
        val pageId = _uiState.value.selectedPageId ?: return
        val docId = _uiState.value.documentId ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                editStates.remove(pageId)
                documentRepository.deletePage(pageId)
                val pages = documentRepository.getPages(docId)
                val items = pages.map { page ->
                    val original = page.originalImagePath ?: page.imagePath
                    editStates.getOrPut(page.id) {
                        parsePageEditStateJson(
                            pageId = page.id,
                            json = page.editStateJson,
                            originalPath = original,
                            workingPath = page.imagePath,
                        )
                    }
                    ScannerPageItem(page.id, page.imagePath, original, page.pageIndex, page.width, page.height)
                }
                val nextSelected = items.firstOrNull()?.id
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        pageCount = items.size,
                        pages = items,
                        selectedPageId = nextSelected,
                        activeTool = if (items.isEmpty()) null else it.activeTool,
                        previewCompareMode = PreviewCompareMode.AFTER,
                    )
                }
                nextSelected?.let {
                    syncDraftFromState(it)
                    refreshPreview(it)
                } ?: _uiState.update { it.copy(previewPath = null) }
                persistWorkflow()
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun revertCurrentTool() {
        val tool = _uiState.value.activeTool ?: return
        if (tool == ScanTool.ORIGINAL || tool == ScanTool.SAVE) return
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.resetCurrentToolDraft(state, tool)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
    }

    fun revertCurrentEffect() {
        undo()
    }

    fun revertToOriginal() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.revertAll(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistIfApplied(pageId)
    }

    fun compareWithHistory(index: Int) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val label = state.history.getOrNull(index)?.label ?: "Version $index"
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingPreview = true) }
            runCatching {
                val historical = scanEditEngine.stateAtHistory(state, index)
                val beforeBmp = withContext(Dispatchers.Default) {
                    scanEditEngine.renderApplied(historical, customFilterPresets())
                }
                val tool = _uiState.value.activeTool?.takeUnless { it == ScanTool.SAVE }
                val afterBmp = withContext(Dispatchers.Default) {
                    scanEditEngine.renderPreview(state, tool, customPresets = customFilterPresets())
                }
                val docId = _uiState.value.documentId ?: 0
                val dir = imageStorage.createWorkingCopy(docId, pageId).parentFile!!
                val beforeFile = File(dir, "compare_before_$pageId.jpg")
                val afterFile = File(dir, "compare_after_$pageId.jpg")
                withContext(Dispatchers.IO) {
                    BitmapUtils.save(beforeBmp, beforeFile.absolutePath)
                    BitmapUtils.save(afterBmp, afterFile.absolutePath)
                }
                beforeFile.absolutePath to afterFile.absolutePath
            }.onSuccess { (before, after) ->
                _uiState.update {
                    it.copy(
                        isProcessingPreview = false,
                        showVersionCompare = true,
                        versionCompareBeforePath = before,
                        versionCompareAfterPath = after,
                        versionCompareLabel = label,
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isProcessingPreview = false) }
            }
        }
    }

    fun dismissVersionCompare() {
        _uiState.update {
            it.copy(
                showVersionCompare = false,
                versionCompareBeforePath = null,
                versionCompareAfterPath = null,
                versionCompareLabel = "",
            )
        }
    }

    fun restoreCropBoundaries() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.restoreCropBoundaries(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
    }

    fun restoreOriginalColors() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.restoreOriginalColors(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistIfApplied(pageId)
    }

    fun jumpToStage(stage: EditStage) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        if (stage == EditStage.ORIGINAL) {
            revertToOriginal()
            return
        }
        val index = state.history.indexOfLast { it.stage == stage }
        if (index >= 0) {
            jumpToHistoryStage(index)
        }
    }

    fun updateCropSkew() {
        val corners = _uiState.value.draftCrop.corners
        val skew = scanEditEngine.detectSkew(corners)
        _uiState.update { it.copy(cropSkewDegrees = skew) }
    }

    fun jumpToHistoryStage(index: Int) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.jumpToHistory(state, index)
        syncDraftFromState(pageId)
        _uiState.update {
            it.copy(
                draftFilterSelection = editStates[pageId]?.let { scanEditEngine.cleanDraftFromApplied(it) }
                    ?: CleanFilterSelection(),
                expandedCleanAdjustment = null,
            )
        }
        refreshPreview(pageId)
        persistIfApplied(pageId)
    }

    fun dismissExportPreview() {
        _uiState.update { it.copy(showExportPreview = false, exportPreviewPaths = emptyList()) }
    }

    fun updateDraftCrop(block: (CropParams) -> CropParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val draftCrop = block(state.draftCrop).let { crop ->
            crop.copy(rotationDegrees = normalizeRotationDegrees(crop.rotationDegrees))
        }
        val updated = state.copy(draftCrop = draftCrop)
        editStates[pageId] = updated
        _uiState.update { it.copy(draftCrop = updated.draftCrop) }
        updateCropSkew()
        refreshPreview(pageId, debounceMs = 75)
    }

    private fun normalizeRotationDegrees(degrees: Float): Float =
        ((degrees % 360f) + 360f) % 360f

    fun updateDraftTransition(block: (TransitionParams) -> TransitionParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftTransition = block(state.draftTransition))
        editStates[pageId] = updated
        _uiState.update {
            it.copy(
                draftTransition = updated.draftTransition,
                selectedFilterPresetId = if (it.editingCustomFilter) it.selectedFilterPresetId else null,
            )
        }
        refreshPreview(pageId, debounceMs = 75)
    }

    fun updateDraftClean(block: (CleanParams) -> CleanParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftClean = block(state.draftClean))
        editStates[pageId] = updated
        _uiState.update {
            it.copy(
                draftClean = updated.draftClean,
                selectedFilterPresetId = if (it.editingCustomFilter) it.selectedFilterPresetId else null,
            )
        }
        refreshPreview(pageId, debounceMs = 75)
    }

    fun updateDraftGray(block: (GrayParams) -> GrayParams) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val updated = state.copy(draftGray = block(state.draftGray))
        editStates[pageId] = updated
        _uiState.update {
            it.copy(
                draftGray = updated.draftGray,
                selectedFilterPresetId = if (it.editingCustomFilter) it.selectedFilterPresetId else null,
            )
        }
        refreshPreview(pageId, debounceMs = 75)
    }

    fun toggleCleanFilter(presetId: String) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val current = state.draftFilterSelection
        val nextIds = if (presetId in current.presetIds) {
            current.presetIds - presetId
        } else {
            var base = current.presetIds
            if (presetId in DocumentFilterPresets.colorRowIds) {
                base = base.filterNot { it in DocumentFilterPresets.colorRowIds }
            }
            if (presetId.startsWith("custom_")) {
                base = base.filterNot { it.startsWith("custom_") }
            }
            base + presetId
        }
        updateDraftFilterSelection(state, current.copy(presetIds = nextIds))
    }

    fun toggleCleanAdjustment(kind: CleanAdjustmentKind, level: Int) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        val current = state.draftFilterSelection
        val nextAdjustments = current.adjustments.toMutableMap()
        when {
            level == 0 || current.adjustments[kind] == level -> nextAdjustments.remove(kind)
            else -> nextAdjustments[kind] = level
        }
        updateDraftFilterSelection(state, current.copy(adjustments = nextAdjustments))
    }

    fun setExpandedCleanAdjustment(kind: CleanAdjustmentKind?) {
        _uiState.update { it.copy(expandedCleanAdjustment = kind) }
    }

    fun addCustomFilterFromSelection() {
        val ui = _uiState.value
        val selection = ui.draftFilterSelection
        if (selection.presetIds.isEmpty() && selection.adjustments.isEmpty()) return
        val nextIndex = ui.customFilters.size + 1
        val slotId = "custom_$nextIndex"
        val slot = CustomFilterSlot(
            slotId = slotId,
            displayName = "Custom $nextIndex",
            savedSelection = selection,
            hasSavedSettings = true,
        )
        _uiState.update { it.copy(customFilters = it.customFilters + slot) }
        syncCustomFiltersToEditStates()
        persistCustomFiltersJson()
        requestRenameCustomFilter(slotId)
    }

    private fun updateDraftFilterSelection(state: PageEditState, selection: CleanFilterSelection) {
        val pageId = state.pageId
        val updated = state.copy(draftFilterSelection = selection)
        editStates[pageId] = updated
        _uiState.update {
            it.copy(
                draftFilterSelection = selection,
                selectedFilterPresetId = null,
                editingCustomFilter = false,
            )
        }
        refreshPreview(pageId)
    }

    fun selectFilterPreset(presetId: String) = toggleCleanFilter(presetId)

    fun saveCustomFilter(slotId: String) {
        val ui = _uiState.value
        val updatedSlots = ui.customFilters.map { slot ->
            if (slot.slotId != slotId) slot
            else slot.copy(savedSelection = ui.draftFilterSelection, hasSavedSettings = true)
        }
        _uiState.update { it.copy(customFilters = updatedSlots) }
        syncCustomFiltersToEditStates()
        persistCustomFiltersJson()
    }

    fun requestRenameCustomFilter(slotId: String) {
        _uiState.update { it.copy(showCustomFilterRenameDialog = true, renamingCustomSlotId = slotId) }
    }

    fun dismissRenameCustomFilter() {
        _uiState.update { it.copy(showCustomFilterRenameDialog = false, renamingCustomSlotId = null) }
    }

    fun renameCustomFilter(name: String) {
        val slotId = _uiState.value.renamingCustomSlotId ?: return
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        val updatedSlots = _uiState.value.customFilters.map { slot ->
            if (slot.slotId == slotId) slot.copy(displayName = trimmed) else slot
        }
        _uiState.update {
            it.copy(
                customFilters = updatedSlots,
                showCustomFilterRenameDialog = false,
                renamingCustomSlotId = null,
            )
        }
        syncCustomFiltersToEditStates()
        persistCustomFiltersJson()
    }

    fun updateExportOptions(block: (ExportOptions) -> ExportOptions) {
        _uiState.update { it.copy(exportOptions = block(it.exportOptions)) }
    }

    fun dismissExportDialog() {
        _uiState.update { it.copy(showExportDialog = false, activeTool = null) }
    }

    fun autoDetectEdges(pageId: Long? = null) {
        if (_uiState.value.activeTool == ScanTool.CROP) {
            detectCropCorners(pageId)
        } else {
            val targetId = pageId ?: _uiState.value.selectedPageId ?: return
            viewModelScope.launch {
                autoEnhanceCapturedPage(targetId, openCropTool = false)
            }
        }
    }

    /** Detect document edges into draft crop corners only — does not apply or enhance. */
    fun detectCropCorners(pageId: Long? = null) {
        val targetId = pageId ?: _uiState.value.selectedPageId ?: return
        val state = editStates[targetId] ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessingPreview = true) }
            runCatching {
                val hints = DocumentDetectionHints(
                    scanType = _uiState.value.draftTransition.scanType,
                    cropPreset = _uiState.value.draftCrop.preset,
                )
                val bitmap = withContext(Dispatchers.IO) { BitmapUtils.load(state.originalPath) }
                val curve = scanEditEngine.autoDetectCurve(bitmap, hints)
                state.copy(
                    draftCrop = state.draftCrop.copy(
                        corners = curve.boundingQuad(),
                        curveBoundary = curve,
                        useCurvedBoundary = true,
                        perspectiveCorrection = state.draftCrop.preset != CropPreset.FREEFORM,
                    ),
                )
            }.onSuccess { updated ->
                editStates[targetId] = updated
                _uiState.update {
                    it.copy(
                        isProcessingPreview = false,
                        draftCrop = updated.draftCrop,
                        cropSkewDegrees = scanEditEngine.detectSkew(updated.draftCrop.corners),
                    )
                }
            }.onFailure {
                _uiState.update { it.copy(isProcessingPreview = false) }
            }
        }
    }

    /** CamScanner-style auto crop, perspective, and background clean right after capture. */
    private suspend fun autoEnhanceCapturedPage(pageId: Long, openCropTool: Boolean) {
        val state = editStates[pageId] ?: return
        val hints = DocumentDetectionHints(
            scanType = _uiState.value.draftTransition.scanType,
            cropPreset = CropPreset.RECTANGLE,
        )
        val bitmap = withContext(Dispatchers.IO) { BitmapUtils.load(state.originalPath) }
        val curve = scanEditEngine.autoDetectCurve(bitmap, hints)
        val corners = curve.boundingQuad()
        val cropDraft = CropParams(
            corners = corners,
            curveBoundary = curve,
            useCurvedBoundary = true,
            preset = CropPreset.RECTANGLE,
            perspectiveCorrection = true,
            autoStraighten = false,
        )
        val transitionDraft = TransitionParams(
            corners = corners,
            autoDetect = true,
            perspectiveStrength = 85,
        )
        val cleanDraft = CleanParams()
        var updated = state.copy(
            draftCrop = cropDraft,
            draftTransition = transitionDraft,
            draftClean = cleanDraft,
        )
        updated = scanEditEngine.applyTool(updated, ScanTool.CROP)
        updated = scanEditEngine.applyTool(updated, ScanTool.TRANSITION)
        updated = updated.copy(
            draftFilterSelection = CleanFilterSelection(presetIds = listOf("document")),
        )
        updated = scanEditEngine.applyTool(updated, ScanTool.CLEAN, customFilterPresets())
        val rendered = withContext(Dispatchers.Default) {
            scanEditEngine.renderApplied(updated, customFilterPresets())
        }
        val docId = _uiState.value.documentId ?: return
        val outFile = imageStorage.createWorkingCopy(docId, updated.pageId)
        val saved = withContext(Dispatchers.IO) { BitmapUtils.save(rendered, outFile.absolutePath) }
        val finalState = updated.copy(workingPath = saved.path)
        editStates[pageId] = finalState
        documentRepository.updatePageImage(
            pageId = pageId,
            imagePath = saved.path,
            width = saved.width,
            height = saved.height,
            editStateJson = finalState.toJson(),
        )
        _uiState.update { ui ->
            ui.copy(
                pages = ui.pages.map { page ->
                    if (page.id == pageId) page.copy(imagePath = saved.path) else page
                },
                selectedPageId = pageId,
                activeTool = if (openCropTool) ScanTool.CROP else ui.activeTool,
                cropSkewDegrees = scanEditEngine.detectSkew(cropDraft.corners),
            )
        }
        syncDraftFromState(pageId)
        refreshPreview(pageId)
    }

    fun applyCropPreset(preset: CropPreset) {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.IO) { BitmapUtils.load(state.originalPath) }
            val corners = when (preset) {
                CropPreset.FREEFORM -> state.draftCrop.corners
                CropPreset.RECTANGLE -> state.draftCrop.corners.toAxisAlignedRectangle()
                CropPreset.ID_CARD,
                CropPreset.BUSINESS_CARD,
                CropPreset.PASSPORT,
                -> {
                    val hints = DocumentDetectionHints(
                        scanType = _uiState.value.draftTransition.scanType,
                        cropPreset = preset,
                    )
                    scanEditEngine.autoDetectCrop(bitmap, hints)
                }
                else -> scanEditEngine.presetCrop(preset, bitmap.width, bitmap.height)
            }
            updateDraftCrop {
                it.copy(
                    preset = preset,
                    corners = corners,
                    perspectiveCorrection = preset != CropPreset.FREEFORM,
                )
            }
        }
    }

    fun applyCurrentTool() {
        val tool = _uiState.value.activeTool ?: return
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val updated = when (tool) {
                    ScanTool.ORIGINAL -> scanEditEngine.revertAll(state)
                    else -> scanEditEngine.applyTool(state, tool, customFilterPresets())
                }
                val rendered = withContext(Dispatchers.Default) {
                    scanEditEngine.renderApplied(updated, customFilterPresets())
                }
                val docId = _uiState.value.documentId ?: error("No document")
                val outFile = imageStorage.createWorkingCopy(docId, updated.pageId)
                val saved = withContext(Dispatchers.IO) {
                    BitmapUtils.save(rendered, outFile.absolutePath)
                }
                val finalState = updated.copy(workingPath = saved.path)
                editStates[pageId] = finalState
                documentRepository.updatePageImage(
                    pageId = pageId,
                    imagePath = saved.path,
                    width = saved.width,
                    height = saved.height,
                    editStateJson = finalState.toJson(),
                )
                finalState
            }.onSuccess { finalState ->
                _uiState.update { stateUi ->
                    stateUi.copy(
                        isSaving = false,
                        pages = stateUi.pages.map { p ->
                            if (p.id == pageId) p.copy(imagePath = finalState.workingPath) else p
                        },
                        canUndo = finalState.historyIndex >= 0,
                        canRedo = finalState.historyIndex < finalState.history.lastIndex,
                        editHistory = historyChips(finalState),
                        activeTool = if (tool == ScanTool.ORIGINAL) null else stateUi.activeTool,
                    )
                }
                syncDraftFromState(pageId)
                refreshPreview(pageId)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun revertAllEdits() = applyCurrentToolWithOriginal()

    private fun applyCurrentToolWithOriginal() {
        _uiState.update { it.copy(activeTool = ScanTool.ORIGINAL) }
        applyCurrentTool()
    }

    fun undo() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.undo(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistIfApplied(pageId)
    }

    fun redo() {
        val pageId = _uiState.value.selectedPageId ?: return
        val state = editStates[pageId] ?: return
        editStates[pageId] = scanEditEngine.redo(state)
        syncDraftFromState(pageId)
        refreshPreview(pageId)
        persistIfApplied(pageId)
    }

    fun exportDocument() {
        val options = _uiState.value.exportOptions
        val paths = _uiState.value.pages.map { it.imagePath }
        if (paths.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            runCatching {
                val plan = withContext(Dispatchers.IO) { scanExportService.planExport(paths.size, options) }
                if (plan.hasConflict) {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            showExportConflictDialog = true,
                            exportConflictExisting = plan.conflictingFiles.map { f -> f.name },
                            exportConflictRenameHint = plan.renameTargets.joinToString(", ") { f -> f.name },
                        )
                    }
                    return@launch
                }
                performExport(paths, options, ExportConflictResolution.RENAME)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun confirmExportReplace() {
        val options = _uiState.value.exportOptions
        val paths = _uiState.value.pages.map { it.imagePath }
        viewModelScope.launch {
            _uiState.update { it.copy(showExportConflictDialog = false, isSaving = true) }
            runCatching {
                performExport(paths, options, ExportConflictResolution.REPLACE)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun confirmExportRename() {
        val options = _uiState.value.exportOptions
        val paths = _uiState.value.pages.map { it.imagePath }
        viewModelScope.launch {
            _uiState.update { it.copy(showExportConflictDialog = false, isSaving = true) }
            runCatching {
                performExport(paths, options, ExportConflictResolution.RENAME)
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            }
        }
    }

    fun dismissExportConflictDialog() {
        _uiState.update {
            it.copy(
                showExportConflictDialog = false,
                exportConflictExisting = emptyList(),
                exportConflictRenameHint = "",
            )
        }
    }

    private suspend fun performExport(
        paths: List<String>,
        options: ExportOptions,
        resolution: ExportConflictResolution,
    ) {
        val result = withContext(Dispatchers.IO) {
            scanExportService.export(paths, options, resolution)
        }
        _uiState.update {
            it.copy(
                isSaving = false,
                showExportDialog = false,
                showExportConflictDialog = false,
                exportConflictExisting = emptyList(),
                exportConflictRenameHint = "",
                activeTool = null,
                showExportPreview = true,
                exportPreviewPaths = result.paths,
                exportMessage = "Saved ${result.paths.size} file(s) to Documents/AILanguageTutor/",
            )
        }
        if (scanOnly) {
            viewModelScope.launch { scanWorkflowRepository.clear() }
        }
    }

    fun clearExportMessage() {
        _uiState.update { it.copy(exportMessage = null) }
    }

    private fun persistIfApplied(pageId: Long) {
        val state = editStates[pageId] ?: return
        viewModelScope.launch {
            runCatching {
                val rendered = withContext(Dispatchers.Default) {
                    scanEditEngine.renderApplied(state, customFilterPresets())
                }
                val docId = _uiState.value.documentId ?: return@runCatching
                val outFile = imageStorage.createWorkingCopy(docId, state.pageId)
                val saved = withContext(Dispatchers.IO) { BitmapUtils.save(rendered, outFile.absolutePath) }
                val finalState = state.copy(workingPath = saved.path)
                editStates[pageId] = finalState
                documentRepository.updatePageImage(pageId, saved.path, saved.width, saved.height, finalState.toJson())
            }
        }
    }

    private fun syncDraftFromState(pageId: Long) {
        val state = editStates[pageId] ?: return
        val page = _uiState.value.pages.find { it.id == pageId }
        _uiState.update {
            it.copy(
                draftCrop = state.draftCrop,
                draftTransition = state.draftTransition,
                draftClean = state.draftClean,
                draftGray = state.draftGray,
                draftFilterSelection = state.draftFilterSelection,
                selectedPageOriginalPath = page?.originalPath ?: state.originalPath,
                cropSourceWidth = page?.width?.takeIf { it > 0 } ?: 0,
                cropSourceHeight = page?.height?.takeIf { it > 0 } ?: 0,
                canUndo = state.historyIndex >= 0,
                canRedo = state.historyIndex < state.history.lastIndex,
                editHistory = historyChips(state),
                selectedHistoryIndex = state.historyIndex,
                cropSkewDegrees = scanEditEngine.detectSkew(state.draftCrop.corners),
                appliedStages = state.appliedStages().toSet(),
            )
        }
    }

    private fun historyChips(state: PageEditState): List<Pair<Int, String>> =
        state.history.mapIndexed { index, entry -> index to entry.label }

    private fun refreshPreview(
        pageId: Long,
        forceOriginal: Boolean = false,
        debounceMs: Long = 0,
    ) {
        previewJob?.cancel()
        val generation = ++previewGeneration
        previewJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            if (generation != previewGeneration) return@launch
            val state = editStates[pageId] ?: return@launch
            _uiState.update { it.copy(isProcessingPreview = true) }
            val customPresets = customFilterPresets()
            runCatching {
                val tool = _uiState.value.activeTool?.takeUnless { it == ScanTool.SAVE || it == ScanTool.ORIGINAL }
                val compareMode = _uiState.value.previewCompareMode
                val bitmap = withContext(Dispatchers.Default) {
                    when {
                        compareMode == PreviewCompareMode.ORIGINAL -> scanEditEngine.renderOriginal(state)
                        compareMode == PreviewCompareMode.BEFORE -> scanEditEngine.renderBeforeCurrentTool(state, customPresets)
                        tool != null -> scanEditEngine.renderPreview(state, tool, customPresets = customPresets)
                        else -> scanEditEngine.renderApplied(state, customPresets)
                    }
                }
                if (generation != previewGeneration) return@runCatching null
                val revision = _uiState.value.previewRevision + 1
                val previewFile = File(
                    imageStorage.createWorkingCopy(_uiState.value.documentId ?: 0, pageId).parentFile,
                    "preview_${pageId}_$revision.jpg",
                )
                withContext(Dispatchers.IO) { BitmapUtils.save(bitmap, previewFile.absolutePath) }
                previewFile.absolutePath
            }.onSuccess { path ->
                if (generation != previewGeneration || path == null) {
                    _uiState.update { it.copy(isProcessingPreview = false) }
                    return@onSuccess
                }
                _uiState.update {
                    it.copy(
                        previewPath = path,
                        previewRevision = it.previewRevision + 1,
                        isProcessingPreview = false,
                    )
                }
            }.onFailure {
                if (generation == previewGeneration) {
                    _uiState.update { it.copy(isProcessingPreview = false) }
                }
            }
        }
    }

    private fun customFilterPresets(): List<com.cheradip.ailanguagetutor.core.image.DocumentFilterPreset> =
        _uiState.value.customFilters
            .filter { it.hasSavedSettings }
            .map { DocumentFilterPresets.customSlot(it.slotId, it.displayName, it.savedSelection) }

    private fun CustomFilterSlot.toSaved(): SavedCustomFilterSlot =
        SavedCustomFilterSlot(slotId = slotId, displayName = displayName, savedSelection = savedSelection)

    private fun SavedCustomFilterSlot.toUi(): CustomFilterSlot =
        CustomFilterSlot(
            slotId = slotId,
            displayName = displayName,
            savedSelection = savedSelection,
            hasSavedSettings = true,
        )

    private fun syncCustomFiltersToEditStates() {
        val saved = _uiState.value.customFilters.map { it.toSaved() }
        editStates.keys.toList().forEach { id ->
            editStates[id]?.let { state ->
                editStates[id] = state.copy(customFilterSlots = saved)
            }
        }
    }

    private fun restoreCustomFiltersFromEditStates() {
        val restored = editStates.values
            .flatMap { it.customFilterSlots }
            .distinctBy { it.slotId }
        if (restored.isNotEmpty()) {
            _uiState.update { it.copy(customFilters = restored.map { slot -> slot.toUi() }) }
        }
    }

    private fun persistCustomFiltersJson() {
        syncCustomFiltersToEditStates()
        val pageId = _uiState.value.pages.firstOrNull()?.id ?: return
        val state = editStates[pageId] ?: return
        viewModelScope.launch {
            runCatching {
                documentRepository.updatePageEditState(pageId, state.toJson())
            }
        }
    }
}
