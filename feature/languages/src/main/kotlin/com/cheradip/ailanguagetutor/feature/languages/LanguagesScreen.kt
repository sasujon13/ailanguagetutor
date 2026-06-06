package com.cheradip.ailanguagetutor.feature.languages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.database.entity.LanguagePackStateEntity
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.LanguageSearchFilter
import com.cheradip.ailanguagetutor.core.pack.LanguageCatalogRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import com.cheradip.ailanguagetutor.ui.components.CheradipDropdown
import com.cheradip.ailanguagetutor.ui.components.CheradipScrollScreen
import com.cheradip.ailanguagetutor.ui.components.EmptyStateHint
import com.cheradip.ailanguagetutor.ui.components.LanguageFlagBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private enum class PackFilter(val label: String) {
    ALL("All languages"),
    DOWNLOADED("Downloaded"),
    ACTIVE("Active"),
    NOT_DOWNLOADED("Not downloaded"),
}

@HiltViewModel
class LanguagesViewModel @Inject constructor(
    private val catalogRepository: LanguageCatalogRepository,
    private val languagePackRepository: LanguagePackRepository,
) : ViewModel() {
    private val _languages = MutableStateFlow<List<LanguageCatalogEntry>>(emptyList())
    val languages: StateFlow<List<LanguageCatalogEntry>> = _languages.asStateFlow()

    val packs: StateFlow<List<LanguagePackStateEntity>> = languagePackRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        viewModelScope.launch {
            _languages.value = catalogRepository.getAll()
        }
    }

    fun download(code: String) {
        viewModelScope.launch {
            languagePackRepository.downloadAndActivate(code)
                .onSuccess { _message.value = "Pack downloaded for $code" }
                .onFailure { _message.value = it.message }
        }
    }

    fun clearMessage() = _message.update { null }
}

@Composable
fun LanguagesScreen(
    modifier: Modifier = Modifier,
    viewModel: LanguagesViewModel = hiltViewModel(),
) {
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val packs by viewModel.packs.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    var packFilter by remember { mutableStateOf(PackFilter.ALL) }
    val packMap = remember(packs) { packs.associateBy { it.languageCode.lowercase() } }
    val filtered = remember(languages, query, packFilter, packMap) {
        val searched = LanguageSearchFilter.filterAndSort(languages, query)
        when (packFilter) {
            PackFilter.ALL -> searched
            PackFilter.DOWNLOADED -> searched.filter { packMap.containsKey(it.code.lowercase()) }
            PackFilter.ACTIVE -> searched.filter { packMap[it.code.lowercase()]?.isActive == true }
            PackFilter.NOT_DOWNLOADED -> searched.filter { !packMap.containsKey(it.code.lowercase()) }
        }
    }

    CheradipScrollScreen(
        modifier = modifier,
        title = "Languages",
        subtitle = "${packs.count { it.isActive }} active · max 3",
    ) {
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search") },
                placeholder = { Text("Filter 243 languages…") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            )
        }
        item {
            CheradipDropdown(
                label = "Show",
                options = PackFilter.entries.toList(),
                selected = packFilter,
                onSelected = { packFilter = it },
                optionLabel = { it.label },
                leadingIcon = Icons.Default.FilterList,
            )
        }
        item {
            Text(
                "${filtered.size} shown",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            message?.let {
                Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
            }
        }
        if (filtered.isEmpty()) {
            item {
                EmptyStateHint(
                    if (query.isBlank() && languages.isEmpty()) "Loading languages…" else "No languages match your filters",
                    icon = Icons.Default.Search,
                )
            }
        } else {
            items(filtered.size, key = { filtered[it].code }) { index ->
                val lang = filtered[index]
                val pack = packMap[lang.code.lowercase()]
                LanguageRow(
                    lang = lang,
                    pack = pack,
                    onDownload = { viewModel.download(lang.code) },
                )
            }
        }
    }
}

@Composable
private fun LanguageRow(
    lang: LanguageCatalogEntry,
    pack: LanguagePackStateEntity?,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LanguageFlagBadge(flagEmoji = lang.flagEmoji)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(lang.name, style = MaterialTheme.typography.bodyLarge)
            Text(
                lang.nativeName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            pack?.let {
                Text(
                    if (it.isActive) "Downloaded · Active" else "Downloaded",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (pack != null) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = "Downloaded",
                tint = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(28.dp),
            )
        } else {
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download pack", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
