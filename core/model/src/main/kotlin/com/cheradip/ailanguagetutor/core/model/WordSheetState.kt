package com.cheradip.ailanguagetutor.core.model

data class WordSheetState(
    val definition: WordDefinition,
    val grammarText: String? = null,
    val grammarLoading: Boolean = false,
    val grammarDepth: GrammarDepth = GrammarDepth.WORD,
    val contextSnippet: String? = null,
)
