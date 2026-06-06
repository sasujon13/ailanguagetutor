package com.cheradip.ailanguagetutor.core.model

/**
 * Filters 243+ catalog entries by substring match (anywhere in name / native / code / region).
 * Sort: earliest match index first, then full label ascending.
 */
object LanguageSearchFilter {

    fun haystack(entry: LanguageCatalogEntry): String =
        "${entry.name} ${entry.nativeName} ${entry.code} ${entry.region}".lowercase()

    fun filterAndSort(
        languages: List<LanguageCatalogEntry>,
        query: String,
        localeHints: LanguageCatalogOrder.Hints = LanguageCatalogOrder.Hints(),
    ): List<LanguageCatalogEntry> {
        val q = query.trim().lowercase()
        if (q.isEmpty()) {
            return LanguageCatalogOrder.sort(languages, localeHints)
        }
        val matched = languages
            .mapNotNull { entry ->
                val text = haystack(entry)
                val index = text.indexOf(q)
                if (index < 0) null else entry to (index to text)
            }
            .sortedWith(
                compareBy<Pair<LanguageCatalogEntry, Pair<Int, String>>>(
                    { it.second.first },
                    { it.second.second },
                ),
            )
            .map { it.first }
        return LanguageCatalogOrder.sort(matched, localeHints)
    }
}
