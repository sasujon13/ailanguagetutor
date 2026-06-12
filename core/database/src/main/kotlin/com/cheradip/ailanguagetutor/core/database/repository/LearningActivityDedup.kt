package com.cheradip.ailanguagetutor.core.database.repository

import com.cheradip.ailanguagetutor.core.database.entity.LearningActivityEntity

/**
 * Collapses activities with the same trimmed input:
 * - If all replies match → keep the most recent row.
 * - If replies differ → keep the row with the longest reply (saved wins ties, then most recent).
 */
internal fun List<LearningActivityEntity>.deduplicateByInput(): List<LearningActivityEntity> {
    if (size <= 1) return this

    val withoutInput = filter { it.inputText.isNullOrBlank() }
    val withInput = filter { !it.inputText.isNullOrBlank() }
    if (withInput.isEmpty()) return this

    val deduped = withInput
        .groupBy { it.inputText!!.trim() }
        .map { (_, group) -> selectBestDuplicate(group) }

    return (withoutInput + deduped).sortedByDescending { it.sortTimestamp() }
}

private fun selectBestDuplicate(group: List<LearningActivityEntity>): LearningActivityEntity {
    if (group.size == 1) return group.first()

    val outputs = group.map { it.outputText?.trim().orEmpty() }
    val allRepliesSame = outputs.distinct().size == 1

    val picked = if (allRepliesSame) {
        group.maxBy { it.sortTimestamp() }
    } else {
        group.maxWith(
            compareBy<LearningActivityEntity> { it.outputText?.trim()?.length ?: 0 }
                .thenBy { it.isSaved }
                .thenBy { it.sortTimestamp() },
        )
    }

    return if (group.any { it.isSaved } && !picked.isSaved) {
        picked.copy(isSaved = true)
    } else {
        picked
    }
}

private fun LearningActivityEntity.sortTimestamp(): Long = maxOf(updatedAt, createdAt)
