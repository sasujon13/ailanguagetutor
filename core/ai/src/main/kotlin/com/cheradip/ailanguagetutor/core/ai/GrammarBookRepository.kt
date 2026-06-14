package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.database.dao.AiCacheDao
import com.cheradip.ailanguagetutor.core.database.entity.AiCacheEntity
import com.cheradip.ailanguagetutor.core.model.AiBackend
import com.cheradip.ailanguagetutor.core.model.AiEngineMode
import com.cheradip.ailanguagetutor.core.model.GrammarBook
import com.cheradip.ailanguagetutor.core.model.GrammarBookChapter
import com.cheradip.ailanguagetutor.core.model.GrammarBookSection
import com.cheradip.ailanguagetutor.core.model.GrammarSectionEnrichment
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.model.SubscriptionTier
import com.cheradip.ailanguagetutor.core.pack.LanguageCodeResolver
import com.cheradip.ailanguagetutor.core.network.CHECK_INTERNET_CONNECTION
import com.cheradip.ailanguagetutor.core.network.NetworkErrorFormatter
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookChapterDto
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookEnrichResponse
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookResponse
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookSectionDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrammarBookRepository @Inject constructor(
    private val homeAiService: HomeAiService,
    private val homeAiSettings: HomeAiSettingsRepository,
    private val cloudAiFallback: CloudAiFallbackService,
    private val aiModePrefs: AiModePreferenceRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
    private val aiCacheDao: AiCacheDao,
    private val networkErrors: NetworkErrorFormatter,
    private val aiProviderRepository: AiProviderRepository,
    private val englishPivotAi: EnglishPivotAiCoordinator,
    private val appConfig: AppConfig,
    moshi: Moshi,
) {
    private val adapter = moshi.adapter(HomeAiGrammarBookResponse::class.java)
    private val enrichAdapter = moshi.adapter(HomeAiGrammarBookEnrichResponse::class.java)

    suspend fun loadBook(
        languageCode: String,
        languageName: String,
        forceRefresh: Boolean = false,
    ): Result<GrammarBook> = withContext(Dispatchers.IO) {
        val code = languageCode.lowercase()

        if (!forceRefresh) {
            localBook(code)
                ?.takeUnless { isLegacyPlaceholderBook(it) }
                ?.let { return@withContext Result.success(it.copy(cached = true)) }
        }

        if (checkAppAccess.subscriptionTier() == SubscriptionTier.FREE) {
            return@withContext Result.success(builtInOutlineBook(code, languageName))
        }

        if (!networkErrors.isOnline()) {
            return@withContext Result.failure(
                IllegalStateException(CHECK_INTERNET_CONNECTION),
            )
        }

        val tier = checkAppAccess.subscriptionTier()
        val mode = aiModePrefs.resolvedMode(
            inputSource = InputSource.TYPED,
            tier = tier,
        )
        val generateInEnglish = !LanguageCodeResolver.isEnglish(code)
        val aiLang = if (generateInEnglish) "en" else code

        val bookFromAi = fetchBookViaHome(aiLang, languageName, mode, tier, generateInEnglish, code, languageName)
            ?.let { true to it }
            ?: fetchBookViaCloud(aiLang, languageName, generateInEnglish, code, languageName)
                ?.let { true to it }
            ?: (false to builtInOutlineBook(code, languageName))

        if (bookFromAi.first) {
            persistLocal(code, bookFromAi.second)
        }
        Result.success(bookFromAi.second)
    }

    suspend fun enrichSection(
        languageCode: String,
        languageName: String,
        chapterNumber: Int,
        chapterTitle: String,
        section: GrammarBookSection,
    ): Result<GrammarSectionEnrichment> = withContext(Dispatchers.IO) {
        val code = languageCode.lowercase()
        val cacheKey = enrichCacheKey(code, chapterNumber, section.heading)
        localEnrichment(cacheKey)?.let { return@withContext Result.success(it) }

        if (checkAppAccess.subscriptionTier() == SubscriptionTier.FREE) {
            return@withContext Result.success(
                GrammarSectionEnrichment(expandedBody = section.body, loaded = true),
            )
        }

        if (!networkErrors.isOnline()) {
            return@withContext Result.failure(
                IllegalStateException(CHECK_INTERNET_CONNECTION),
            )
        }

        val tier = checkAppAccess.subscriptionTier()
        val mode = aiModePrefs.resolvedMode(inputSource = InputSource.TYPED, tier = tier)

        val response = enrichViaHome(
            code = code,
            languageName = languageName,
            chapterNumber = chapterNumber,
            chapterTitle = chapterTitle,
            section = section,
            mode = mode,
            tier = tier,
        ) ?: enrichViaCloud(
            code = code,
            languageName = languageName,
            chapterNumber = chapterNumber,
            chapterTitle = chapterTitle,
            section = section,
        )

        if (response == null) {
            return@withContext Result.success(
                GrammarSectionEnrichment(expandedBody = section.body, loaded = true),
            )
        }

        runCatching {
            val enrichment = GrammarSectionEnrichment(
                expandedBody = englishPivotAi.fromEnglish(
                    response.expandedBody.ifBlank { section.body },
                    code,
                    InputSource.TYPED,
                ),
                extraExamples = response.extraExamples.map {
                    englishPivotAi.fromEnglish(it, code, InputSource.TYPED)
                },
                learnerTip = response.learnerTip.takeIf { it.isNotBlank() }?.let {
                    englishPivotAi.fromEnglish(it, code, InputSource.TYPED)
                }.orEmpty(),
                loaded = true,
            )
            persistEnrichment(cacheKey, response)
            enrichment
        }.recoverCatching { error ->
            throw IllegalStateException(
                networkErrors.present(error, "Could not expand this section. Try again."),
            )
        }
    }

    private suspend fun fetchBookViaHome(
        aiLang: String,
        languageName: String,
        mode: AiEngineMode,
        tier: SubscriptionTier,
        generateInEnglish: Boolean,
        targetCode: String,
        displayName: String,
    ): GrammarBook? {
        if (homeAiSettings.preferredBackend.first() != AiBackend.LOCAL_HOME) return null
        return runCatching {
            withTimeout(appConfig.homeAiTimeoutMs) {
                homeAiService.fetchGrammarBook(
                    languageCode = aiLang,
                    languageName = languageName,
                    mode = mode,
                    tier = tier,
                ).let { response ->
                    val model = response.toModel()
                    if (generateInEnglish) {
                        pivotGrammarBookToLanguage(model, targetCode, displayName)
                    } else {
                        model
                    }
                }
            }
        }.fold(
            onSuccess = { it },
            onFailure = { e ->
                aiProviderRepository.recordFallback(homeFailureReason(e, "grammar_book"))
                null
            },
        )
    }

    private suspend fun fetchBookViaCloud(
        aiLang: String,
        languageName: String,
        generateInEnglish: Boolean,
        targetCode: String,
        displayName: String,
    ): GrammarBook? {
        return cloudAiFallback.generateGrammarBook(
            languageCode = aiLang,
            languageName = languageName,
            homeFailureReason = "grammar_book_cloud",
        )?.let { response ->
            val model = response.toModel()
            if (generateInEnglish) {
                pivotGrammarBookToLanguage(model, targetCode, displayName)
            } else {
                model
            }
        }
    }

    private suspend fun enrichViaHome(
        code: String,
        languageName: String,
        chapterNumber: Int,
        chapterTitle: String,
        section: GrammarBookSection,
        mode: AiEngineMode,
        tier: SubscriptionTier,
    ): HomeAiGrammarBookEnrichResponse? {
        if (homeAiSettings.preferredBackend.first() != AiBackend.LOCAL_HOME) return null
        return runCatching {
            withTimeout(appConfig.homeAiTimeoutMs) {
                val enBody = englishPivotAi.toEnglish(section.body, code, InputSource.TYPED)
                val enHeading = englishPivotAi.toEnglish(section.heading, code, InputSource.TYPED)
                homeAiService.enrichGrammarBookSection(
                    languageCode = "en",
                    languageName = languageName,
                    chapterNumber = chapterNumber,
                    chapterTitle = chapterTitle,
                    sectionHeading = enHeading,
                    sectionBody = enBody,
                    examples = section.examples.map {
                        englishPivotAi.toEnglish(it, code, InputSource.TYPED)
                    },
                    mode = mode,
                    tier = tier,
                )
            }
        }.fold(
            onSuccess = { it },
            onFailure = { e ->
                aiProviderRepository.recordFallback(homeFailureReason(e, "grammar_book_enrich"))
                null
            },
        )
    }

    private suspend fun enrichViaCloud(
        code: String,
        languageName: String,
        chapterNumber: Int,
        chapterTitle: String,
        section: GrammarBookSection,
    ): HomeAiGrammarBookEnrichResponse? {
        val generateInEnglish = !LanguageCodeResolver.isEnglish(code)
        val aiLang = if (generateInEnglish) "en" else code
        val enBody = englishPivotAi.toEnglish(section.body, code, InputSource.TYPED)
        val enHeading = englishPivotAi.toEnglish(section.heading, code, InputSource.TYPED)
        return cloudAiFallback.enrichGrammarSection(
            languageCode = aiLang,
            languageName = languageName,
            chapterNumber = chapterNumber,
            chapterTitle = chapterTitle,
            sectionHeading = enHeading,
            sectionBody = enBody,
            examples = section.examples.map {
                englishPivotAi.toEnglish(it, code, InputSource.TYPED)
            },
            homeFailureReason = "grammar_book_enrich_cloud",
        )
    }

    private fun homeFailureReason(e: Throwable, prefix: String): String = when (e) {
        is TimeoutCancellationException -> "${prefix}_home_timeout"
        else -> "${prefix}_home_error: ${e.message}"
    }

    private suspend fun localEnrichment(cacheKey: String): GrammarSectionEnrichment? {
        val json = aiCacheDao.get(cacheKey)?.responseJson ?: return null
        return runCatching {
            enrichAdapter.fromJson(json)?.toEnrichment()
        }.getOrNull()
    }

    private suspend fun persistEnrichment(cacheKey: String, response: HomeAiGrammarBookEnrichResponse) {
        val json = enrichAdapter.toJson(response) ?: return
        aiCacheDao.put(
            AiCacheEntity(
                cacheKey = cacheKey,
                responseJson = json,
                cachedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun HomeAiGrammarBookEnrichResponse.toEnrichment() = GrammarSectionEnrichment(
        expandedBody = expandedBody,
        extraExamples = extraExamples,
        learnerTip = learnerTip,
        loaded = true,
    )

    private fun enrichCacheKey(languageCode: String, chapterNumber: Int, heading: String): String {
        val slug = heading.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_")
            .trim('_')
            .take(40)
        return "grammar_book_enrich:v1:$languageCode:$chapterNumber:$slug"
    }

    private suspend fun localBook(languageCode: String): GrammarBook? {
        val json = aiCacheDao.get(cacheKey(languageCode))?.responseJson ?: return null
        return runCatching {
            adapter.fromJson(json)?.toModel()?.copy(cached = true)
        }.getOrNull()
    }

    private suspend fun persistLocal(languageCode: String, book: GrammarBook) {
        val dto = book.toDto()
        val json = adapter.toJson(dto) ?: return
        aiCacheDao.put(
            AiCacheEntity(
                cacheKey = cacheKey(languageCode),
                responseJson = json,
                cachedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun cacheKey(languageCode: String) = "grammar_book:v2:${languageCode.lowercase()}"

    private suspend fun pivotGrammarBookToLanguage(
        englishBook: GrammarBook,
        targetCode: String,
        languageName: String,
    ): GrammarBook {
        suspend fun localize(text: String): String {
            if (text.isBlank()) return text
            return englishPivotAi.fromEnglish(text, targetCode, InputSource.TYPED)
        }
        return englishBook.copy(
            title = localize(englishBook.title),
            languageCode = targetCode,
            languageName = languageName.ifBlank { englishBook.languageName },
            chapters = englishBook.chapters.map { chapter ->
                chapter.copy(
                    title = localize(chapter.title),
                    summary = localize(chapter.summary),
                    sections = chapter.sections.map { section ->
                        section.copy(
                            heading = localize(section.heading),
                            body = localize(section.body),
                            examples = section.examples.map { localize(it) },
                        )
                    },
                )
            },
        )
    }

    private fun isLegacyPlaceholderBook(book: GrammarBook): Boolean {
        if (book.chapters.size != 1) return false
        val chapter = book.chapters.first()
        return chapter.title.equals("Getting started", ignoreCase = true) &&
            chapter.summary.contains("Connect to Home AI", ignoreCase = true)
    }

    private fun builtInOutlineBook(code: String, name: String): GrammarBook {
        val display = name.ifBlank { code.uppercase() }
        val outline = listOf(
            "Sounds & spelling" to "Alphabet, pronunciation, and writing basics.",
            "Nouns & articles" to "Gender, number, and common noun patterns.",
            "Verbs — present" to "Regular and essential irregular verbs in the present tense.",
            "Questions & negation" to "Forming questions and saying no / not.",
            "Adjectives & agreement" to "Describing nouns and agreement rules.",
            "Pronouns" to "Subject, object, and possessive pronouns.",
            "Past & future" to "Talking about yesterday and tomorrow.",
            "Everyday patterns" to "Politeness, prepositions, and useful sentence templates.",
        )
        return GrammarBook(
            title = "Grammar Book — $display",
            languageCode = code,
            languageName = display,
            chapters = outline.mapIndexed { index, (title, summary) ->
                GrammarBookChapter(
                    number = index + 1,
                    title = title,
                    summary = summary,
                    sections = listOf(
                        GrammarBookSection(
                            heading = "Overview",
                            body = "This chapter covers ${title.lowercase()} in $display. "
                                + "Tap refresh when online to generate a full personalized edition.",
                            examples = listOf("[$display] — example pending"),
                        ),
                    ),
                )
            },
            cached = false,
        )
    }

    private fun HomeAiGrammarBookResponse.toModel() = GrammarBook(
        title = title,
        languageCode = languageCode,
        languageName = languageName,
        chapters = chapters.map { it.toModel() },
        cached = cached,
    )

    private fun HomeAiGrammarBookChapterDto.toModel() = GrammarBookChapter(
        number = number,
        title = title,
        summary = summary,
        sections = sections.map { it.toModel() },
    )

    private fun HomeAiGrammarBookSectionDto.toModel() = GrammarBookSection(
        heading = heading,
        body = body,
        examples = examples,
    )

    private fun GrammarBook.toDto() = HomeAiGrammarBookResponse(
        title = title,
        languageCode = languageCode,
        languageName = languageName,
        chapters = chapters.map { ch ->
            HomeAiGrammarBookChapterDto(
                number = ch.number,
                title = ch.title,
                summary = ch.summary,
                sections = ch.sections.map { sec ->
                    HomeAiGrammarBookSectionDto(
                        heading = sec.heading,
                        body = sec.body,
                        examples = sec.examples,
                    )
                },
            )
        },
        cached = cached,
    )
}
