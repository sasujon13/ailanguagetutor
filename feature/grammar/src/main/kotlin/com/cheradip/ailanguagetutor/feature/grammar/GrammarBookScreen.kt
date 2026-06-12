package com.cheradip.ailanguagetutor.feature.grammar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.cheradip.ailanguagetutor.core.ai.GrammarBookRepository
import com.cheradip.ailanguagetutor.core.database.entity.LanguagePackStateEntity
import com.cheradip.ailanguagetutor.core.model.GrammarBook
import com.cheradip.ailanguagetutor.core.model.GrammarBookChapter
import com.cheradip.ailanguagetutor.core.model.GrammarBookLanguageOption
import com.cheradip.ailanguagetutor.core.model.GrammarBookSection
import com.cheradip.ailanguagetutor.core.model.GrammarSectionEnrichment
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.pack.LanguageCatalogRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import com.cheradip.ailanguagetutor.ui.components.EmptyStateHint
import com.cheradip.ailanguagetutor.ui.components.LanguageFlagBadge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SectionEnrichmentUi(
    val loading: Boolean = false,
    val enrichment: GrammarSectionEnrichment? = null,
    val error: String? = null,
)

data class GrammarBookUiState(
    val languages: List<GrammarBookLanguageOption> = emptyList(),
    val selectedLanguageCode: String? = null,
    val book: GrammarBook? = null,
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val openChapterNumber: Int? = null,
    val sectionEnrichments: Map<String, SectionEnrichmentUi> = emptyMap(),
)

@HiltViewModel
class GrammarBookViewModel @Inject constructor(
    private val grammarBookRepository: GrammarBookRepository,
    private val languagePackRepository: LanguagePackRepository,
    private val catalogRepository: LanguageCatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GrammarBookUiState())
    val uiState: StateFlow<GrammarBookUiState> = _uiState.asStateFlow()

    private var catalog: List<LanguageCatalogEntry> = emptyList()
    private var loadJob: Job? = null
    private var lastLoadedCode: String? = null
    private val enrichJobs = mutableMapOf<String, Job>()

    init {
        viewModelScope.launch { catalog = catalogRepository.getAll() }
        viewModelScope.launch {
            languagePackRepository.observeActive().collect { packs ->
                val options = packs
                    .filter { it.localPath != null }
                    .take(LanguagePackRepository.MAX_ACTIVE_PACKS)
                    .mapNotNull { pack -> toLanguageOption(pack) }
                val current = _uiState.value
                val selected = current.selectedLanguageCode
                    ?.takeIf { code -> options.any { it.code == code } }
                    ?: options.firstOrNull()?.code
                val languageChanged = selected != null && selected != current.selectedLanguageCode
                _uiState.update {
                    it.copy(
                        languages = options,
                        selectedLanguageCode = selected,
                    )
                }
                if (selected != null && (languageChanged || lastLoadedCode != selected)) {
                    loadBook(selected, showSpinner = current.book == null || languageChanged)
                }
            }
        }
    }

    fun onLanguageSelected(code: String) {
        if (code == _uiState.value.selectedLanguageCode) return
        enrichJobs.values.forEach { it.cancel() }
        enrichJobs.clear()
        lastLoadedCode = null
        _uiState.update {
            it.copy(
                selectedLanguageCode = code,
                book = null,
                openChapterNumber = null,
                sectionEnrichments = emptyMap(),
            )
        }
        loadBook(code, showSpinner = true)
    }

    fun refresh() {
        val code = _uiState.value.selectedLanguageCode ?: return
        lastLoadedCode = null
        loadBook(code, showSpinner = true, forceRefresh = true)
    }

    fun openChapter(chapterNumber: Int) {
        _uiState.update { it.copy(openChapterNumber = chapterNumber) }
    }

    fun closeChapter() {
        _uiState.update { it.copy(openChapterNumber = null) }
    }

    fun requestSectionEnrichment(chapterNumber: Int, sectionIndex: Int) {
        val state = _uiState.value
        val book = state.book ?: return
        val chapter = book.chapters.firstOrNull { it.number == chapterNumber } ?: return
        val section = chapter.sections.getOrNull(sectionIndex) ?: return
        val langCode = state.selectedLanguageCode ?: return
        val langName = state.languages.firstOrNull { it.code == langCode }?.name ?: langCode
        val key = enrichmentKey(langCode, chapterNumber, sectionIndex)

        val existing = state.sectionEnrichments[key]
        if (existing?.loading == true || existing?.enrichment?.loaded == true) return
        if (enrichJobs.containsKey(key)) return

        _uiState.update {
            it.copy(
                sectionEnrichments = it.sectionEnrichments + (key to SectionEnrichmentUi(loading = true)),
            )
        }

        enrichJobs[key] = viewModelScope.launch {
            grammarBookRepository.enrichSection(
                languageCode = langCode,
                languageName = langName,
                chapterNumber = chapterNumber,
                chapterTitle = chapter.title,
                section = section,
            ).onSuccess { enrichment ->
                _uiState.update {
                    it.copy(
                        sectionEnrichments = it.sectionEnrichments + (
                            key to SectionEnrichmentUi(
                                loading = false,
                                enrichment = enrichment,
                            )
                            ),
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        sectionEnrichments = it.sectionEnrichments + (
                            key to SectionEnrichmentUi(
                                loading = false,
                                error = err.message,
                            )
                            ),
                    )
                }
            }
            enrichJobs.remove(key)
        }
    }

    private fun enrichmentKey(langCode: String, chapterNumber: Int, sectionIndex: Int) =
        "${langCode.lowercase()}:$chapterNumber:$sectionIndex"

    private fun loadBook(languageCode: String, showSpinner: Boolean, forceRefresh: Boolean = false) {
        loadJob?.cancel()
        val name = _uiState.value.languages.firstOrNull { it.code == languageCode }?.name ?: languageCode
        loadJob = viewModelScope.launch {
            if (showSpinner) {
                _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            }
            grammarBookRepository.loadBook(languageCode, name, forceRefresh)
                .onSuccess { book ->
                    lastLoadedCode = languageCode
                    _uiState.update {
                        it.copy(
                            book = book,
                            isLoading = false,
                            statusMessage = grammarBookStatusMessage(book),
                        )
                    }
                }
                .onFailure { err ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = err.message ?: "Could not load grammar book",
                        )
                    }
                }
        }
    }

    private fun grammarBookStatusMessage(book: GrammarBook): String = when {
        book.cached -> "Loaded from cache"
        book.chapters.size >= 8 && book.chapters.firstOrNull()?.title == "Sounds & spelling" ->
            "Starter outline — tap refresh when online for the full book"
        else -> "Grammar book ready"
    }

    private fun toLanguageOption(pack: LanguagePackStateEntity): GrammarBookLanguageOption? {
        if (pack.localPath.isNullOrBlank()) return null
        val entry = catalog.firstOrNull { it.code.equals(pack.languageCode, ignoreCase = true) }
        return GrammarBookLanguageOption(
            code = pack.languageCode.lowercase(),
            name = entry?.name ?: pack.languageCode.uppercase(),
            flagEmoji = entry?.flagEmoji ?: "🏳️",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarBookScreen(
    modifier: Modifier = Modifier,
    viewModel: GrammarBookViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val openChapter = uiState.openChapterNumber?.let { num ->
        uiState.book?.chapters?.firstOrNull { it.number == num }
    }

    if (openChapter != null) {
        ChapterReaderScreen(
            modifier = modifier,
            chapter = openChapter,
            languageCode = uiState.selectedLanguageCode.orEmpty(),
            sectionEnrichments = uiState.sectionEnrichments,
            onBack = viewModel::closeChapter,
            onSectionVisible = viewModel::requestSectionEnrichment,
        )
        return
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("Grammar", style = MaterialTheme.typography.titleMedium)
                        uiState.book?.title?.let { subtitle ->
                            Text(
                                subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }
                },
                actions = {
                    if (uiState.languages.isNotEmpty()) {
                        IconButton(onClick = viewModel::refresh) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh grammar book")
                        }
                    }
                    Row(
                        modifier = Modifier.padding(end = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        uiState.languages.forEach { lang ->
                            FilterChip(
                                selected = lang.code == uiState.selectedLanguageCode,
                                onClick = { viewModel.onLanguageSelected(lang.code) },
                                label = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        LanguageFlagBadge(flagEmoji = lang.flagEmoji, size = 18.dp)
                                        Text(lang.code.uppercase())
                                    }
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        when {
            uiState.languages.isEmpty() -> {
                EmptyStateHint(
                    modifier = Modifier.padding(padding).padding(24.dp),
                    message = "Download up to 3 languages on the Languages tab, then return here for your grammar book.",
                )
            }
            uiState.isLoading && uiState.book == null -> {
                Box(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            "Loading grammar book…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            else -> {
                BookTableOfContents(
                    padding = padding,
                    book = uiState.book,
                    isLoading = uiState.isLoading,
                    statusMessage = uiState.statusMessage,
                    onChapterClick = viewModel::openChapter,
                    onRefresh = viewModel::refresh,
                )
            }
        }
    }
}

@Composable
private fun BookTableOfContents(
    padding: PaddingValues,
    book: GrammarBook?,
    isLoading: Boolean,
    statusMessage: String?,
    onChapterClick: (Int) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "cover") {
            BookCoverCard(book = book)
        }
        if (isLoading) {
            item(key = "loading") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(
                        " Updating…",
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
        statusMessage?.let { msg ->
            item(key = "status") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        msg,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (msg.contains("Could not", ignoreCase = true) ||
                        msg.contains("Starter outline", ignoreCase = true)
                    ) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Retry",
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        }
        item(key = "toc-header") {
            Text(
                "Contents",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }
        book?.chapters?.let { chapters ->
            items(chapters, key = { "chapter-${it.number}" }) { chapter ->
                TocChapterRow(
                    chapter = chapter,
                    onClick = { onChapterClick(chapter.number) },
                )
            }
        }
        item(key = "end") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                )
                Text(
                    " End of book",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
    }
}

@Composable
private fun BookCoverCard(book: GrammarBook?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                book?.title ?: "Grammar",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            book?.languageName?.takeIf { it.isNotBlank() }?.let { lang ->
                Text(
                    lang,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            book?.chapters?.size?.takeIf { it > 0 }?.let { count ->
                Text(
                    "$count chapters",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Text(
                "Tap a chapter below to start reading",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun TocChapterRow(
    chapter: GrammarBookChapter,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "${chapter.number}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    chapter.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                if (chapter.summary.isNotBlank()) {
                    Text(
                        chapter.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (chapter.sections.isNotEmpty()) {
                    Text(
                        "${chapter.sections.size} sections",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "Open chapter",
                tint = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChapterReaderScreen(
    modifier: Modifier = Modifier,
    chapter: GrammarBookChapter,
    languageCode: String,
    sectionEnrichments: Map<String, SectionEnrichmentUi>,
    onBack: () -> Unit,
    onSectionVisible: (chapterNumber: Int, sectionIndex: Int) -> Unit,
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text(
                            "Chapter ${chapter.number}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            chapter.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to contents")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, top = 0.dp, end = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item(key = "chapter-intro") {
                Column {
                    if (chapter.summary.isNotBlank()) {
                        Text(
                            chapter.summary,
                            style = MaterialTheme.typography.bodyLarge,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
            itemsIndexed(
                items = chapter.sections,
                key = { index, section -> "section-${chapter.number}-$index-${section.heading}" },
            ) { index, section ->
                val enrichKey = "${languageCode.lowercase()}:${chapter.number}:$index"
                val enrichUi = sectionEnrichments[enrichKey]

                LaunchedEffect(chapter.number, index) {
                    onSectionVisible(chapter.number, index)
                    if (index + 1 < chapter.sections.size) {
                        onSectionVisible(chapter.number, index + 1)
                    }
                }

                GrammarSectionReader(
                    section = section,
                    enrichmentUi = enrichUi,
                )
            }
            item(key = "chapter-end") {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "— End of Chapter ${chapter.number} —",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }
    }
}

@Composable
private fun GrammarSectionReader(
    section: GrammarBookSection,
    enrichmentUi: SectionEnrichmentUi?,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (section.heading.isNotBlank()) {
            Text(
                section.heading,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        val bodyText = enrichmentUi?.enrichment?.expandedBody?.takeIf { it.isNotBlank() }
            ?: section.body
        if (bodyText.isNotBlank()) {
            Text(
                bodyText,
                style = MaterialTheme.typography.bodyLarge,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f,
            )
        }

        val examples = section.examples +
            (enrichmentUi?.enrichment?.extraExamples?.filter { it.isNotBlank() } ?: emptyList())
        if (examples.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Examples",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    examples.forEach { example ->
                        Text(
                            "• $example",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(top = 6.dp, start = 4.dp),
                        )
                    }
                }
            }
        }

        enrichmentUi?.enrichment?.learnerTip?.takeIf { it.isNotBlank() }?.let { tip ->
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        "Tip",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Text(
                        tip,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }

        when {
            enrichmentUi?.loading == true -> {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                    Text(
                        "Adding more detail…",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            enrichmentUi?.error != null -> {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    enrichmentUi.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
