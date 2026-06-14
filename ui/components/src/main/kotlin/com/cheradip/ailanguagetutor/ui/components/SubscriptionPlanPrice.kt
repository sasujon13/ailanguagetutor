package com.cheradip.ailanguagetutor.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp

@Composable
fun SubscriptionPlanPrice(
    payablePrice: String,
    compareAtPrice: String?,
    modifier: Modifier = Modifier,
) {
    val payableStyle = MaterialTheme.typography.bodyLarge.copy(
        color = MaterialTheme.colorScheme.primary,
    ).toSpanStyle()
    val compareStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 10.sp,
        lineHeight = 12.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textDecoration = TextDecoration.LineThrough,
    ).toSpanStyle()

    Text(
        modifier = modifier,
        text = buildAnnotatedString {
            withStyle(payableStyle) {
                append(payablePrice)
            }
            if (compareAtPrice != null) {
                append(" ")
                withStyle(compareStyle) {
                    append(compareAtPrice)
                }
            }
        },
    )
}
