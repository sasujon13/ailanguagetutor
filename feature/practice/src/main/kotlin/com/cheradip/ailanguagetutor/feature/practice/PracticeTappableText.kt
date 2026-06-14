package com.cheradip.ailanguagetutor.feature.practice

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cheradip.ailanguagetutor.core.model.WordSpan
import com.cheradip.ailanguagetutor.ui.components.TappableGrammarText

@Composable
fun PracticeTappableText(
    text: String,
    words: List<WordSpan>,
    onWordTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
) {
    TappableGrammarText(
        text = text,
        words = words,
        onWordTap = onWordTap,
        modifier = modifier,
        label = label,
    )
}
