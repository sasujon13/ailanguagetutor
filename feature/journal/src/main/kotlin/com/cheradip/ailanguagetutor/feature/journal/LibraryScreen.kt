package com.cheradip.ailanguagetutor.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.model.Document
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.EmptyStateHint
import com.cheradip.ailanguagetutor.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    documentRepository: DocumentRepository,
    private val learningActivityRepository: LearningActivityRepository,
) : ViewModel() {
    val documents = documentRepository.observeDocuments()
    private val searchQuery = MutableStateFlow("")

    val activities: StateFlow<List<com.cheradip.ailanguagetutor.core.database.entity.LearningActivityEntity>> =
        searchQuery.flatMapLatest { q ->
            if (q.isBlank()) learningActivityRepository.observeAll()
            else learningActivityRepository.search(q)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearch(query: String) {
        searchQuery.value = query
    }
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onOpenDocument: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle(initialValue = emptyList())
    val activities by viewModel.activities.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }

    CheradipScrollScreen(
        modifier = modifier,
        title = "My Learning",
        subtitle = "${documents.size} documents · ${activities.size} activities",
    ) {
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setSearch(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search activities…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
        }
        if (activities.isNotEmpty()) {
            item(key = "header-activities") { SectionHeader(title = "Recent activities") }
            items(activities, key = { "activity-${it.id}" }) { activity ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text(activity.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                        activity.summary?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, maxLines = 2, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        item(key = "header-documents") { SectionHeader(title = "Documents") }
        if (documents.isEmpty()) {
            item(key = "empty-documents") {
                EmptyStateHint("No documents yet. Scan or import from Home.", icon = Icons.Default.Description)
            }
        } else {
            items(documents, key = { "document-${it.id}" }) { doc ->
                DocumentCard(doc = doc, onClick = { onOpenDocument(doc.id) })
            }
        }
    }
}

@Composable
private fun DocumentCard(doc: Document, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(doc.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            Text(
                "${doc.pageCount} page(s) · ${doc.languageCode.uppercase()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
