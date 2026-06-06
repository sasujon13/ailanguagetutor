package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.model.WordSpan
import com.cheradip.ailanguagetutor.core.pack.LanguagePackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gentle background warm-up: debounced, cancellable, Pro/Plus only.
 * One combined Home AI call + optional local paragraph cache (cache-first).
 */
@Singleton
class AiPrefetchCoordinator @Inject constructor(
    private val planner: GrammarPrefetchPlanner,
    private val aiManager: AIManager,
    private val aiModePrefs: AiModePreferenceRepository,
    private val languagePackRepository: LanguagePackRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var generation = 0
    private var debounceJob: Job? = null

    private val _isWarming = MutableStateFlow(false)
    val isWarming: StateFlow<Boolean> = _isWarming.asStateFlow()

    fun scheduleReaderWarm(
        fullText: String,
        words: List<WordSpan>,
        documentLanguageCode: String,
        grammarDepth: GrammarDepth,
        targetLang: String,
        inputSource: InputSource = InputSource.OCR_SCAN,
        debounceMs: Long = READER_DEBOUNCE_MS,
    ) {
        if (fullText.isBlank()) return
        debounceJob?.cancel()
        val gen = ++generation
        debounceJob = scope.launch {
            delay(debounceMs)
            if (gen != generation) return@launch
            runReaderWarm(fullText, words, documentLanguageCode, grammarDepth, targetLang, inputSource)
        }
    }

    fun schedulePracticeWarm(
        text: String,
        languageCode: String,
        targetLang: String,
        grammarDepth: GrammarDepth,
        debounceMs: Long = PRACTICE_DEBOUNCE_MS,
    ) {
        val trimmed = text.trim()
        if (trimmed.length < PRACTICE_MIN_CHARS) return
        debounceJob?.cancel()
        val gen = ++generation
        debounceJob = scope.launch {
            delay(debounceMs)
            if (gen != generation) return@launch
            runReaderWarm(
                fullText = trimmed,
                words = emptyList(),
                documentLanguageCode = languageCode,
                grammarDepth = grammarDepth,
                targetLang = targetLang,
                inputSource = InputSource.TYPED,
            )
        }
    }

    fun cancel() {
        generation++
        debounceJob?.cancel()
        _isWarming.value = false
    }

    private suspend fun runReaderWarm(
        fullText: String,
        words: List<WordSpan>,
        documentLanguageCode: String,
        grammarDepth: GrammarDepth,
        targetLang: String,
        inputSource: InputSource,
    ) {
        if (checkAppAccess.subscriptionTier() == SubscriptionTier.FREE) return

        val activeCodes = languagePackRepository.observeActive().first()
            .map { it.languageCode.lowercase() }
        val sourceLang = documentLanguageCode.lowercase()
        if (activeCodes.isNotEmpty() && sourceLang !in activeCodes) return

        val targetLangs = if (activeCodes.isEmpty()) {
            listOf(targetLang.lowercase()).filter { it != sourceLang }.ifEmpty { listOf("en") }
        } else {
            activeCodes.filter { it != sourceLang }.distinct().ifEmpty { listOf(targetLang.lowercase()) }
        }

        val grammarTargets = planner.plan(fullText, words, grammarDepth)
        if (grammarTargets.isEmpty()) return

        val chunk = fullText.take(PREFETCH_CHUNK_CHARS)
        val intent = aiModePrefs.current().processingIntent

        _isWarming.value = true
        try {
            aiManager.warmReaderCaches(
                grammarTargets = grammarTargets,
                sourceLang = sourceLang,
                targetLangs = targetLangs,
                targetLang = targetLangs.firstOrNull() ?: targetLang,
                depth = grammarDepth,
                inputSource = inputSource,
                intent = intent,
                explainChunk = if (intent == ProcessingIntent.ANSWER) chunk else null,
                translateChunk = if (intent == ProcessingIntent.TRANSLATION) chunk else null,
            )
        } finally {
            _isWarming.value = false
        }
    }

    companion object {
        const val PREFETCH_CHUNK_CHARS = 480
        const val READER_DEBOUNCE_MS = 900L
        const val PRACTICE_DEBOUNCE_MS = 1_500L
        const val PRACTICE_MIN_CHARS = 60
    }
}
