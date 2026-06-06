package com.cheradip.ailanguagetutor.core.locale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.ReadOnlyComposable

/** Current translated UI strings — provided at the app root from [AppLocaleManager]. */
val LocalAppStrings = compositionLocalOf { AppStrings.english }

@Composable
@ReadOnlyComposable
fun appString(key: String): String = AppStrings.text(key, LocalAppStrings.current)

/** For non-@Composable lambdas — pass [strings] from `LocalAppStrings.current` at the call site. */
fun localizedString(key: String, strings: Map<String, String> = AppStrings.english): String =
    AppStrings.text(key, strings)
