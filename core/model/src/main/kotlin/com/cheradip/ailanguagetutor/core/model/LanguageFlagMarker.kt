package com.cheradip.ailanguagetutor.core.model

object LanguageFlagMarker {
    fun emoji(entry: LanguageCatalogEntry?): String = entry?.flagEmoji ?: "🏳️"

    fun label(entry: LanguageCatalogEntry?): String {
        if (entry == null) return ""
        return "${entry.flagEmoji} ${entry.name}"
    }

    fun compact(entry: LanguageCatalogEntry?): String {
        if (entry == null) return ""
        return "${entry.flagEmoji} ${entry.code.uppercase()}"
    }
}
