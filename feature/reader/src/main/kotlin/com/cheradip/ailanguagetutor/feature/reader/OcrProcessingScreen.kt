package com.cheradip.ailanguagetutor.feature.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.ui.components.CheradipTopBar

@Composable
fun OcrProcessingScreen(
    documentId: Long,
    onComplete: (Long) -> Unit,
    viewModel: OcrProcessingViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(documentId) { viewModel.process(documentId) }
    LaunchedEffect(uiState.isComplete) {
        if (uiState.isComplete) onComplete(documentId)
    }

    Scaffold(
        topBar = {
            CheradipTopBar(
                title = "Processing scan",
                subtitle = "OCR + AI structure",
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Default.DocumentScanner,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.height(16.dp))
            CircularProgressIndicator()
            Text(
                text = stepLabel(uiState.currentStep),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
            if (uiState.totalPages > 0) {
                Text(
                    text = "Page ${uiState.processedPages} of ${uiState.totalPages}",
                    modifier = Modifier.padding(top = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                LinearProgressIndicator(
                    progress = {
                        uiState.processedPages.toFloat() / uiState.totalPages.toFloat()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                )
            }
            PipelineSteps(current = uiState.currentStep)
            uiState.error?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

private fun stepLabel(step: OcrPipelineStep): String = when (step) {
    OcrPipelineStep.IDLE -> "Starting…"
    OcrPipelineStep.PREPROCESS -> "Preparing pages…"
    OcrPipelineStep.RECOGNIZE -> "Recognizing text…"
    OcrPipelineStep.CLASSIFY -> "Detecting content type…"
    OcrPipelineStep.STRUCTURE -> "Structuring with AI…"
    OcrPipelineStep.SAVE -> "Saving…"
}

@Composable
private fun PipelineSteps(current: OcrPipelineStep) {
    Column(modifier = Modifier.padding(top = 24.dp), horizontalAlignment = Alignment.Start) {
        OcrStep("Preprocess image", Icons.Default.DocumentScanner, current.ordinal >= OcrPipelineStep.PREPROCESS.ordinal)
        OcrStep("Recognize text (ML Kit)", Icons.Default.TextFields, current.ordinal >= OcrPipelineStep.RECOGNIZE.ordinal)
        OcrStep("Detect math / code / flowchart", Icons.Default.Category, current.ordinal >= OcrPipelineStep.CLASSIFY.ordinal)
        OcrStep("Structure & fix OCR (cloud AI for math/code)", Icons.Default.AutoAwesome, current.ordinal >= OcrPipelineStep.STRUCTURE.ordinal)
        OcrStep("Save to reader", Icons.Default.Save, current.ordinal >= OcrPipelineStep.SAVE.ordinal)
    }
}

@Composable
private fun OcrStep(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    active: Boolean,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        )
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 8.dp),
            color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Renders structured OCR text with code fences and headings. */
@Composable
fun StructuredReaderText(
    text: String,
    useMonospace: Boolean,
    modifier: Modifier = Modifier,
) {
    val blocks = rememberStructuredBlocks(text)
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is StructuredBlock.Code -> {
                    Text(
                        text = block.language?.uppercase() ?: "CODE",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    Text(
                        text = block.content,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(12.dp),
                    )
                }
                is StructuredBlock.Heading -> {
                    Text(
                        text = block.title,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                is StructuredBlock.Paragraph -> {
                    Text(
                        text = block.content,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontFamily = if (useMonospace) FontFamily.Monospace else FontFamily.Default,
                        ),
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private sealed class StructuredBlock {
    data class Heading(val title: String) : StructuredBlock()
    data class Paragraph(val content: String) : StructuredBlock()
    data class Code(val content: String, val language: String?) : StructuredBlock()
}

private fun rememberStructuredBlocks(text: String): List<StructuredBlock> = parseStructuredBlocks(text)

private fun parseStructuredBlocks(text: String): List<StructuredBlock> {
    if (!text.contains("```")) {
        return text.split(Regex("\n{2,}")).mapNotNull { p ->
            val t = p.trim()
            if (t.isBlank()) null
            else if (t.startsWith("## ")) StructuredBlock.Heading(t.removePrefix("## ").trim())
            else StructuredBlock.Paragraph(t)
        }
    }
    val blocks = mutableListOf<StructuredBlock>()
    var i = 0
    while (i < text.length) {
        val fenceStart = text.indexOf("```", i)
        if (fenceStart < 0) {
            appendProse(text.substring(i), blocks)
            break
        }
        appendProse(text.substring(i, fenceStart), blocks)
        val langEnd = text.indexOf('\n', fenceStart + 3)
        val lang = if (langEnd > fenceStart) {
            text.substring(fenceStart + 3, langEnd).trim().ifBlank { null }
        } else null
        val contentStart = if (langEnd > fenceStart) langEnd + 1 else fenceStart + 3
        val fenceEnd = text.indexOf("```", contentStart)
        if (fenceEnd < 0) {
            blocks.add(StructuredBlock.Paragraph(text.substring(fenceStart)))
            break
        }
        blocks.add(StructuredBlock.Code(text.substring(contentStart, fenceEnd).trim(), lang))
        i = fenceEnd + 3
    }
    return blocks.ifEmpty { listOf(StructuredBlock.Paragraph(text)) }
}

private fun appendProse(chunk: String, blocks: MutableList<StructuredBlock>) {
    chunk.split(Regex("\n{2,}")).forEach { part ->
        val t = part.trim()
        if (t.isBlank()) return@forEach
        if (t.startsWith("## ")) {
            blocks.add(StructuredBlock.Heading(t.removePrefix("## ").trim()))
        } else {
            blocks.add(StructuredBlock.Paragraph(t))
        }
    }
}
