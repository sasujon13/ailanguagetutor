package com.cheradip.ailanguagetutor.core.locale

import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.common.AppLocaleHolder
import com.cheradip.ailanguagetutor.core.pack.LanguageCatalogRepository
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

data class AppLocaleUiState(
    val locale: AppLocaleState = AppLocaleState(),
    val strings: Map<String, String> = AppStrings.english,
    val isUpdating: Boolean = false,
    val updateProgress: Float = 0f,
    val snackbarMessage: String? = null,
    val catalog: List<LanguageCatalogEntry> = emptyList(),
    /** App UI may only use one of the up-to-3 active study languages. */
    val allowedLanguages: List<LanguageCatalogEntry> = emptyList(),
)

@Singleton
class AppLocaleManager @Inject constructor(
    private val repository: AppLocaleRepository,
    private val translator: AppLocaleTranslator,
    private val catalogRepository: LanguageCatalogRepository,
    private val languagePackRepository: LanguagePackRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _uiState = MutableStateFlow(AppLocaleUiState())
    val uiState: StateFlow<AppLocaleUiState> = _uiState.asStateFlow()
    private val _catalog = MutableStateFlow<List<LanguageCatalogEntry>>(emptyList())
    private var activeLanguageCodes: Set<String> = emptySet()

    init {
        scope.launch {
            repository.localeState.collect { locale ->
                _uiState.update { it.copy(locale = locale) }
            }
        }
        scope.launch {
            _catalog.value = catalogRepository.getAll()
            _uiState.update { it.copy(catalog = _catalog.value) }
            refreshAllowedLanguages()
            val lang = repository.currentLanguageCode()
            AppLocaleHolder.languageCode = lang
            val strings = repository.translatedStrings(lang)
            _uiState.update { it.copy(strings = strings) }
            if (activeLanguageCodes.isNotEmpty()) {
                ensureAppLanguageInActive(activeLanguageCodes)
            }
        }
        scope.launch {
            languagePackRepository.observeActiveLanguageCodes().collect { codes ->
                activeLanguageCodes = codes.toSet()
                refreshAllowedLanguages()
                if (codes.isNotEmpty()) {
                    ensureAppLanguageInActive(activeLanguageCodes)
                }
            }
        }
    }

    private fun refreshAllowedLanguages() {
        val allowed = if (activeLanguageCodes.isEmpty()) {
            emptyList()
        } else {
            _catalog.value.filter { it.code.lowercase() in activeLanguageCodes }
        }
        _uiState.update { it.copy(allowedLanguages = allowed) }
    }

    private suspend fun ensureAppLanguageInActive(activeCodes: Set<String>) {
        val current = repository.currentLanguageCode().lowercase()
        if (current in activeCodes) return
        val fallbackCode = activeCodes.firstOrNull() ?: return
        val entry = _catalog.value.firstOrNull { it.code.equals(fallbackCode, ignoreCase = true) } ?: return
        applyLanguageInternal(entry)
    }

    private fun isAllowedAppLanguage(code: String): Boolean {
        if (activeLanguageCodes.isEmpty()) return true
        return code.lowercase() in activeLanguageCodes
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    fun selectLanguage(entry: LanguageCatalogEntry, animated: Boolean = true) {
        if (!isAllowedAppLanguage(entry.code)) return
        scope.launch {
            val current = repository.current()
            val newLang = entry.code.lowercase()
            if (newLang == current.languageCode.lowercase()) return@launch

            if (animated) {
                _uiState.update {
                    it.copy(isUpdating = true, updateProgress = 0f, snackbarMessage = AppStrings.english["updating_language"])
                }
                val durationMs = 2500L
                val steps = 50
                repeat(steps) { step ->
                    delay(durationMs / steps)
                    _uiState.update { it.copy(updateProgress = (step + 1) / steps.toFloat()) }
                }
            }

            applyLanguageInternal(entry)
            _uiState.update {
                it.copy(
                    isUpdating = false,
                    updateProgress = 1f,
                    snackbarMessage = if (animated) null else it.snackbarMessage,
                )
            }
        }
    }

    /** Awaitable apply for onboarding (no progress ring). */
    suspend fun applyLanguage(entry: LanguageCatalogEntry) {
        if (!isAllowedAppLanguage(entry.code)) return
        applyLanguageInternal(entry)
        _uiState.update { it.copy(isUpdating = false, updateProgress = 1f) }
    }

    private suspend fun applyLanguageInternal(entry: LanguageCatalogEntry) {
        val current = repository.current()
        val newLang = entry.code.lowercase()
        if (newLang == current.languageCode.lowercase()) return

        if (newLang == AppStrings.DEFAULT_LANG) {
            repository.saveLocale(entry, AppStrings.DEFAULT_REGION)
            AppLocaleHolder.languageCode = AppStrings.DEFAULT_LANG
            _uiState.update { it.copy(strings = AppStrings.english, isUpdating = false) }
            return
        }

        repository.saveLocale(entry, entry.flagCountry ?: "")
        AppLocaleHolder.languageCode = newLang

        val cached = repository.translatedStrings(newLang)
        val hasCache = cached.keys.containsAll(AppStrings.english.keys) &&
            cached.any { (k, v) -> AppStrings.english[k] != v }
        val translated = if (hasCache) {
            cached
        } else {
            translator.translateUiStrings(newLang).also {
                repository.saveTranslatedStrings(newLang, it)
            }
        }
        _uiState.update { it.copy(strings = translated) }
    }

    fun t(key: String): String = AppStrings.text(key, _uiState.value.strings)

    fun t(key: String, vararg args: Any): String = AppStrings.format(key, _uiState.value.strings, *args)
}
