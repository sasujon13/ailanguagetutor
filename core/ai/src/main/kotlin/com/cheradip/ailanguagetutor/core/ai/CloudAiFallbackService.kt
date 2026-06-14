package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.device.GuestAiUsageRepository
import com.cheradip.ailanguagetutor.core.model.GuestAiLimitReachedException
import com.cheradip.ailanguagetutor.core.network.AiParagraphRequest
import com.cheradip.ailanguagetutor.core.network.AiltAiService
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookEnrichResponse
import com.cheradip.ailanguagetutor.core.network.HomeAiGrammarBookResponse
import com.cheradip.ailanguagetutor.core.network.NetworkErrorFormatter
import com.squareup.moshi.Moshi
import javax.inject.Inject
import javax.inject.Singleton

/** Cloud pool fallback when Home AI is unavailable (cheradip.com /ai/explain-paragraph). */
@Singleton
class CloudAiFallbackService @Inject constructor(
    private val aiService: AiltAiService,
    private val guestAiUsageRepository: GuestAiUsageRepository,
    private val aiProviderRepository: AiProviderRepository,
    private val networkErrors: NetworkErrorFormatter,
    moshi: Moshi,
) {
    private val bookAdapter = moshi.adapter(HomeAiGrammarBookResponse::class.java)
    private val enrichAdapter = moshi.adapter(HomeAiGrammarBookEnrichResponse::class.java)

    suspend fun translate(
        text: String,
        sourceLang: String,
        targetLang: String,
        homeFailureReason: String,
    ): String? {
        if (text.isBlank()) return text
        val prompt =
            "Translate the following text from $sourceLang to $targetLang. " +
                "Return ONLY the translation with no quotes or explanation.\n\n$text"
        return complete(
            prompt = prompt,
            sourceLang = sourceLang,
            targetLang = targetLang,
            homeFailureReason = homeFailureReason,
        )
    }

    suspend fun generateGrammarBook(
        languageCode: String,
        languageName: String,
        homeFailureReason: String,
    ): HomeAiGrammarBookResponse? {
        val prompt = buildGrammarBookPrompt(languageCode, languageName)
        val raw = complete(
            prompt = prompt,
            sourceLang = languageCode,
            targetLang = languageCode,
            homeFailureReason = homeFailureReason,
        ) ?: return null
        return parseGrammarBook(raw, languageCode, languageName)
    }

    suspend fun enrichGrammarSection(
        languageCode: String,
        languageName: String,
        chapterNumber: Int,
        chapterTitle: String,
        sectionHeading: String,
        sectionBody: String,
        examples: List<String>,
        homeFailureReason: String,
    ): HomeAiGrammarBookEnrichResponse? {
        val prompt = buildSectionEnrichPrompt(
            languageCode = languageCode,
            languageName = languageName,
            chapterNumber = chapterNumber,
            chapterTitle = chapterTitle,
            sectionHeading = sectionHeading,
            sectionBody = sectionBody,
            examples = examples,
        )
        val raw = complete(
            prompt = prompt,
            sourceLang = languageCode,
            targetLang = languageCode,
            homeFailureReason = homeFailureReason,
        ) ?: return null
        return parseSectionEnrich(raw, sectionBody)
    }

    private suspend fun complete(
        prompt: String,
        sourceLang: String,
        targetLang: String,
        homeFailureReason: String,
    ): String? {
        if (!networkErrors.isOnline()) return null
        return runCatching {
            guestAiUsageRepository.ensureGuestCanUseAi()
            val resp = aiService.explainParagraph(
                AiParagraphRequest(
                    paragraph = prompt,
                    sourceLang = sourceLang,
                    targetLang = targetLang,
                ),
            )
            guestAiUsageRepository.recordGuestAiUsage()
            aiProviderRepository.recordProviderUsed(resp.providerUsed)
            aiProviderRepository.recordFallback(homeFailureReason)
            resp.explanation.trim()
        }.fold(
            onSuccess = { it.takeIf { text -> text.isNotBlank() } },
            onFailure = { e ->
                if (e is GuestAiLimitReachedException) throw e
                null
            },
        )
    }

    private fun parseGrammarBook(
        raw: String,
        languageCode: String,
        languageName: String,
    ): HomeAiGrammarBookResponse? {
        val json = extractJsonObject(raw) ?: return null
        return runCatching { bookAdapter.fromJson(json) }
            .getOrNull()
            ?.takeIf { it.chapters.isNotEmpty() }
            ?: run {
                runCatching {
                    parseGrammarBookLoose(json, languageCode, languageName)
                }.getOrNull()
            }
    }

    private fun parseGrammarBookLoose(
        json: String,
        languageCode: String,
        languageName: String,
    ): HomeAiGrammarBookResponse? {
        val mapAdapter = bookAdapter.fromJson(json) ?: return null
        if (mapAdapter.chapters.isEmpty()) return null
        return mapAdapter.copy(
            title = mapAdapter.title.ifBlank { "Grammar Book — $languageName" },
            languageCode = mapAdapter.languageCode.ifBlank { languageCode },
            languageName = mapAdapter.languageName.ifBlank { languageName },
        )
    }

    private fun parseSectionEnrich(raw: String, sectionBody: String): HomeAiGrammarBookEnrichResponse? {
        val json = extractJsonObject(raw) ?: return null
        return runCatching { enrichAdapter.fromJson(json) }
            .getOrNull()
            ?.let { parsed ->
                parsed.copy(expandedBody = parsed.expandedBody.ifBlank { sectionBody })
            }
    }

    private fun extractJsonObject(raw: String): String? {
        var text = raw.trim()
        val fence = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(text)
        if (fence != null) text = fence.groupValues[1].trim()
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return text.substring(start, end + 1)
    }

    private fun buildGrammarBookPrompt(languageCode: String, languageName: String): String {
        val display = languageName.ifBlank { languageCode }
        return """
            You are writing a concise grammar learning book for learners of $display ($languageCode).
            Return ONLY valid JSON (no markdown fences) with this exact shape:
            {
              "title": "Grammar Book — $display",
              "language_code": "$languageCode",
              "language_name": "$display",
              "chapters": [
                {
                  "number": 1,
                  "title": "Chapter title",
                  "summary": "One sentence overview",
                  "sections": [
                    {
                      "heading": "Section heading",
                      "body": "Clear learner-friendly explanation (2-4 sentences)",
                      "examples": ["example in target language with brief gloss"]
                    }
                  ]
                }
              ]
            }
            Include exactly 8 chapters from sounds/alphabet through common verbs,
            sentence structure, questions, negation, and everyday patterns.
            Each chapter: 2-3 sections. Examples must use the target language.
        """.trimIndent()
    }

    private fun buildSectionEnrichPrompt(
        languageCode: String,
        languageName: String,
        chapterNumber: Int,
        chapterTitle: String,
        sectionHeading: String,
        sectionBody: String,
        examples: List<String>,
    ): String {
        val display = languageName.ifBlank { languageCode }
        val ex = examples.take(4).joinToString("\n") { "- $it" }.ifBlank { "(none yet)" }
        return """
            You are expanding a grammar lesson for learners of $display ($languageCode).
            Chapter $chapterNumber: $chapterTitle
            Section: $sectionHeading
            Current text:
            $sectionBody
            Existing examples:
            $ex

            Return ONLY valid JSON (no markdown fences):
            {
              "expanded_body": "2-4 sentences of deeper explanation building on the current text",
              "extra_examples": ["new example in target language with gloss"],
              "learner_tip": "One practical tip for remembering this rule"
            }
            Use the target language in examples. Be clear and encouraging.
        """.trimIndent()
    }
}
