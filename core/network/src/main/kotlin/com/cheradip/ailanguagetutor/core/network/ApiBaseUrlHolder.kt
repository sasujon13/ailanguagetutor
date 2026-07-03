package com.cheradip.ailanguagetutor.core.network

import javax.inject.Inject
import javax.inject.Singleton

/** Mutable API base URL — updated from Admin Developer Options without rebuilding the app. */
@Singleton
class ApiBaseUrlHolder @Inject constructor() {
    @Volatile
    var baseUrl: String = ""

    fun update(url: String) {
        baseUrl = ApiBaseUrlNormalizer.normalize(url)
    }
}
