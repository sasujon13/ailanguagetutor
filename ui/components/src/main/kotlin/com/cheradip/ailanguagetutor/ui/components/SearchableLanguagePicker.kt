package com.cheradip.ailanguagetutor.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
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
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.LanguageSearchFilter

@Composable
fun SearchableLanguageMultiSelect(
    languages: List<LanguageCatalogEntry>,
    selectedCodes: Set<String>,
    onToggle: (LanguageCatalogEntry) -> Unit,
    modifier: Modifier = Modifier,
    maxSelection: Int = 3,
    searchLabel: String = "Search languages",
    searchPlaceholder: String = "Type to filter (e.g. al, spanish, fr)…",
) {
    var query by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(languages, query) {
        LanguageSearchFilter.filterAndSort(languages, query)
    }
    val selectedEntries = remember(languages, selectedCodes) {
        languages.filter { it.code in selectedCodes }
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (selectedEntries.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                selectedEntries.forEach { lang ->
                    FilterChip(
                        selected = true,
                        onClick = { onToggle(lang) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LanguageFlagBadge(flagEmoji = lang.flagEmoji)
                                Text(lang.name, modifier = Modifier.padding(start = 4.dp))
                            }
                        },
                    )
                }
            }
            Text(
                "${selectedCodes.size}/$maxSelection selected",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(searchLabel) },
            placeholder = { Text(searchPlaceholder) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            },
        )

        Text(
            "${filtered.size} of ${languages.size} languages",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (expanded) {
            LanguageDropdownList(
                languages = filtered,
                selectedCodes = selectedCodes,
                onItemClick = { lang ->
                    onToggle(lang)
                },
                showSelectionCheck = true,
                emptyMessage = if (query.isBlank()) "Type to search 243 languages" else "No languages match \"$query\"",
            )
        }
    }
}

@Composable
fun SearchableLanguageDropdown(
    languages: List<LanguageCatalogEntry>,
    selected: LanguageCatalogEntry?,
    onSelected: (LanguageCatalogEntry) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Language",
    searchPlaceholder: String = "Search…",
) {
    var query by remember(selected) {
        mutableStateOf(selected?.name.orEmpty())
    }
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(languages, query) {
        LanguageSearchFilter.filterAndSort(languages, query)
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                expanded = true
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            placeholder = { Text(searchPlaceholder) },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.clickable { expanded = !expanded },
                )
            },
        )

        if (expanded) {
            LanguageDropdownList(
                languages = filtered,
                selectedCodes = setOfNotNull(selected?.code),
                onItemClick = { lang ->
                    onSelected(lang)
                    query = lang.name
                    expanded = false
                },
                showSelectionCheck = true,
                emptyMessage = if (query.isBlank()) "Type to search languages" else "No matches",
            )
        }
    }
}

@Composable
private fun LanguageDropdownList(
    languages: List<LanguageCatalogEntry>,
    selectedCodes: Set<String>,
    onItemClick: (LanguageCatalogEntry) -> Unit,
    showSelectionCheck: Boolean,
    emptyMessage: String,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 280.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        if (languages.isEmpty()) {
            Text(
                emptyMessage,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                items(languages, key = { it.code }) { lang ->
                    val isSelected = lang.code in selectedCodes
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onItemClick(lang) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LanguageFlagBadge(flagEmoji = lang.flagEmoji)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 10.dp),
                        ) {
                            Text(lang.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${lang.nativeName} · ${lang.code}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (showSelectionCheck && isSelected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
