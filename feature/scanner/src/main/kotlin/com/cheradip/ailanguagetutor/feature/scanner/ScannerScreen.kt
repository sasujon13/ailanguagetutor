package com.cheradip.ailanguagetutor.feature.scanner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.image.ScanTool
import com.cheradip.ailanguagetutor.ui.components.CheradipTopBar
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.launch

enum class ScannerLaunchMode {
    CAMERA,
    IMPORT,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    documentId: Long?,
    onBack: () -> Unit,
    onDone: (Long) -> Unit,
    launchMode: ScannerLaunchMode = ScannerLaunchMode.CAMERA,
    scanOnly: Boolean = false,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { stream ->
            viewModel.onGalleryImage(stream.readBytes())
        }
    }
    val mlKitScannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val scanResult = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
        val pages = MlKitDocumentScannerHelper.extractPageBytes(scanResult) { uri ->
            context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: ByteArray(0)
        }.filter { it.isNotEmpty() }
        viewModel.onPhotosCaptured(pages)
    }
    val scope = rememberCoroutineScope()
    var importGalleryOpened by remember { mutableStateOf(false) }
    var initialScanLaunched by remember { mutableStateOf(false) }

    val launchMlKitScan: () -> Unit = {
        val activity = context as? Activity
        if (activity != null) {
            scope.launch {
                runCatching {
                    val sender = MlKitDocumentScannerHelper.getScanIntentSender(activity)
                    mlKitScannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }.onFailure {
                    snackbarHostState.showSnackbar("Document scanner unavailable")
                    galleryLauncher.launch("image/*")
                }
            }
        } else {
            galleryLauncher.launch("image/*")
        }
    }

    LaunchedEffect(documentId, launchMode, scanOnly) {
        val sourceType = if (launchMode == ScannerLaunchMode.IMPORT) "import" else "scan"
        viewModel.initDocument(
            existingId = documentId,
            sourceType = sourceType,
            mode = if (launchMode == ScannerLaunchMode.IMPORT) "import" else "camera",
            scanOnlyMode = scanOnly,
        )
    }

    LaunchedEffect(launchMode) {
        if (launchMode == ScannerLaunchMode.IMPORT && !importGalleryOpened) {
            importGalleryOpened = true
            galleryLauncher.launch("image/*")
        }
    }

    LaunchedEffect(hasCameraPermission, launchMode) {
        if (launchMode == ScannerLaunchMode.CAMERA && !hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    LaunchedEffect(hasCameraPermission, launchMode, uiState.pages.isEmpty()) {
        if (
            launchMode == ScannerLaunchMode.CAMERA &&
            hasCameraPermission &&
            uiState.pages.isEmpty() &&
            !initialScanLaunched
        ) {
            initialScanLaunched = true
            launchMlKitScan()
        }
    }

    LaunchedEffect(uiState.exportMessage, uiState.error) {
        uiState.exportMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearExportMessage()
        }
        uiState.error?.let { snackbarHostState.showSnackbar(it) }
    }

    val isImportMode = launchMode == ScannerLaunchMode.IMPORT
    val showEditor = uiState.pages.isNotEmpty() && uiState.selectedPageId != null

    if (uiState.showExportDialog) {
        ScanExportDialog(
            options = uiState.exportOptions,
            previewPath = uiState.previewPath ?: uiState.pages.firstOrNull()?.imagePath,
            onDismiss = viewModel::dismissExportDialog,
            onExport = viewModel::exportDocument,
            onUpdate = viewModel::updateExportOptions,
        )
    }
    if (uiState.showExportConflictDialog) {
        ScanExportConflictDialog(
            existingNames = uiState.exportConflictExisting,
            renameHint = uiState.exportConflictRenameHint,
            onReplace = viewModel::confirmExportReplace,
            onRename = viewModel::confirmExportRename,
            onDismiss = viewModel::dismissExportConflictDialog,
        )
    }
    if (uiState.showExportPreview) {
        ScanExportPreviewDialog(
            paths = uiState.exportPreviewPaths,
            onDismiss = viewModel::dismissExportPreview,
        )
    }
    if (uiState.showVersionCompare) {
        ScanVersionCompareDialog(
            label = uiState.versionCompareLabel,
            beforePath = uiState.versionCompareBeforePath,
            afterPath = uiState.versionCompareAfterPath,
            onDismiss = viewModel::dismissVersionCompare,
        )
    }
    if (uiState.showCustomFilterRenameDialog) {
        val slot = uiState.customFilters.find { it.slotId == uiState.renamingCustomSlotId }
        CustomFilterRenameDialog(
            currentName = slot?.displayName ?: "Custom",
            onDismiss = viewModel::dismissRenameCustomFilter,
            onConfirm = viewModel::renameCustomFilter,
        )
    }

    Scaffold(
        topBar = {
            CheradipTopBar(
                title = if (isImportMode) "Import" else "Scanner",
                subtitle = if (showEditor) {
                    if (scanOnly) "Edit · then Save" else "Edit · then Process & Read"
                } else if (isImportMode) {
                    "Gallery import"
                } else {
                    "ML Kit document scan"
                },
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (isImportMode || (showEditor && !isImportMode)) {
                FloatingActionButton(onClick = {
                    if (isImportMode) {
                        galleryLauncher.launch("image/*")
                    } else {
                        launchMlKitScan()
                    }
                }) {
                    Icon(
                        if (isImportMode) Icons.Default.PhotoLibrary else Icons.Default.Camera,
                        contentDescription = "Add page",
                    )
                }
            }
        },
    ) { padding ->
        val editorScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(
                    editorScrollState,
                    enabled = uiState.activeTool != ScanTool.CROP,
                ),
        ) {
            if (showEditor) {
                ScannerPreviewArea(
                    uiState = uiState,
                    onUpdateCrop = viewModel::updateDraftCrop,
                    onDeletePage = viewModel::deleteSelectedPage,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                ScannerPageThumbnailStrip(
                    pages = uiState.pages,
                    selectedPageId = uiState.selectedPageId,
                    thumbnailPathFor = viewModel::pageThumbnailPath,
                    onSelectPage = viewModel::selectPage,
                )
                Text(
                    text = "${uiState.pageCount} page(s) · tap a thumbnail to switch page",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                ScannerEditingControls(
                    uiState = uiState,
                    onOpenTool = viewModel::openTool,
                    onCloseTool = viewModel::closeToolPanel,
                    onApply = viewModel::applyCurrentTool,
                    onPreviewCompareMode = viewModel::setPreviewCompareMode,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo,
                    onRevertAll = viewModel::revertAllEdits,
                    onRevertCurrent = viewModel::revertCurrentTool,
                    onRevertCurrentEffect = viewModel::revertCurrentEffect,
                    onRevertToOriginal = viewModel::revertToOriginal,
                    onCompareHistory = viewModel::compareWithHistory,
                    onRestoreCrop = viewModel::restoreCropBoundaries,
                    onRestoreColors = viewModel::restoreOriginalColors,
                    onJumpToHistory = viewModel::jumpToHistoryStage,
                    onJumpToStage = viewModel::jumpToStage,
                    onAutoDetect = viewModel::autoDetectEdges,
                    onCropPreset = viewModel::applyCropPreset,
                    onUpdateCrop = viewModel::updateDraftCrop,
                    onUpdateTransition = viewModel::updateDraftTransition,
                    onUpdateClean = viewModel::updateDraftClean,
                    onUpdateGray = viewModel::updateDraftGray,
                    onSelectFilterPreset = viewModel::selectFilterPreset,
                    onSaveCustomFilter = viewModel::saveCustomFilter,
                    onRenameCustomFilter = viewModel::requestRenameCustomFilter,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
                Button(
                    onClick = {
                        if (scanOnly) {
                            viewModel.openSaveExportDialog()
                        } else {
                            viewModel.prepareForOcr()
                            uiState.documentId?.let(onDone)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    enabled = uiState.pageCount > 0 && !uiState.isSaving,
                ) {
                    Icon(
                        if (scanOnly) Icons.Default.Save else Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text(if (scanOnly) "Save" else "Process & Read")
                }
            } else if (isImportMode) {
                MlKitScanPrompt(
                    title = "Pick images from your gallery",
                    primaryLabel = "Open gallery",
                    onPrimary = { galleryLauncher.launch("image/*") },
                    icon = Icons.Default.PhotoLibrary,
                )
            } else if (hasCameraPermission) {
                MlKitScanPrompt(
                    title = "Scan documents",
                    subtitle = "Auto edge detection, perspective correction, and multi-page capture.",
                    primaryLabel = "Scan document",
                    onPrimary = launchMlKitScan,
                    icon = Icons.Default.Camera,
                    secondaryLabel = "Import from gallery",
                    onSecondary = { galleryLauncher.launch("image/*") },
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Camera permission required")
                        TextButton(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Grant permission")
                        }
                    }
                }
            }

            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.padding(8.dp).align(Alignment.CenterHorizontally))
            }
        }
    }
}

@Composable
private fun MlKitScanPrompt(
    title: String,
    primaryLabel: String,
    onPrimary: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    subtitle: String? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )
            }
            Button(onClick = onPrimary, modifier = Modifier.padding(top = 12.dp)) {
                Text(primaryLabel)
            }
            if (secondaryLabel != null && onSecondary != null) {
                OutlinedButton(onClick = onSecondary, modifier = Modifier.padding(top = 8.dp)) {
                    Text(secondaryLabel)
                }
            }
        }
    }
}
