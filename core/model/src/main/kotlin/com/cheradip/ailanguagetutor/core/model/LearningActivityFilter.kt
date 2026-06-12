package com.cheradip.ailanguagetutor.core.model

enum class LearningActivityFilter(val label: String) {
    ALL("All"),
    RECENT("Recent"),
    SAVED("Saved"),
    PRACTICE("Learning"),
    SCANS("Scans"),
    UPLOADS("Uploads"),
    GRAMMAR("Grammar"),
    READ("Read"),
}

fun LearningActivityFilter.matches(
    activityType: String,
    isSaved: Boolean,
): Boolean = when (this) {
    LearningActivityFilter.ALL -> true
    LearningActivityFilter.RECENT -> !isSaved
    LearningActivityFilter.SAVED -> isSaved
    LearningActivityFilter.PRACTICE -> activityType.startsWith("practice")
    LearningActivityFilter.SCANS -> activityType == "scan"
    LearningActivityFilter.UPLOADS -> activityType == "import"
    LearningActivityFilter.GRAMMAR -> activityType == "grammar"
    LearningActivityFilter.READ -> activityType == "read"
}

private val documentSectionFilters = setOf(
    LearningActivityFilter.ALL,
    LearningActivityFilter.SCANS,
    LearningActivityFilter.UPLOADS,
    LearningActivityFilter.READ,
)

fun Set<LearningActivityFilter>.matchesActivity(
    activityType: String,
    isSaved: Boolean,
): Boolean {
    if (isEmpty() || LearningActivityFilter.ALL in this) return true
    return any { it.matches(activityType, isSaved) }
}

fun Set<LearningActivityFilter>.matchesDocument(sourceType: String): Boolean {
    if (isEmpty() || LearningActivityFilter.ALL in this) return true
    var match = false
    if (LearningActivityFilter.SCANS in this && sourceType == "scan") match = true
    if (LearningActivityFilter.UPLOADS in this && sourceType == "import") match = true
    if (LearningActivityFilter.READ in this) match = true
    return match
}

fun Set<LearningActivityFilter>.showsDocuments(): Boolean {
    if (isEmpty() || LearningActivityFilter.ALL in this) return true
    return any { it in documentSectionFilters }
}

fun toggleHistoryFilter(
    current: Set<LearningActivityFilter>,
    filter: LearningActivityFilter,
): Set<LearningActivityFilter> = when (filter) {
    LearningActivityFilter.ALL -> setOf(LearningActivityFilter.ALL)
    else -> {
        val base = current - LearningActivityFilter.ALL
        val updated = if (filter in base) base - filter else base + filter
        if (updated.isEmpty()) setOf(LearningActivityFilter.ALL) else updated
    }
}
