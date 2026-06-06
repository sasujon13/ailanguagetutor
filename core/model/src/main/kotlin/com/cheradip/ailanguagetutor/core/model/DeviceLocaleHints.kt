package com.cheradip.ailanguagetutor.core.model

import java.util.Locale

/** Device locale hints for catalog ordering (English first, region second). */
object DeviceLocaleHints {
    fun current(): LanguageCatalogOrder.Hints {
        val locale = Locale.getDefault()
        return LanguageCatalogOrder.Hints(
            countryCode = locale.country.ifBlank { LanguageCatalogOrder.DEFAULT_COUNTRY },
            languageCode = locale.language.ifBlank { LanguageCatalogOrder.ENGLISH_CODE },
        )
    }
}
