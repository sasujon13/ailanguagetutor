package com.cheradip.ailanguagetutor.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cheradip.ailanguagetutor.core.database.entity.LearningActivityEntity
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivityRepository
import com.cheradip.ailanguagetutor.core.model.Document
import com.cheradip.ailanguagetutor.core.model.LearningActivityFilter
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.EmptyStateHint
import com.cheradip.ailanguagetutor.ui.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    private val activityFilter = MutableStateFlow(LearningActivityFilter.ALL)

    val activities: StateFlow<List<LearningActivityEntity>> = combine(
        searchQuery,
        activityFilter,
    ) { query, filter -> query to filter }
        .flatMapLatest { (query, filter) ->
            learningActivityRepository.observeFiltered(filter, query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearch(query: String) {
        searchQuery.value = query
    }

    fun setFilter(filter: LearningActivityFilter) {
        activityFilter.value = filter
    }
}

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    onOpenDocument: (Long) -> Unit,
    onOpenPracticeActivity: (Long) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val documents by viewModel.documents.collectAsStateWithLifecycle(initialValue = emptyList())
    val activities by viewModel.activities.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(LearningActivityFilter.ALL) }

    val filteredDocuments = remember(documents, selectedFilter, query) {
        documents.filter { doc ->
            val matchesFilter = when (selectedFilter) {
                LearningActivityFilter.ALL -> true
                LearningActivityFilter.SCANS -> doc.sourceType == "scan"
                LearningActivityFilter.UPLOADS -> doc.sourceType == "import"
                LearningActivityFilter.READ -> true
                else -> false
            }
            val matchesQuery = query.isBlank() ||
                doc.title.contains(query, ignoreCase = true) ||
                doc.languageCode.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }
    }

    val showDocuments = selectedFilter in setOf(
        LearningActivityFilter.ALL,
        LearningActivityFilter.SCANS,
        LearningActivityFilter.UPLOADS,
        LearningActivityFilter.READ,
    )

    CheradipScrollScreen(
        modifier = modifier,
        title = "My Learning",
        subtitle = "${documents.size} documents · ${activities.size} activities",
    ) {
        item(key = "filters") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LearningActivityFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = {
                            selectedFilter = filter
                            viewModel.setFilter(filter)
                        },
                        label = { Text(filter.label) },
                    )
                }
            }
        }
        item(key = "search") {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setSearch(it)
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        if (selectedFilter == LearningActivityFilter.ALL) {
                            "Search activities…"
                        } else {
                            "Search in ${selectedFilter.label.lowercase()}…"
                        },
                    )
                },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
        }
        if (activities.isNotEmpty()) {
            item(key = "header-activities") {
                SectionHeader(title = if (selectedFilter == LearningActivityFilter.SAVED) "Saved" else "Recent activities")
            }
            items(activities, key = { "activity-${it.id}" }) { activity ->
                ActivityCard(
                    activity = activity,
                    onOpen = { openLearningActivity(activity, onOpenDocument, onOpenPracticeActivity) },
                    onReload = { openLearningActivity(activity, onOpenDocument, onOpenPracticeActivity) },
                )
            }
        } else {
            item(key = "empty-activities") {
                EmptyStateHint(
                    message = when (selectedFilter) {
                        LearningActivityFilter.SAVED -> "No saved items yet. Use Save on Practice or Grammar."
                        else -> "No activities in this filter yet."
                    },
                    icon = Icons.Default.History,
                )
            }
        }
        if (showDocuments) {
            item(key = "header-documents") { SectionHeader(title = "Documents") }
            if (filteredDocuments.isEmpty()) {
                item(key = "empty-documents") {
                    EmptyStateHint("No documents in this filter yet.", icon = Icons.Default.Description)
                }
            } else {
                items(filteredDocuments, key = { "document-${it.id}" }) { doc ->
                    DocumentCard(doc = doc, onClick = { onOpenDocument(doc.id) })
                }
            }
        }
    }
}

private fun openLearningActivity(
    activity: LearningActivityEntity,
    onOpenDocument: (Long) -> Unit,
    onOpenPracticeActivity: (Long) -> Unit,
) {
    val documentId = activity.documentId
    when {
        activity.activityType == "grammar" ||
            activity.activityType.startsWith("practice") ||
            !activity.inputText.isNullOrBlank() ||
            !activity.outputText.isNullOrBlank() -> onOpenPracticeActivity(activity.id)
        documentId != null -> onOpenDocument(documentId)
        else -> onOpenPracticeActivity(activity.id)
    }
}

@Composable
private fun ActivityCard(
    activity: LearningActivityEntity,
    onOpen: () -> Unit,
    onReload: () -> Unit,
) {
    val preview = activity.inputText?.takeIf { it.isNotBlank() }
        ?: activity.summary
        ?: activity.outputText
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = activityIcon(activity.activityType),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        activity.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (activity.isSaved) {
                        Icon(
                            Icons.Default.Bookmark,
                            contentDescription = "Saved",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(18.dp),
                        )
                    }
                }
                preview?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                activity.outputText?.takeIf { activity.inputText != null }?.let { reply ->
                    Text(
                        reply,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            IconButton(onClick = onReload) {
                Icon(
                    Icons.Default.Replay,
                    contentDescription = "Open in Practice",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun activityIcon(activityType: String) = when {
    activityType.startsWith("practice") -> Icons.Default.History
    activityType == "grammar" -> Icons.AutoMirrored.Filled.MenuBook
    activityType == "read" -> Icons.AutoMirrored.Filled.MenuBook
    activityType == "scan" || activityType == "import" -> Icons.Default.Description
    else -> Icons.Default.History
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
                "${doc.pageCount} page(s) · ${doc.languageCode.uppercase()} · ${doc.sourceType}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
