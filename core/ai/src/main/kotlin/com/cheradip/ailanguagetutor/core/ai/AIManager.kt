package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.database.dao.AiCacheDao
import com.cheradip.ailanguagetutor.core.database.entity.AiCacheEntity
import com.cheradip.ailanguagetutor.core.device.GuestAiUsageRepository
import com.cheradip.ailanguagetutor.core.model.AiBackend
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.GrammarPrefetchTarget
import com.cheradip.ailanguagetutor.core.model.GuestAiLimitReachedException
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.ProcessingIntent
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.network.AiActivityMetadataRequest
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarResultItem
import com.cheradip.ailanguagetutor.core.network.AiltAiService
import com.cheradip.ailanguagetutor.core.network.AiParagraphRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

data class ActivityMetadata(
    val title: String,
    val summary: String?,
    val tags: List<String>,
    val providerUsed: String? = null,
)

/**
 * Batch-only AI. Primary: home PC (LOCAL_HOME). If response takes >= 30s → free cloud APIs.
 */
@Singleton
class AIManager @Inject constructor(
    private val aiService: AiltAiService,
    private val homeAiService: HomeAiService,
    private val homeAiSettings: HomeAiSettingsRepository,
    private val aiModePrefs: AiModePreferenceRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
    private val aiCacheDao: AiCacheDao,
    private val aiProviderRepository: AiProviderRepository,
    private val appConfig: AppConfig,
    private val guestAiUsageRepository: GuestAiUsageRepository,
    private val englishPivotAi: EnglishPivotAiCoordinator,
) {
    private var lastBackend: AiBackend = AiBackend.CLOUD_POOL

    fun lastBackendUsed(): AiBackend = lastBackend

    suspend fun generateActivityMetadata(text: String, languageCode: String): ActivityMetadata =
        withContext(Dispatchers.IO) {
            val key = "meta:${languageCode}:${text.hashCode()}"
            aiCacheDao.get(key)?.let {
                return@withContext ActivityMetadata(
                    title = text.take(48).trim(),
                    summary = it.responseJson,
                    tags = emptyList(),
                )
            }
            runCatching {
                guestAiUsageRepository.ensureGuestCanUseAi()
                aiService.activityMetadata(AiActivityMetadataRequest(text, languageCode))
            }.fold(
                onSuccess = { resp ->
                    guestAiUsageRepository.recordGuestAiUsage()
                    aiProviderRepository.recordProviderUsed(resp.providerUsed)
                    lastBackend = AiBackend.CLOUD_POOL
                    aiCacheDao.put(AiCacheEntity(key, resp.summary ?: resp.title, System.currentTimeMillis()))
                    ActivityMetadata(resp.title, resp.summary, resp.tags, resp.providerUsed)
                },
                onFailure = { e ->
                    if (e is GuestAiLimitReachedException) throw e
                    ActivityMetadata(
                        title = text.lines().firstOrNull()?.take(48)?.trim() ?: "Activity",
                        summary = text.take(200),
                        tags = listOf("offline"),
                    )
                },
            )
        }

    suspend fun explainParagraph(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource = InputSource.TYPED,
    ): String = processParagraph(
        paragraph = paragraph,
        sourceLang = sourceLang,
        targetLang = targetLang,
        inputSource = inputSource,
        intent = ProcessingIntent.ANSWER,
    )

    suspend fun translateParagraph(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource = InputSource.TYPED,
    ): String = processParagraph(
        paragraph = paragraph,
        sourceLang = sourceLang,
        targetLang = targetLang,
        inputSource = inputSource,
        intent = ProcessingIntent.TRANSLATION,
    )

    /** Batch grammar explanation (user tap). Meanings still come from SQLite packs only. */
    suspend fun explainGrammar(
        contextText: String,
        focusWord: String?,
        sourceLang: String,
        targetLang: String,
        depth: GrammarDepth,
        inputSource: InputSource = InputSource.TYPED,
    ): String = withContext(Dispatchers.IO) {
        val tier = checkAppAccess.subscriptionTier()
        if (tier == SubscriptionTier.FREE) {
            return@withContext "Grammar hints need Pro or Plus. Meanings above are from your offline pack."
        }

        val prompt = buildGrammarPrompt(contextText, focusWord, sourceLang, targetLang, depth)
        val key = "grammar:${depth.id}:$sourceLang:${prompt.hashCode()}"
        aiCacheDao.get(key)?.responseJson?.let { return@withContext it }

        val enContext = englishPivotAi.toEnglish(contextText, sourceLang, inputSource)
        val enFocus = focusWord?.let { englishPivotAi.toEnglish(it, sourceLang, inputSource) }
        val enPrompt = buildGrammarPrompt(enContext, enFocus, "en", "en", depth)
        val enKey = "grammar:${depth.id}:en:${enPrompt.hashCode()}"
        aiCacheDao.get(enKey)?.responseJson?.let {
            return@withContext englishPivotAi.fromEnglish(it, targetLang, inputSource)
        }

        val mode = aiModePrefs.resolvedMode(inputSource, tier)
        val backend = homeAiSettings.preferredBackend.first()
        if (backend == AiBackend.LOCAL_HOME) {
            guestAiUsageRepository.ensureGuestCanUseAi()
            runCatching {
                withTimeout(appConfig.homeAiTimeoutMs) {
                    homeAiService.ask(
                        text = enPrompt,
                        mode = mode,
                        intent = ProcessingIntent.ANSWER,
                        inputSource = inputSource,
                        tier = tier,
                        languageCode = "en",
                        targetLang = "en",
                    )
                }
            }.onSuccess { result ->
                guestAiUsageRepository.recordGuestAiUsage()
                lastBackend = AiBackend.LOCAL_HOME
                aiCacheDao.put(AiCacheEntity(enKey, result, System.currentTimeMillis()))
                return@withContext englishPivotAi.fromEnglish(result, targetLang, inputSource)
            }.onFailure { e ->
                val reason = when (e) {
                    is TimeoutCancellationException -> "home_ai_timeout_${appConfig.homeAiTimeoutMs}ms"
                    else -> "home_ai_error: ${e.message}"
                }
                aiProviderRepository.recordFallback(reason)
            }
        }

        val cloud = runCatching {
            guestAiUsageRepository.ensureGuestCanUseAi()
            aiService.explainParagraph(
                AiParagraphRequest(
                    paragraph = enPrompt,
                    sourceLang = "en",
                    targetLang = "en",
                ),
            )
        }.fold(
            onSuccess = { resp ->
                guestAiUsageRepository.recordGuestAiUsage()
                aiProviderRepository.recordProviderUsed(resp.providerUsed)
                lastBackend = AiBackend.CLOUD_POOL
                resp.explanation
            },
            onFailure = { e ->
                if (e is GuestAiLimitReachedException) throw e
                offlineSummary(contextText)
            },
        )
        val localized = englishPivotAi.fromEnglish(cloud, targetLang, inputSource)
        aiCacheDao.put(AiCacheEntity(enKey, cloud, System.currentTimeMillis()))
        localized
    }

    /** Warm local + server cache in one Home AI round-trip; no duplicate grammar calls. */
    suspend fun warmReaderCaches(
        grammarTargets: List<GrammarPrefetchTarget>,
        sourceLang: String,
        targetLangs: List<String>,
        targetLang: String,
        depth: GrammarDepth,
        inputSource: InputSource,
        intent: ProcessingIntent,
        explainChunk: String?,
        translateChunk: String?,
    ) = withContext(Dispatchers.IO) {
        if (grammarTargets.isEmpty()) return@withContext
        val tier = checkAppAccess.subscriptionTier()
        if (tier == SubscriptionTier.FREE) return@withContext

        val mode = aiModePrefs.resolvedMode(inputSource, tier)
        val backend = homeAiSettings.preferredBackend.first()

        if (backend == AiBackend.LOCAL_HOME) {
            runCatching {
                homeAiService.prefetchAi(
                    targets = grammarTargets,
                    sourceLang = sourceLang,
                    targetLangs = targetLangs,
                    depth = depth,
                    mode = mode,
                    tier = tier,
                    inputSource = inputSource,
                    explainChunk = explainChunk,
                    translateChunk = translateChunk,
                )?.let { response ->
                    cacheGrammarResults(
                        response.grammar.results,
                        grammarTargets,
                        sourceLang,
                        targetLang,
                        depth,
                    )
                }
            }
        }

        when (intent) {
            ProcessingIntent.ANSWER -> explainChunk?.let { chunk ->
                warmParagraphIfCached(chunk, sourceLang, targetLang, inputSource, ProcessingIntent.ANSWER)
            }
            ProcessingIntent.TRANSLATION -> translateChunk?.let { chunk ->
                warmParagraphIfCached(chunk, sourceLang, targetLang, inputSource, ProcessingIntent.TRANSLATION)
            }
        }

        trimAiCacheIfNeeded()
    }

    private suspend fun warmParagraphIfCached(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource,
        intent: ProcessingIntent,
    ) {
        val tier = checkAppAccess.subscriptionTier()
        if (tier == SubscriptionTier.FREE) return
        val mode = aiModePrefs.resolvedMode(inputSource, tier)
        val key = "batch:$sourceLang:$targetLang:${intent.name}:${mode.id}:${inputSource.name}:${paragraph.hashCode()}"
        if (aiCacheDao.get(key) != null) return
        processParagraph(paragraph, sourceLang, targetLang, inputSource, intent)
    }

    private suspend fun cacheGrammarResults(
        results: List<HomeAiGrammarResultItem>,
        targets: List<GrammarPrefetchTarget>,
        sourceLang: String,
        targetLang: String,
        depth: GrammarDepth,
    ) {
        for (item in results) {
            if (item.explanation.isBlank()) continue
            val contextText = targets.firstOrNull { t ->
                item.focusWord == null || t.focusWord.equals(item.focusWord, ignoreCase = true)
            }?.contextText ?: targets.firstOrNull()?.contextText ?: ""
            val prompt = buildGrammarPrompt(
                contextText,
                item.focusWord,
                sourceLang,
                targetLang,
                depth,
            )
            val key = "grammar:${depth.id}:$sourceLang:${prompt.hashCode()}"
            aiCacheDao.put(AiCacheEntity(key, item.explanation, System.currentTimeMillis()))
        }
    }

    private suspend fun trimAiCacheIfNeeded() {
        if (aiCacheDao.count() > MAX_AI_CACHE_ENTRIES) {
            aiCacheDao.trimToMax(MAX_AI_CACHE_ENTRIES)
        }
    }

    suspend fun prefetchGrammarBatch(
        targets: List<GrammarPrefetchTarget>,
        sourceLang: String,
        targetLangs: List<String>,
        depth: GrammarDepth,
        inputSource: InputSource = InputSource.TYPED,
    ) = warmReaderCaches(
        grammarTargets = targets,
        sourceLang = sourceLang,
        targetLangs = targetLangs,
        targetLang = targetLangs.firstOrNull() ?: "en",
        depth = depth,
        inputSource = inputSource,
        intent = aiModePrefs.current().processingIntent,
        explainChunk = null,
        translateChunk = null,
    )

    private fun buildGrammarPrompt(
        contextText: String,
        focusWord: String?,
        sourceLang: String,
        targetLang: String,
        depth: GrammarDepth,
    ): String = when (depth) {
        GrammarDepth.WORD -> """
            You are a language tutor. Language: $sourceLang (learner may read notes in $targetLang if helpful).
            Explain the grammar role of the word "$focusWord" in this sentence only:
            "$contextText"
            Cover: part of speech, morphology/inflection if any, and why it appears in this form.
            Keep it concise (3–5 sentences). No dictionary definitions — grammar only.
        """.trimIndent()

        GrammarDepth.SENTENCE -> """
            You are a language tutor. Language: $sourceLang.
            Explain the grammar of this sentence (structure, tense/mood, clauses, agreement):
            "$contextText"
            Include brief notes on key words. Keep it clear for a learner (4–6 sentences).
        """.trimIndent()

        GrammarDepth.PARAGRAPH -> """
            You are a language tutor. Language: $sourceLang.
            Explain the grammar patterns in this paragraph — sentence by sentence overview, then main rules:
            "$contextText"
            Be structured and learner-friendly. Max 8 short sentences.
        """.trimIndent()
    }

    private suspend fun processParagraph(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource,
        intent: ProcessingIntent,
    ): String = withContext(Dispatchers.IO) {
        val tier = checkAppAccess.subscriptionTier()
        if (tier == SubscriptionTier.FREE) {
            return@withContext offlineSummary(paragraph)
        }

        val profile = MixedLanguageAnalyzer.analyze(paragraph, sourceLang, targetLang)
        val effectiveSource = profile.effectiveSourceLang()
        val responseLang = englishPivotAi.responseLanguage(effectiveSource, targetLang, intent)

        if (englishPivotAi.needsPivot(effectiveSource, responseLang)) {
            val enInput = englishPivotAi.toEnglish(paragraph, effectiveSource, inputSource)
            val enOutput = runAiCore(
                paragraph = enInput,
                sourceLang = "en",
                targetLang = "en",
                inputSource = inputSource,
                intent = intent,
                tier = tier,
            )
            return@withContext englishPivotAi.fromEnglish(enOutput, responseLang, inputSource)
        }

        runAiCore(
            paragraph = paragraph,
            sourceLang = effectiveSource,
            targetLang = targetLang,
            inputSource = inputSource,
            intent = intent,
            tier = tier,
        )
    }

    private suspend fun runAiCore(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource,
        intent: ProcessingIntent,
        tier: SubscriptionTier,
    ): String {
        val profile = MixedLanguageAnalyzer.analyze(paragraph, sourceLang, targetLang)
        val mode = aiModePrefs.resolvedMode(inputSource, tier)
        val key = "batch:$sourceLang:$targetLang:${intent.name}:${mode.id}:${inputSource.name}:${paragraph.hashCode()}"
        aiCacheDao.get(key)?.responseJson?.let { return formatAiOutput(it) }

        val backend = homeAiSettings.preferredBackend.first()
        if (backend == AiBackend.LOCAL_HOME) {
            guestAiUsageRepository.ensureGuestCanUseAi()
            runCatching {
                withTimeout(appConfig.homeAiTimeoutMs) {
                    callHomeAi(paragraph, sourceLang, targetLang, inputSource, intent, mode, tier)
                }
            }.onSuccess { result ->
                if (intent == ProcessingIntent.TRANSLATION && isTranslationStub(result)) {
                    aiProviderRepository.recordFallback("home_ai_translation_stub")
                } else {
                    guestAiUsageRepository.recordGuestAiUsage()
                    lastBackend = AiBackend.LOCAL_HOME
                    val formatted = formatAiOutput(result)
                    aiCacheDao.put(AiCacheEntity(key, formatted, System.currentTimeMillis()))
                    return formatted
                }
            }.onFailure { e ->
                val reason = when (e) {
                    is TimeoutCancellationException -> "home_ai_timeout_${appConfig.homeAiTimeoutMs}ms"
                    else -> "home_ai_error: ${e.message}"
                }
                aiProviderRepository.recordFallback(reason)
            }
        }

        val cloud = runCatching {
            guestAiUsageRepository.ensureGuestCanUseAi()
            val prompt = PracticePromptBuilder.build(
                paragraph,
                sourceLang,
                targetLang,
                intent,
                profile,
            )
            val body = AiParagraphRequest(
                paragraph = prompt,
                sourceLang = sourceLang,
                targetLang = targetLang,
            )
            aiService.explainParagraph(body)
        }.fold(
            onSuccess = { resp ->
                guestAiUsageRepository.recordGuestAiUsage()
                aiProviderRepository.recordProviderUsed(resp.providerUsed)
                lastBackend = AiBackend.CLOUD_POOL
                resp.explanation
            },
            onFailure = { e ->
                if (e is GuestAiLimitReachedException) throw e
                offlineSummary(paragraph)
            },
        )
        val formatted = formatAiOutput(cloud)
        aiCacheDao.put(AiCacheEntity(key, formatted, System.currentTimeMillis()))
        return formatted
    }

    private fun formatAiOutput(raw: String): String = AiResponseFormatter.format(raw)

    private fun isTranslationStub(text: String): Boolean =
        text.startsWith("[NLLB stub") || text.startsWith("[NLLB openvino stub")

    private suspend fun callHomeAi(
        paragraph: String,
        sourceLang: String,
        targetLang: String,
        inputSource: InputSource,
        intent: ProcessingIntent,
        mode: AiEngineMode,
        tier: SubscriptionTier,
    ): String = when (intent) {
        ProcessingIntent.TRANSLATION -> homeAiService.translateParagraph(
            paragraph = paragraph,
            sourceLang = sourceLang,
            targetLang = targetLang,
            mode = mode,
            tier = tier,
            inputSource = inputSource,
        )
        ProcessingIntent.ANSWER -> homeAiService.ask(
            text = paragraph,
            mode = mode,
            intent = intent,
            inputSource = inputSource,
            tier = tier,
            languageCode = sourceLang,
            targetLang = targetLang,
        )
    }

    private fun offlineSummary(paragraph: String) =
        "Offline summary: ${paragraph.take(160)}…"

    companion object {
        private const val MAX_AI_CACHE_ENTRIES = 200
    }
}
