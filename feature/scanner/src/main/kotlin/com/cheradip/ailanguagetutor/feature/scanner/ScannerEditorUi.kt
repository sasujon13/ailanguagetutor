package com.cheradip.ailanguagetutor.feature.scanner

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FilterBAndW
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.cheradip.ailanguagetutor.core.image.EditStage
import com.cheradip.ailanguagetutor.core.image.WatermarkMode
import com.cheradip.ailanguagetutor.core.image.CleanParams
import com.cheradip.ailanguagetutor.core.image.CropPreset
import com.cheradip.ailanguagetutor.core.image.DocumentScanType
import com.cheradip.ailanguagetutor.core.image.ExportCompression
import com.cheradip.ailanguagetutor.core.image.ExportFormat
import com.cheradip.ailanguagetutor.core.image.ExportMargins
import com.cheradip.ailanguagetutor.core.image.ExportOptions
import com.cheradip.ailanguagetutor.core.image.ExportOrientation
import com.cheradip.ailanguagetutor.core.image.ExportPageSize
import com.cheradip.ailanguagetutor.core.image.ExportQuality
import com.cheradip.ailanguagetutor.core.image.GrayMode
import com.cheradip.ailanguagetutor.core.image.GrayParams
import com.cheradip.ailanguagetutor.core.image.PointF
import com.cheradip.ailanguagetutor.core.image.QuadPoints
import com.cheradip.ailanguagetutor.core.image.ScanTool
import com.cheradip.ailanguagetutor.core.image.TransitionParams
import kotlin.math.hypot
import kotlin.math.roundToInt

@Composable
fun ScannerPreviewArea(
    uiState: ScannerUiState,
    onUpdateCrop: ((com.cheradip.ailanguagetutor.core.image.CropParams) -> com.cheradip.ailanguagetutor.core.image.CropParams) -> Unit,
    onUpdateTransition: ((TransitionParams) -> TransitionParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 4f)
        offsetX += panChange.x
        offsetY += panChange.y
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .transformable(state = transformState),
        contentAlignment = Alignment.Center,
    ) {
        val preview = uiState.previewPath
        if (preview != null) {
            AsyncImage(
                model = preview,
                contentDescription = "Page preview",
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    },
                contentScale = ContentScale.Fit,
            )
            if (uiState.activeTool == ScanTool.CROP || uiState.activeTool == ScanTool.TRANSITION) {
                val cropPreset = uiState.draftCrop.preset
                CropCornerOverlay(
                    corners = if (uiState.activeTool == ScanTool.CROP) {
                        uiState.draftCrop.corners
                    } else {
                        uiState.draftTransition.corners
                    },
                    previewPath = preview,
                    lockRectangle = uiState.activeTool == ScanTool.CROP && cropPreset != CropPreset.FREEFORM,
                    onCornersChanged = { quad ->
                        if (uiState.activeTool == ScanTool.CROP) {
                            onUpdateCrop { it.copy(corners = quad) }
                        } else {
                            onUpdateTransition { it.copy(corners = quad, autoDetect = false) }
                        }
                    },
                )
            }
        }
        if (uiState.isProcessingPreview) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
    }
}

@Composable
fun ScannerEditingControls(
    uiState: ScannerUiState,
    onOpenTool: (ScanTool) -> Unit,
    onCloseTool: () -> Unit,
    onApply: () -> Unit,
    onCompareHold: (Boolean) -> Unit,
    onBeforeAfter: (Float) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onRevertAll: () -> Unit,
    onRevertCurrent: () -> Unit,
    onRevertCurrentEffect: () -> Unit,
    onRevertToOriginal: () -> Unit,
    onCompareHistory: (Int) -> Unit,
    onRestoreCrop: () -> Unit,
    onRestoreColors: () -> Unit,
    onJumpToHistory: (Int) -> Unit,
    onJumpToStage: (EditStage) -> Unit,
    onAutoDetect: () -> Unit,
    onCropPreset: (CropPreset) -> Unit,
    onUpdateCrop: ((com.cheradip.ailanguagetutor.core.image.CropParams) -> com.cheradip.ailanguagetutor.core.image.CropParams) -> Unit,
    onUpdateTransition: ((TransitionParams) -> TransitionParams) -> Unit,
    onUpdateClean: ((CleanParams) -> CleanParams) -> Unit,
    onUpdateGray: ((GrayParams) -> GrayParams) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            IconButton(onClick = onUndo, enabled = uiState.canUndo) {
                Icon(Icons.Default.Undo, contentDescription = "Undo")
            }
            IconButton(onClick = onRedo, enabled = uiState.canRedo) {
                Icon(Icons.Default.Redo, contentDescription = "Redo")
            }
            TextButton(onClick = onRevertAll) { Text("Revert all") }
            TextButton(onClick = onRevertToOriginal) { Text("To original") }
        }

        ScannerToolBar(activeTool = uiState.activeTool, onOpenTool = onOpenTool)

        uiState.activeTool?.let { tool ->
            ScannerToolPanel(
                tool = tool,
                uiState = uiState,
                onClose = onCloseTool,
                onApply = onApply,
                onCompareHold = onCompareHold,
                onBeforeAfter = onBeforeAfter,
                onRevertCurrent = onRevertCurrent,
                onRevertCurrentEffect = onRevertCurrentEffect,
                onRevertAll = onRevertAll,
                onRestoreCrop = onRestoreCrop,
                onRestoreColors = onRestoreColors,
                onAutoDetect = onAutoDetect,
                onCropPreset = onCropPreset,
                onUpdateCrop = onUpdateCrop,
                onUpdateTransition = onUpdateTransition,
                onUpdateClean = onUpdateClean,
                onUpdateGray = onUpdateGray,
            )
        }

        if (uiState.editHistory.isNotEmpty() || uiState.appliedStages.isNotEmpty()) {
            Text("Version history", style = MaterialTheme.typography.labelMedium)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                EditStage.entries.forEach { stage ->
                    FilterChip(
                        selected = uiState.appliedStages.contains(stage) || (stage == EditStage.ORIGINAL && uiState.appliedStages.isEmpty()),
                        onClick = { onJumpToStage(stage) },
                        label = { Text(stage.name.lowercase().replaceFirstChar { it.uppercase() }) },
                    )
                }
            }
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(16.dp))
                uiState.editHistory.takeLast(8).forEach { (index, label) ->
                    Box(
                        modifier = Modifier.pointerInput(index) {
                            detectTapGestures(
                                onTap = { onJumpToHistory(index) },
                                onLongPress = { onCompareHistory(index) },
                            )
                        },
                    ) {
                        FilterChip(
                            selected = false,
                            onClick = { onJumpToHistory(index) },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
                Text("Long-press history to compare", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ScannerToolBar(activeTool: ScanTool?, onOpenTool: (ScanTool) -> Unit) {
    val tools = listOf(
        ScanTool.ORIGINAL to ("Original" to Icons.Default.Restore),
        ScanTool.CROP to ("Crop" to Icons.Default.Crop),
        ScanTool.TRANSITION to ("Transition" to Icons.Default.Transform),
        ScanTool.CLEAN to ("Clean" to Icons.Default.AutoFixHigh),
        ScanTool.GRAY to ("Gray" to Icons.Default.FilterBAndW),
        ScanTool.SAVE to ("Save" to Icons.Default.Save),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tools.forEach { (tool, labelIcon) ->
            val (label, icon) = labelIcon
            FilterChip(
                selected = activeTool == tool,
                onClick = { onOpenTool(tool) },
                label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun ScannerToolPanel(
    tool: ScanTool,
    uiState: ScannerUiState,
    onClose: () -> Unit,
    onApply: () -> Unit,
    onCompareHold: (Boolean) -> Unit,
    onBeforeAfter: (Float) -> Unit,
    onRevertCurrent: () -> Unit,
    onRevertCurrentEffect: () -> Unit,
    onRevertAll: () -> Unit,
    onRestoreCrop: () -> Unit,
    onRestoreColors: () -> Unit,
    onAutoDetect: () -> Unit,
    onCropPreset: (CropPreset) -> Unit,
    onUpdateCrop: ((com.cheradip.ailanguagetutor.core.image.CropParams) -> com.cheradip.ailanguagetutor.core.image.CropParams) -> Unit,
    onUpdateTransition: ((TransitionParams) -> TransitionParams) -> Unit,
    onUpdateClean: ((CleanParams) -> CleanParams) -> Unit,
    onUpdateGray: ((GrayParams) -> GrayParams) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        when (tool) {
            ScanTool.ORIGINAL -> {
                Text("Tap Original anytime to reload the untouched capture.", style = MaterialTheme.typography.bodySmall)
                HoldCompareButton(onCompareHold = onCompareHold)
                BeforeAfterSlider(uiState.beforeAfterSlider, onBeforeAfter)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRevertCurrentEffect) { Text("Revert effect") }
                    TextButton(onClick = onRevertAll) { Text("Revert all") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onRevertCurrent) { Text("Reset tool") }
                    TextButton(onClick = onRestoreCrop) { Text("Restore crop") }
                    TextButton(onClick = onRestoreColors) { Text("Restore colors") }
                }
                Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("Apply reset all") }
            }
            ScanTool.CROP -> {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CropPreset.entries.forEach { preset ->
                        AssistChip(onClick = { onCropPreset(preset) }, label = { Text(preset.name.replace('_', ' ')) })
                    }
                }
                FloatSlider("Rotation °", uiState.draftCrop.rotationDegrees, -180f, 180f) { v ->
                    onUpdateCrop { it.copy(rotationDegrees = v) }
                }
                if (kotlin.math.abs(uiState.cropSkewDegrees) > 1f) {
                    Text(
                        "Skew detected: ${uiState.cropSkewDegrees.toInt()}° — use Auto straighten",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ToggleChip("Auto straighten", uiState.draftCrop.autoStraighten) {
                        onUpdateCrop { p -> p.copy(autoStraighten = !p.autoStraighten) }
                    }
                    ToggleChip("Perspective", uiState.draftCrop.perspectiveCorrection) {
                        onUpdateCrop { p -> p.copy(perspectiveCorrection = !p.perspectiveCorrection) }
                    }
                    ToggleChip("Keystone", uiState.draftCrop.keystoneCorrection) {
                        onUpdateCrop { p -> p.copy(keystoneCorrection = !p.keystoneCorrection) }
                    }
                    ToggleChip("H align", uiState.draftCrop.horizontalAlignment) {
                        onUpdateCrop { p -> p.copy(horizontalAlignment = !p.horizontalAlignment) }
                    }
                    ToggleChip("V align", uiState.draftCrop.verticalAlignment) {
                        onUpdateCrop { p -> p.copy(verticalAlignment = !p.verticalAlignment) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onAutoDetect) { Text("Auto detect edges") }
                    TextButton(onClick = onRestoreCrop) { Text("Restore boundaries") }
                }
                HoldCompareButton(onCompareHold = onCompareHold)
                BeforeAfterSlider(uiState.beforeAfterSlider, onBeforeAfter)
            }
            ScanTool.TRANSITION -> {
                Text("Smart detection", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    DocumentScanType.entries.forEach { type ->
                        FilterChip(
                            selected = uiState.draftTransition.scanType == type,
                            onClick = {
                                onUpdateTransition { it.copy(scanType = type) }
                                onAutoDetect()
                            },
                            label = { Text(type.name.replace('_', ' ')) },
                        )
                    }
                }
                TransitionIntSlider("Perspective strength", uiState.draftTransition.perspectiveStrength, onUpdateTransition) { p, v ->
                    p.copy(perspectiveStrength = v)
                }
                FloatSlider("Rotation °", uiState.draftTransition.rotationDegrees, -180f, 180f) { v ->
                    onUpdateTransition { it.copy(rotationDegrees = v) }
                }
                TransitionIntSlider("Vertical correction", uiState.draftTransition.verticalCorrection, onUpdateTransition) { p, v ->
                    p.copy(verticalCorrection = v)
                }
                TransitionIntSlider("Horizontal correction", uiState.draftTransition.horizontalCorrection, onUpdateTransition) { p, v ->
                    p.copy(horizontalCorrection = v)
                }
                TransitionIntSlider("Page flattening", uiState.draftTransition.pageFlattening, onUpdateTransition) { p, v ->
                    p.copy(pageFlattening = v)
                }
                ToggleChip("Auto straighten text", uiState.draftTransition.autoStraightenText) {
                    onUpdateTransition { p -> p.copy(autoStraightenText = !p.autoStraightenText) }
                }
                ToggleChip("Curved page", uiState.draftTransition.curvedPageCorrection) {
                    onUpdateTransition { p -> p.copy(curvedPageCorrection = !p.curvedPageCorrection) }
                }
                TextButton(onClick = onAutoDetect) { Text("Auto detect page") }
                HoldCompareButton(onCompareHold = onCompareHold)
                BeforeAfterSlider(uiState.beforeAfterSlider, onBeforeAfter)
            }
            ScanTool.CLEAN -> {
                FilterChip(
                    selected = uiState.draftClean.autoEnhance,
                    onClick = { onUpdateClean { it.copy(autoEnhance = !it.autoEnhance) } },
                    label = { Text("One-click auto enhance") },
                )
                ToggleChip("Adaptive threshold", uiState.draftClean.adaptiveThreshold) {
                    onUpdateClean { p -> p.copy(adaptiveThreshold = !p.adaptiveThreshold) }
                }
                ToggleChip("Preserve signatures", uiState.draftClean.preserveSignatures) {
                    onUpdateClean { p -> p.copy(preserveSignatures = !p.preserveSignatures) }
                }
                ToggleChip("Preserve stamps", uiState.draftClean.preserveStamps) {
                    onUpdateClean { p -> p.copy(preserveStamps = !p.preserveStamps) }
                }
                ToggleChip("Preserve logos", uiState.draftClean.preserveLogos) {
                    onUpdateClean { p -> p.copy(preserveLogos = !p.preserveLogos) }
                }
                CleanIntSlider("Brightness", uiState.draftClean.brightness, onUpdateClean) { p, v -> p.copy(brightness = v) }
                CleanIntSlider("Contrast", uiState.draftClean.contrast, onUpdateClean) { p, v -> p.copy(contrast = v) }
                CleanIntSlider("Sharpness", uiState.draftClean.sharpness, onUpdateClean) { p, v -> p.copy(sharpness = v) }
                CleanIntSlider("Noise reduction", uiState.draftClean.noiseReduction, onUpdateClean) { p, v -> p.copy(noiseReduction = v) }
                CleanIntSlider("Shadow removal", uiState.draftClean.shadowRemoval, onUpdateClean) { p, v -> p.copy(shadowRemoval = v) }
                CleanIntSlider("Paper whitening", uiState.draftClean.paperWhitening, onUpdateClean) { p, v -> p.copy(paperWhitening = v) }
                CleanIntSlider("Ink enhancement", uiState.draftClean.inkEnhancement, onUpdateClean) { p, v -> p.copy(inkEnhancement = v) }
                HoldCompareButton(onCompareHold = onCompareHold)
                BeforeAfterSlider(uiState.beforeAfterSlider, onBeforeAfter)
            }
            ScanTool.GRAY -> {
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GrayMode.entries.forEach { mode ->
                        FilterChip(
                            selected = uiState.draftGray.mode == mode,
                            onClick = { onUpdateGray { it.copy(mode = mode) } },
                            label = { Text(mode.name.replace('_', ' ')) },
                        )
                    }
                }
                GrayIntSlider("Brightness", uiState.draftGray.brightness, onUpdateGray) { p, v -> p.copy(brightness = v) }
                GrayIntSlider("Contrast", uiState.draftGray.contrast, onUpdateGray) { p, v -> p.copy(contrast = v) }
                GrayIntSlider("Exposure", uiState.draftGray.exposure, onUpdateGray) { p, v -> p.copy(exposure = v) }
                GrayIntSlider("Gamma", uiState.draftGray.gamma, onUpdateGray) { p, v -> p.copy(gamma = v) }
                GrayIntSlider("Black point", uiState.draftGray.blackPoint, onUpdateGray) { p, v -> p.copy(blackPoint = v) }
                GrayIntSlider("White point", uiState.draftGray.whitePoint, onUpdateGray) { p, v -> p.copy(whitePoint = v) }
                ToggleChip("Darken text", uiState.draftGray.darkenText) {
                    onUpdateGray { p -> p.copy(darkenText = !p.darkenText) }
                }
                ToggleChip("Lighten paper", uiState.draftGray.lightenPaper) {
                    onUpdateGray { p -> p.copy(lightenPaper = !p.lightenPaper) }
                }
                ToggleChip("Improve OCR", uiState.draftGray.improveOcrAccuracy) {
                    onUpdateGray { p -> p.copy(improveOcrAccuracy = !p.improveOcrAccuracy) }
                }
                HoldCompareButton(onCompareHold = onCompareHold)
                BeforeAfterSlider(uiState.beforeAfterSlider, onBeforeAfter)
            }
            ScanTool.SAVE -> {
                Text("Export to PDF, separate images, or one long image.", style = MaterialTheme.typography.bodyMedium)
                Text("Files save to Documents/AILanguageTutor/", style = MaterialTheme.typography.bodySmall)
            }
        }
        if (tool != ScanTool.ORIGINAL && tool != ScanTool.SAVE) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onClose) { Text("Cancel") }
                TextButton(onClick = onRevertCurrent) { Text("Revert") }
                Button(onClick = onApply, enabled = !uiState.isSaving) { Text("Apply") }
            }
        }
    }
}

@Composable
private fun ToggleChip(label: String, selected: Boolean, onToggle: () -> Unit) {
    FilterChip(selected = selected, onClick = onToggle, label = { Text(label, style = MaterialTheme.typography.labelSmall) })
}

@Composable
fun ScanExportDialog(
    options: ExportOptions,
    previewPath: String?,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onUpdate: ((ExportOptions) -> ExportOptions) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export document") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (previewPath != null) {
                    AsyncImage(
                        model = previewPath,
                        contentDescription = "Export preview",
                        modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                OutlinedTextField(
                    value = options.documentName,
                    onValueChange = { name -> onUpdate { it.copy(documentName = name) } },
                    label = { Text("Document name (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                EnumChips("Format", ExportFormat.entries, options.format) { f -> onUpdate { it.copy(format = f) } }
                EnumChips("Quality", ExportQuality.entries, options.quality) { q -> onUpdate { it.copy(quality = q) } }
                EnumChips("Compression", ExportCompression.entries, options.compression) { c -> onUpdate { it.copy(compression = c) } }
                EnumChips("Page size", ExportPageSize.entries, options.pageSize) { s -> onUpdate { it.copy(pageSize = s) } }
                EnumChips("Margins", ExportMargins.entries, options.margins) { m -> onUpdate { it.copy(margins = m) } }
                EnumChips("Orientation", ExportOrientation.entries, options.orientation) { o -> onUpdate { it.copy(orientation = o) } }
                EnumChips("Watermark", WatermarkMode.entries, options.watermarkMode) { mode ->
                    onUpdate { it.copy(watermarkMode = mode, useTimestampWatermark = mode == WatermarkMode.TIMESTAMP) }
                }
                if (options.watermarkMode == WatermarkMode.CUSTOM) {
                    OutlinedTextField(
                        value = options.watermark,
                        onValueChange = { w -> onUpdate { it.copy(watermark = w) } },
                        label = { Text("Custom watermark text") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = options.passwordEnabled, onCheckedChange = { enabled ->
                        onUpdate { it.copy(passwordEnabled = enabled) }
                    })
                    Text("Password protect PDF", modifier = Modifier.padding(start = 8.dp))
                }
                if (options.passwordEnabled) {
                    OutlinedTextField(
                        value = options.password,
                        onValueChange = { p -> onUpdate { it.copy(password = p) } },
                        label = { Text("PDF password") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(value = options.author, onValueChange = { v -> onUpdate { it.copy(author = v) } }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = options.title, onValueChange = { v -> onUpdate { it.copy(title = v) } }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = options.subject, onValueChange = { v -> onUpdate { it.copy(subject = v) } }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = options.keywords, onValueChange = { v -> onUpdate { it.copy(keywords = v) } }, label = { Text("Keywords") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = onExport) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun <T : Enum<T>> EnumChips(label: String, values: List<T>, selected: T, onSelect: (T) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }) },
            )
        }
    }
}

@Composable
fun ScanExportPreviewDialog(
    paths: List<String>,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Export complete") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Saved to Documents/AILanguageTutor/")
                paths.take(3).forEach { path ->
                    AsyncImage(
                        model = path,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp)),
                        contentScale = ContentScale.Fit,
                    )
                    Text(path.substringAfterLast('/'), style = MaterialTheme.typography.labelSmall)
                }
                if (paths.size > 3) Text("+ ${paths.size - 3} more file(s)")
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
fun ScanVersionCompareDialog(
    label: String,
    beforePath: String?,
    afterPath: String?,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Compare: $label") },
        text = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Before", style = MaterialTheme.typography.labelSmall)
                    if (beforePath != null) {
                        AsyncImage(
                            model = beforePath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("After", style = MaterialTheme.typography.labelSmall)
                    if (afterPath != null) {
                        AsyncImage(
                            model = afterPath,
                            contentDescription = null,
                            modifier = Modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(6.dp)),
                            contentScale = ContentScale.Fit,
                        )
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun BeforeAfterSlider(value: Float, onValueChange: (Float) -> Unit) {
    Text("Before / after", style = MaterialTheme.typography.labelMedium)
    Slider(value = value, onValueChange = onValueChange)
}

@Composable
private fun HoldCompareButton(onCompareHold: (Boolean) -> Unit) {
    TextButton(
        onClick = {},
        modifier = Modifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onCompareHold(true)
                    tryAwaitRelease()
                    onCompareHold(false)
                },
            )
        },
    ) {
        Icon(Icons.Default.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(" Hold to compare")
    }
}

@Composable
private fun CropCornerOverlay(
    corners: QuadPoints,
    previewPath: String,
    lockRectangle: Boolean,
    onCornersChanged: (QuadPoints) -> Unit,
) {
    var size by remember { mutableStateOf(IntSize.Zero) }
    var dragIndex by remember { mutableStateOf(-1) }
    var magnifierCenter by remember { mutableStateOf<Offset?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { size = it }
            .pointerInput(corners, size) {
                detectDragGestures(
                    onDragStart = { offset ->
                        dragIndex = nearestHandleIndex(offset, corners, size)
                        magnifierCenter = offset
                    },
                    onDragEnd = { dragIndex = -1; magnifierCenter = null },
                    onDragCancel = { dragIndex = -1; magnifierCenter = null },
                    onDrag = { change, _ ->
                        if (dragIndex < 0 || size.width == 0 || size.height == 0) return@detectDragGestures
                        val nx = (change.position.x / size.width).coerceIn(0f, 1f)
                        val ny = (change.position.y / size.height).coerceIn(0f, 1f)
                        magnifierCenter = change.position
                        onCornersChanged(updateHandle(corners, dragIndex, nx, ny, lockRectangle))
                        change.consume()
                    },
                )
            },
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (size.width == 0) return@Canvas
            val pts = listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
            val path = Path().apply {
                pts.forEachIndexed { i, p ->
                    val x = p.x * size.width
                    val y = p.y * size.height
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            drawPath(path, Color(0xFF00897B), style = Stroke(width = 3f))
            pts.forEach { p ->
                drawCircle(Color(0xFF00897B), radius = 14f, center = Offset(p.x * size.width, p.y * size.height))
            }
            val edges = edgeMidpoints(corners, size)
            edges.forEach { e ->
                drawCircle(Color(0xFF00695C), radius = 10f, center = e)
            }
        }
        magnifierCenter?.let { center ->
            Box(
                modifier = Modifier
                    .offset { IntOffset((center.x - 40).roundToInt(), (center.y - 90).roundToInt()) }
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            ) {
                AsyncImage(
                    model = previewPath,
                    contentDescription = "Magnifier",
                    modifier = Modifier.fillMaxSize().graphicsLayer { scaleX = 2.5f; scaleY = 2.5f },
                    contentScale = ContentScale.Crop,
                )
            }
        }
    }
}

@Composable
private fun CleanIntSlider(
    label: String,
    value: Int,
    onUpdate: ((CleanParams) -> CleanParams) -> Unit,
    transform: (CleanParams, Int) -> CleanParams,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(value = value.toFloat(), onValueChange = { v -> onUpdate { transform(it, v.toInt()) } }, valueRange = 0f..100f)
}

@Composable
private fun TransitionIntSlider(
    label: String,
    value: Int,
    onUpdate: ((TransitionParams) -> TransitionParams) -> Unit,
    transform: (TransitionParams, Int) -> TransitionParams,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(value = value.toFloat(), onValueChange = { v -> onUpdate { transform(it, v.toInt()) } }, valueRange = 0f..100f)
}

@Composable
private fun GrayIntSlider(
    label: String,
    value: Int,
    onUpdate: ((GrayParams) -> GrayParams) -> Unit,
    transform: (GrayParams, Int) -> GrayParams,
) {
    Text(label, style = MaterialTheme.typography.labelMedium)
    Slider(value = value.toFloat(), onValueChange = { v -> onUpdate { transform(it, v.toInt()) } }, valueRange = 0f..100f)
}

@Composable
private fun FloatSlider(label: String, value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    Text("$label: ${value.toInt()}", style = MaterialTheme.typography.labelMedium)
    Slider(value = value, onValueChange = onChange, valueRange = min..max)
}

private fun edgeMidpoints(corners: QuadPoints, size: IntSize): List<Offset> {
    val tl = Offset(corners.topLeft.x * size.width, corners.topLeft.y * size.height)
    val tr = Offset(corners.topRight.x * size.width, corners.topRight.y * size.height)
    val br = Offset(corners.bottomRight.x * size.width, corners.bottomRight.y * size.height)
    val bl = Offset(corners.bottomLeft.x * size.width, corners.bottomLeft.y * size.height)
    return listOf(
        Offset((tl.x + tr.x) / 2f, (tl.y + tr.y) / 2f),
        Offset((tr.x + br.x) / 2f, (tr.y + br.y) / 2f),
        Offset((bl.x + br.x) / 2f, (bl.y + br.y) / 2f),
        Offset((tl.x + bl.x) / 2f, (tl.y + bl.y) / 2f),
    )
}

private fun nearestHandleIndex(offset: Offset, corners: QuadPoints, size: IntSize): Int {
    val cornerPts = listOf(corners.topLeft, corners.topRight, corners.bottomRight, corners.bottomLeft)
    var best = 0
    var bestDist = Float.MAX_VALUE
    cornerPts.forEachIndexed { i, p ->
        val d = hypot(offset.x - p.x * size.width, offset.y - p.y * size.height)
        if (d < bestDist) { bestDist = d; best = i }
    }
    edgeMidpoints(corners, size).forEachIndexed { i, e ->
        val d = hypot(offset.x - e.x, offset.y - e.y)
        if (d < bestDist) { bestDist = d; best = 4 + i }
    }
    return best
}

private fun updateHandle(quad: QuadPoints, index: Int, x: Float, y: Float, lockRectangle: Boolean): QuadPoints {
    if (lockRectangle) return updateRectangleHandle(quad.toAxisAlignedRectangle(), index, x, y)
    return when (index) {
        in 0..3 -> updateCorner(quad, index, x, y)
        4 -> quad.copy(topLeft = quad.topLeft.copy(y = y), topRight = quad.topRight.copy(y = y))
        5 -> quad.copy(topRight = quad.topRight.copy(x = x), bottomRight = quad.bottomRight.copy(x = x))
        6 -> quad.copy(bottomLeft = quad.bottomLeft.copy(y = y), bottomRight = quad.bottomRight.copy(y = y))
        7 -> quad.copy(topLeft = quad.topLeft.copy(x = x), bottomLeft = quad.bottomLeft.copy(x = x))
        else -> quad
    }
}

private fun updateRectangleHandle(quad: QuadPoints, index: Int, x: Float, y: Float): QuadPoints {
    val minSize = 0.05f
    var left = quad.topLeft.x
    var top = quad.topLeft.y
    var right = quad.bottomRight.x
    var bottom = quad.bottomRight.y
    when (index) {
        0 -> {
            left = x.coerceIn(0f, right - minSize)
            top = y.coerceIn(0f, bottom - minSize)
        }
        1 -> {
            right = x.coerceIn(left + minSize, 1f)
            top = y.coerceIn(0f, bottom - minSize)
        }
        2 -> {
            right = x.coerceIn(left + minSize, 1f)
            bottom = y.coerceIn(top + minSize, 1f)
        }
        3 -> {
            left = x.coerceIn(0f, right - minSize)
            bottom = y.coerceIn(top + minSize, 1f)
        }
        4 -> top = y.coerceIn(0f, bottom - minSize)
        5 -> right = x.coerceIn(left + minSize, 1f)
        6 -> bottom = y.coerceIn(top + minSize, 1f)
        7 -> left = x.coerceIn(0f, right - minSize)
    }
    return QuadPoints(
        topLeft = PointF(left, top),
        topRight = PointF(right, top),
        bottomRight = PointF(right, bottom),
        bottomLeft = PointF(left, bottom),
    )
}

private fun updateCorner(quad: QuadPoints, index: Int, x: Float, y: Float): QuadPoints = when (index) {
    0 -> quad.copy(topLeft = PointF(x, y))
    1 -> quad.copy(topRight = PointF(x, y))
    2 -> quad.copy(bottomRight = PointF(x, y))
    else -> quad.copy(bottomLeft = PointF(x, y))
}
