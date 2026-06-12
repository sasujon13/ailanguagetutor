package com.cheradip.ailanguagetutor.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DocumentScanner
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
                subtitle = "Running OCR on your pages",
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
            text = "Running OCR…",
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
        RowSteps()
        uiState.error?.let {
            Text(text = it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
        }
    }
    }
}

@Composable
private fun RowSteps() {
    Column(modifier = Modifier.padding(top = 24.dp), horizontalAlignment = Alignment.Start) {
        OcrStep(label = "Preprocess image", active = true)
        OcrStep(label = "Recognize text", active = true)
        OcrStep(label = "Build word map", active = true)
    }
}

@Composable
private fun OcrStep(label: String, active: Boolean) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        Icon(
            Icons.Default.TextFields,
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
