package com.cheradip.ailanguagetutor.core.common

/** Sync app UI/API language code for OkHttp interceptors. Default: English (US). */
object AppLocaleHolder {
    @Volatile
    var languageCode: String = "en"
}
