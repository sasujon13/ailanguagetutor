package com.cheradip.ailanguagetutor.feature.journal

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.cheradip.ailanguagetutor.core.locale.appString
import com.cheradip.ailanguagetutor.core.model.Document
import com.cheradip.ailanguagetutor.core.model.LearningActivityFilter
import com.cheradip.ailanguagetutor.core.model.matchesDocument
import com.cheradip.ailanguagetutor.core.model.showsDocuments
import com.cheradip.ailanguagetutor.core.model.toggleHistoryFilter
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
    private val activityFilters = MutableStateFlow(setOf(LearningActivityFilter.ALL))

    val selectedFilters: StateFlow<Set<LearningActivityFilter>> = activityFilters

    val activities: StateFlow<List<LearningActivityEntity>> = combine(
        searchQuery,
        activityFilters,
    ) { query, filters -> query to filters }
        .flatMapLatest { (query, filters) ->
            learningActivityRepository.observeFiltered(filters, query)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setSearch(query: String) {
        searchQuery.value = query
    }

    fun toggleFilter(filter: LearningActivityFilter) {
        activityFilters.value = toggleHistoryFilter(activityFilters.value, filter)
    }

    fun removeFilter(filter: LearningActivityFilter) {
        val updated = activityFilters.value - filter
        activityFilters.value = if (updated.isEmpty()) {
            setOf(LearningActivityFilter.ALL)
        } else {
            updated
        }
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
    val selectedFilters by viewModel.selectedFilters.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val filteredDocuments = remember(documents, selectedFilters, query) {
        documents.filter { doc ->
            selectedFilters.matchesDocument(doc.sourceType) &&
                (query.isBlank() ||
                    doc.title.contains(query, ignoreCase = true) ||
                    doc.languageCode.contains(query, ignoreCase = true))
        }
    }

    val showDocuments = selectedFilters.showsDocuments()
    val activitiesHeader = when {
        selectedFilters == setOf(LearningActivityFilter.SAVED) -> "Saved"
        selectedFilters.size == 1 && LearningActivityFilter.SAVED in selectedFilters -> "Saved"
        else -> "Recent activities"
    }

    CheradipScrollScreen(
        modifier = modifier,
        title = appString("nav_learning"),
        subtitle = "${documents.size} documents · ${activities.size} activities",
    ) {
        item(key = "filters") {
            HistoryFilterBar(
                selectedFilters = selectedFilters,
                onToggleFilter = viewModel::toggleFilter,
                onRemoveFilter = viewModel::removeFilter,
            )
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
                    Text(historySearchPlaceholder(selectedFilters))
                },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
        }
        if (activities.isNotEmpty()) {
            item(key = "header-activities") {
                SectionHeader(title = activitiesHeader)
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
                    message = if (selectedFilters == setOf(LearningActivityFilter.SAVED)) {
                        appString("history_empty_saved")
                    } else {
                        "No activities in this filter yet."
                    },
                    icon = Icons.Default.Refresh,
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

@Composable
private fun HistoryFilterBar(
    selectedFilters: Set<LearningActivityFilter>,
    onToggleFilter: (LearningActivityFilter) -> Unit,
    onRemoveFilter: (LearningActivityFilter) -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val sortedSelected = remember(selectedFilters) {
        LearningActivityFilter.entries.filter { it in selectedFilters }
    }
    val chipScroll = rememberScrollState()

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val inlineChips = maxWidth >= 360.dp

        if (inlineChips) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HistoryFilterDropdown(
                    selectedFilters = selectedFilters,
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    onToggleFilter = onToggleFilter,
                    modifier = Modifier.widthIn(max = 168.dp),
                )
                SelectedFilterChips(
                    filters = sortedSelected,
                    onRemove = onRemoveFilter,
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(chipScroll),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                HistoryFilterDropdown(
                    selectedFilters = selectedFilters,
                    expanded = menuExpanded,
                    onExpandedChange = { menuExpanded = it },
                    onToggleFilter = onToggleFilter,
                    modifier = Modifier.fillMaxWidth(),
                )
                SelectedFilterChips(
                    filters = sortedSelected,
                    onRemove = onRemoveFilter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(chipScroll),
                )
            }
        }
    }
}

@Composable
private fun HistoryFilterDropdown(
    selectedFilters: Set<LearningActivityFilter>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onToggleFilter: (LearningActivityFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { onExpandedChange(true) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = historyFilterDropdownLabel(selectedFilters),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Default.ArrowDropDown, contentDescription = "Filter options")
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            LearningActivityFilter.entries.forEach { filter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleFilter(filter) }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = filter in selectedFilters,
                        onCheckedChange = { onToggleFilter(filter) },
                    )
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SelectedFilterChips(
    filters: List<LearningActivityFilter>,
    onRemove: (LearningActivityFilter) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (filters.isEmpty()) return
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        filters.forEach { filter ->
            AssistChip(
                onClick = { onRemove(filter) },
                label = {
                    Text(
                        text = filter.label,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                trailingIcon = {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove ${filter.label}",
                        modifier = Modifier.size(14.dp),
                    )
                },
            )
        }
    }
}

private fun historyFilterDropdownLabel(selectedFilters: Set<LearningActivityFilter>): String {
    if (selectedFilters.isEmpty() || LearningActivityFilter.ALL in selectedFilters) return "All filters"
    return when (selectedFilters.size) {
        1 -> selectedFilters.first().label
        else -> "${selectedFilters.size} filters"
    }
}

private fun historySearchPlaceholder(selectedFilters: Set<LearningActivityFilter>): String {
    if (selectedFilters.isEmpty() || LearningActivityFilter.ALL in selectedFilters) {
        return "Search activities…"
    }
    val labels = LearningActivityFilter.entries
        .filter { it in selectedFilters }
        .joinToString(", ") { it.label.lowercase() }
    return "Search in $labels…"
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
                    contentDescription = appString("open_in_learning"),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

private fun activityIcon(activityType: String) = when {
    activityType.startsWith("practice") -> Icons.AutoMirrored.Filled.MenuBook
    activityType == "grammar" -> Icons.AutoMirrored.Filled.MenuBook
    activityType == "read" -> Icons.AutoMirrored.Filled.MenuBook
    activityType == "scan" || activityType == "import" -> Icons.Default.Description
    else -> Icons.Default.Refresh
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
