package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.database.dao.AiCacheDao
import com.cheradip.ailanguagetutor.core.database.entity.AiCacheEntity
import com.cheradip.ailanguagetutor.core.model.GrammarBook
import com.cheradip.ailanguagetutor.core.model.GrammarBookChapter
import com.cheradip.ailanguagetutor.core.model.GrammarBookSection
import com.cheradip.ailanguagetutor.core.model.GrammarSectionEnrichment
import com.cheradip.ailanguagetutor.core.model.InputSource
import com.cheradip.ailanguagetutor.core.network.CHECK_INTERNET_CONNECTION
import com.cheradip.ailanguagetutor.core.network.NetworkErrorFormatter
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookChapterDto
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookEnrichResponse
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookResponse
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookSectionDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrammarBookRepository @Inject constructor(
    private val homeAiService: HomeAiService,
    private val aiModePrefs: AiModePreferenceRepository,
    private val checkAppAccess: CheckAppAccessUseCase,
    private val aiCacheDao: AiCacheDao,
    private val networkErrors: NetworkErrorFormatter,
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

        val tier = checkAppAccess.subscriptionTier()
        val mode = aiModePrefs.resolvedMode(
            inputSource = InputSource.TYPED,
            tier = tier,
        )

        if (homeAiService.isReachable()) {
            if (!networkErrors.isOnline()) {
                return@withContext Result.failure(
                    IllegalStateException(CHECK_INTERNET_CONNECTION),
                )
            }
            runCatching {
                homeAiService.fetchGrammarBook(
                    languageCode = code,
                    languageName = languageName,
                    mode = mode,
                    tier = tier,
                )
            }.onSuccess { response ->
                val book = response.toModel()
                persistLocal(code, book)
                return@withContext Result.success(book.copy(cached = response.cached))
            }.onFailure { err ->
                return@withContext Result.failure(
                    IllegalStateException(
                        networkErrors.present(err, "Could not load grammar book. Check Home AI and try refresh."),
                    ),
                )
            }
        }

        Result.success(builtInOutlineBook(code, languageName))
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

        val tier = checkAppAccess.subscriptionTier()
        val mode = aiModePrefs.resolvedMode(inputSource = InputSource.TYPED, tier = tier)

        if (!homeAiService.isReachable()) {
            return@withContext Result.success(
                GrammarSectionEnrichment(expandedBody = section.body, loaded = true),
            )
        }

        if (!networkErrors.isOnline()) {
            return@withContext Result.failure(
                IllegalStateException(CHECK_INTERNET_CONNECTION),
            )
        }

        runCatching {
            homeAiService.enrichGrammarBookSection(
                languageCode = code,
                languageName = languageName,
                chapterNumber = chapterNumber,
                chapterTitle = chapterTitle,
                sectionHeading = section.heading,
                sectionBody = section.body,
                examples = section.examples,
                mode = mode,
                tier = tier,
            )
        }.map { response ->
            val enrichment = GrammarSectionEnrichment(
                expandedBody = response.expandedBody.ifBlank { section.body },
                extraExamples = response.extraExamples,
                learnerTip = response.learnerTip,
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

    private fun cacheKey(languageCode: String) = "grammar_book:v1:${languageCode.lowercase()}"

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
