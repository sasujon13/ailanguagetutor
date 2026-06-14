package com.cheradip.ailanguagetutor.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkErrorFormatter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun isOnline(): Boolean = context.hasValidatedInternet()

    fun offlineMessage(): String = CHECK_INTERNET_CONNECTION

    fun present(error: Throwable?, fallback: String): String =
        resolveInternetRequiredError(isOnline(), error, fallback)

    fun presentFailure(error: Throwable, fallback: String): String = present(error, fallback)
}
