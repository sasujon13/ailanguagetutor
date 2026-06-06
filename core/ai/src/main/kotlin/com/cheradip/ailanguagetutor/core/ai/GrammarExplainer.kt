package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.common.TextContextExtractor
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import com.cheradip.ailanguagetutor.core.model.InputSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrammarExplainer @Inject constructor(
    private val aiManager: AIManager,
) {

    fun contextForDepth(fullText: String, offset: Int, depth: GrammarDepth): String = when (depth) {
        GrammarDepth.WORD, GrammarDepth.SENTENCE -> TextContextExtractor.sentenceAt(fullText, offset)
        GrammarDepth.PARAGRAPH -> TextContextExtractor.paragraphAt(fullText, offset)
    }

    suspend fun explain(
        fullText: String,
        tapOffset: Int,
        focusWord: String,
        languageCode: String,
        targetLang: String,
        depth: GrammarDepth,
        inputSource: InputSource = InputSource.TYPED,
    ): String {
        val context = contextForDepth(fullText, tapOffset, depth).ifBlank { focusWord }
        return aiManager.explainGrammar(
            contextText = context,
            focusWord = focusWord,
            sourceLang = languageCode,
            targetLang = targetLang,
            depth = depth,
            inputSource = inputSource,
        )
    }
}
