package com.cheradip.ailanguagetutor.core.model

enum class LearningActivityFilter(val label: String) {
    ALL("All"),
    RECENT("Recent"),
    SAVED("Saved"),
    PRACTICE("Practice"),
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
