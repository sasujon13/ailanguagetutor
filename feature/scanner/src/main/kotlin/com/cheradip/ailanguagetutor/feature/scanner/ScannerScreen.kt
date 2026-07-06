package com.cheradip.ailanguagetutor.feature.scanner

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.content.ContextWrapper
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
import com.cheradip.ailanguagetutor.ui.components.CheradipTopBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    launchCapture: Boolean = true,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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
    var importGalleryOpened by remember { mutableStateOf(false) }
    var initialScanLaunched by remember { mutableStateOf(false) }
    var mlKitReplaceSelectedPage by remember { mutableStateOf(false) }
    val mlKitScannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        scope.launch {
            val outcome = withContext(Dispatchers.IO) {
                MlKitDocumentScannerHelper.handleActivityResult(result.resultCode, result.data) { uri ->
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
            }
            when (outcome) {
                is MlKitDocumentScannerHelper.ScanActivityOutcome.Success -> {
                    if (mlKitReplaceSelectedPage) {
                        outcome.pages.firstOrNull()?.let { viewModel.replaceSelectedPage(it) }
                    } else {
                        viewModel.onPhotosCaptured(outcome.pages)
                    }
                    mlKitReplaceSelectedPage = false
                }
                is MlKitDocumentScannerHelper.ScanActivityOutcome.Cancelled -> {
                    mlKitReplaceSelectedPage = false
                    if (scanOnly) viewModel.onScanCaptureFinished()
                }
                is MlKitDocumentScannerHelper.ScanActivityOutcome.Failed ->
                    snackbarHostState.showSnackbar(outcome.message)
            }
        }
    }

    val launchMlKitScan: (replaceSelectedPage: Boolean) -> Unit = { replaceSelectedPage ->
        mlKitReplaceSelectedPage = replaceSelectedPage
        val activity = context.findHostActivity()
        if (activity != null) {
            scope.launch {
                runCatching {
                    val pageLimit = if (replaceSelectedPage) {
                        1
                    } else {
                        viewModel.remainingPageSlots().coerceIn(1, 20)
                    }
                    if (!replaceSelectedPage && pageLimit <= 0) {
                        snackbarHostState.showSnackbar("Maximum 20 pages per document")
                        return@launch
                    }
                    val sender = MlKitDocumentScannerHelper.getScanIntentSender(
                        activity = activity,
                        pageLimit = pageLimit,
                    )
                    mlKitScannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
                }.onFailure {
                    snackbarHostState.showSnackbar("Document scanner unavailable — opening gallery")
                    galleryLauncher.launch("image/*")
                }
            }
        } else {
            galleryLauncher.launch("image/*")
        }
    }

    LaunchedEffect(documentId, launchMode, scanOnly, launchCapture) {
        val sourceType = if (launchMode == ScannerLaunchMode.IMPORT) "import" else "scan"
        viewModel.initDocument(
            existingId = documentId,
            sourceType = sourceType,
            mode = if (launchMode == ScannerLaunchMode.IMPORT) "import" else "camera",
            scanOnlyMode = scanOnly,
            launchCapture = launchCapture,
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

    LaunchedEffect(
        hasCameraPermission,
        launchMode,
        scanOnly,
        uiState.isDocumentReady,
        uiState.pages.isEmpty(),
        uiState.scanInReview,
    ) {
        if (launchMode != ScannerLaunchMode.CAMERA || !hasCameraPermission || !uiState.isDocumentReady || initialScanLaunched) {
            return@LaunchedEffect
        }
        val shouldLaunchMlKit = if (scanOnly) {
            !uiState.scanInReview
        } else {
            uiState.pages.isEmpty()
        }
        if (shouldLaunchMlKit) {
            initialScanLaunched = true
            launchMlKitScan(false)
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
    val showScanOnlyStage = scanOnly && uiState.scanInReview &&
        uiState.pages.isNotEmpty() && uiState.selectedPageId != null
    val showLearningReview = !scanOnly && uiState.pages.isNotEmpty() && uiState.selectedPageId != null
    val previewPath = uiState.previewPath
        ?: uiState.pages.firstOrNull { it.id == uiState.selectedPageId }?.imagePath

    if (!scanOnly && uiState.showExportDialog) {
        ScanExportDialog(
            options = uiState.exportOptions,
            previewPath = previewPath,
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

    val canAddMorePages = !uiState.isSaving && viewModel.remainingPageSlots() > 0
    val onAddScanPage: () -> Unit = {
        if (isImportMode) {
            galleryLauncher.launch("image/*")
        } else {
            launchMlKitScan(false)
        }
    }

    Scaffold(
        topBar = {
            CheradipTopBar(
                title = if (isImportMode) "Import" else "Scanner",
                subtitle = when {
                    showScanOnlyStage -> "Pages kept until Save · tap thumbnail to switch"
                    showLearningReview -> "Review pages · then Process & Read"
                    isImportMode -> "Gallery import"
                    else -> "ML Kit document scan"
                },
                onBack = onBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (showScanOnlyStage) {
                ScannerReadOnlyPreview(
                    imagePath = previewPath,
                    cacheKey = "scan-preview-${uiState.selectedPageId}-${uiState.previewRevision}",
                    isLoading = uiState.isProcessingPreview,
                    onDeletePage = viewModel::deleteSelectedPage,
                    onRescanPage = { launchMlKitScan(true) },
                    rescanEnabled = uiState.selectedPageId != null && !uiState.isSaving,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                ScannerPageThumbnailStrip(
                    pages = uiState.pages,
                    selectedPageId = uiState.selectedPageId,
                    thumbnailPathFor = viewModel::pageThumbnailPath,
                    onSelectPage = viewModel::selectPage,
                    onAddPage = onAddScanPage,
                    addPageEnabled = canAddMorePages,
                )
                Text(
                    text = "${uiState.pageCount} page(s) · tap a thumbnail to switch · + to add more",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                ScanExportOptionsPanel(
                    options = uiState.exportOptions,
                    onUpdate = viewModel::updateExportOptions,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                Button(
                    onClick = viewModel::exportDocument,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    enabled = uiState.pageCount > 0 && !uiState.isSaving,
                ) {
                    Icon(
                        Icons.Default.Save,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Save")
                }
            } else if (showLearningReview) {
                ScannerReadOnlyPreview(
                    imagePath = previewPath,
                    cacheKey = "scan-preview-${uiState.selectedPageId}-${uiState.previewRevision}",
                    isLoading = uiState.isProcessingPreview,
                    onDeletePage = viewModel::deleteSelectedPage,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
                ScannerPageThumbnailStrip(
                    pages = uiState.pages,
                    selectedPageId = uiState.selectedPageId,
                    thumbnailPathFor = viewModel::pageThumbnailPath,
                    onSelectPage = viewModel::selectPage,
                    onAddPage = onAddScanPage,
                    addPageEnabled = canAddMorePages,
                )
                Text(
                    text = "${uiState.pageCount} page(s) · tap a thumbnail to switch · + to add more",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
                Button(
                    onClick = {
                        viewModel.prepareForOcr()
                        uiState.documentId?.let(onDone)
                    },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    enabled = uiState.pageCount > 0 && !uiState.isSaving,
                ) {
                    Icon(
                        Icons.Default.Camera,
                        contentDescription = null,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("Process & Read")
                }
            } else if (isImportMode) {
                MlKitScanPrompt(
                    title = "Pick images from your gallery",
                    primaryLabel = "Open gallery",
                    onPrimary = { galleryLauncher.launch("image/*") },
                    icon = Icons.Default.PhotoLibrary,
                )
            } else if (hasCameraPermission && (!scanOnly || uiState.scanInReview || initialScanLaunched)) {
                MlKitScanPrompt(
                    title = "Scan documents",
                    subtitle = "Auto edge detection, perspective correction, and multi-page capture.",
                    primaryLabel = "Scan document",
                    onPrimary = { launchMlKitScan(false) },
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

private tailrec fun android.content.Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
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
