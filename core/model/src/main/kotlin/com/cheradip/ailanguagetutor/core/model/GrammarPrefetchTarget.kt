package com.cheradip.ailanguagetutor.core.model

data class GrammarPrefetchTarget(
    val offset: Int,
    val focusWord: String,
    val contextText: String,
)
