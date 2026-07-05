package com.cheradip.ailanguagetutor.feature.scanner

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cheradip.ailanguagetutor.core.image.ExportCompression
import com.cheradip.ailanguagetutor.core.image.ExportFormat
import com.cheradip.ailanguagetutor.core.image.ExportMargins
import com.cheradip.ailanguagetutor.core.image.ExportOptions
import com.cheradip.ailanguagetutor.core.image.ExportOrientation
import com.cheradip.ailanguagetutor.core.image.ExportPageSize
import com.cheradip.ailanguagetutor.core.image.ExportQuality
import com.cheradip.ailanguagetutor.core.image.WatermarkMode
import java.io.File

@Composable
fun ScannerPageThumbnailStrip(
    pages: List<ScannerPageItem>,
    selectedPageId: Long?,
    thumbnailPathFor: (ScannerPageItem) -> String,
    onSelectPage: (Long) -> Unit,
    onAddPage: (() -> Unit)? = null,
    addPageEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
            .horizontalScroll(scrollState)
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        pages.forEach { page ->
            val selected = page.id == selectedPageId
            val borderColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
            AsyncImage(
                model = thumbnailPathFor(page),
                contentDescription = "Page ${page.pageIndex + 1}",
                modifier = Modifier
                    .width(48.dp)
                    .height(56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(2.dp, borderColor, RoundedCornerShape(6.dp))
                    .clickable { onSelectPage(page.id) },
                contentScale = ContentScale.Crop,
            )
        }
        if (onAddPage != null) {
            AddPageThumbnail(
                onClick = onAddPage,
                enabled = addPageEnabled,
            )
        }
    }
}

@Composable
private fun AddPageThumbnail(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderColor = if (enabled) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Box(
        modifier = modifier
            .width(48.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .border(2.dp, borderColor, RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Add page",
                modifier = Modifier.size(22.dp),
                tint = contentColor,
            )
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
            )
        }
    }
}

/** Read-only page preview after ML Kit capture — no in-app editing stage. */
@Composable
fun ScannerReadOnlyPreview(
    imagePath: String?,
    cacheKey: String,
    isLoading: Boolean,
    onDeletePage: () -> Unit,
    onRescanPage: (() -> Unit)? = null,
    rescanEnabled: Boolean = true,
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
        if (imagePath != null) {
            val context = LocalContext.current
            val model = remember(imagePath, cacheKey) {
                ImageRequest.Builder(context)
                    .data(File(imagePath))
                    .memoryCacheKey(cacheKey)
                    .diskCacheKey(cacheKey)
                    .build()
            }
            AsyncImage(
                model = model,
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
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(36.dp))
        }
        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onRescanPage != null) {
                IconButton(
                    onClick = onRescanPage,
                    enabled = rescanEnabled,
                    modifier = Modifier.background(
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.92f),
                        CircleShape,
                    ),
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Rescan page",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            IconButton(
                onClick = onDeletePage,
                modifier = Modifier.background(
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.92f),
                    CircleShape,
                ),
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete page",
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
fun AppAutoEnhanceOnScanToggle(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text("App auto-enhance", style = MaterialTheme.typography.bodyMedium)
            Text(
                if (enabled) {
                    "On — crop, perspective & document filter after each capture"
                } else {
                    "Off — keep ML Kit image as captured"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = enabled, onCheckedChange = onEnabledChange)
    }
}

@Composable
fun ScanExportOptionsPanel(
    options: ExportOptions,
    onUpdate: ((ExportOptions) -> ExportOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = options.documentName,
            onValueChange = { name -> onUpdate { it.copy(documentName = name) } },
            label = { Text("Document name (optional)") },
            modifier = Modifier.fillMaxWidth(),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            EnumChips("Format", ExportFormat.entries, options.format) { f -> onUpdate { it.copy(format = f) } }
            EnumChips("Quality", ExportQuality.entries, options.quality) { q -> onUpdate { it.copy(quality = q) } }
            EnumChips("Compression", ExportCompression.entries, options.compression) { c ->
                onUpdate { it.copy(compression = c) }
            }
            EnumChips("Page size", ExportPageSize.entries, options.pageSize) { s -> onUpdate { it.copy(pageSize = s) } }
            EnumChips("Margins", ExportMargins.entries, options.margins) { m -> onUpdate { it.copy(margins = m) } }
            EnumChips("Orientation", ExportOrientation.entries, options.orientation) { o ->
                onUpdate { it.copy(orientation = o) }
            }
            EnumChips("Watermark", WatermarkMode.entries, options.watermarkMode) { mode ->
                onUpdate { it.copy(watermarkMode = mode, useTimestampWatermark = mode == WatermarkMode.TIMESTAMP) }
            }
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
        OutlinedTextField(
            value = options.author,
            onValueChange = { v -> onUpdate { it.copy(author = v) } },
            label = { Text("Author") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = options.title,
            onValueChange = { v -> onUpdate { it.copy(title = v) } },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = options.subject,
            onValueChange = { v -> onUpdate { it.copy(subject = v) } },
            label = { Text("Subject") },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = options.keywords,
            onValueChange = { v -> onUpdate { it.copy(keywords = v) } },
            label = { Text("Keywords") },
            modifier = Modifier.fillMaxWidth(),
        )
    }
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
                ScanExportOptionsPanel(options = options, onUpdate = onUpdate)
            }
        },
        confirmButton = {
            Button(onClick = onExport) {
                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.padding(end = 6.dp))
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun <T : Enum<T>> EnumChips(
    label: String,
    values: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    ExportLabelOptionsRow(label = label) {
        values.forEach { value ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = {
                    Text(
                        text = value.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        }
    }
}

@Composable
private fun ExportLabelOptionsRow(
    label: String,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(88.dp),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

@Composable
fun ScanExportConflictDialog(
    existingNames: List<String>,
    renameHint: String,
    onReplace: () -> Unit,
    onRename: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("File already exists") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("These files would be overwritten:")
                existingNames.forEach { name ->
                    Text("• $name", style = MaterialTheme.typography.bodyMedium)
                }
                if (renameHint.isNotBlank()) {
                    Text(
                        "Save with a new name instead:",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(renameHint, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(onClick = onRename) { Text("Save as new name") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                TextButton(onClick = onReplace) { Text("Replace") }
            }
        },
    )
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
