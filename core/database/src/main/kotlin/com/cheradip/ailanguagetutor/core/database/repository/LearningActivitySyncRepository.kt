package com.cheradip.ailanguagetutor.core.database.repository

import com.cheradip.ailanguagetutor.core.common.SessionTokenHolder
import com.cheradip.ailanguagetutor.core.database.entity.LearningActivityEntity
import com.cheradip.ailanguagetutor.core.network.AiltLearningService
import com.cheradip.ailanguagetutor.core.network.LearningActivityDto
import com.cheradip.ailanguagetutor.core.network.LearningActivitySyncRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LearningActivitySyncRepository @Inject constructor(
    private val learningActivityRepository: LearningActivityRepository,
    private val learningService: AiltLearningService,
    private val sessionTokenHolder: SessionTokenHolder,
) {
    suspend fun syncIfLoggedIn(): Result<Int> {
        if (sessionTokenHolder.token.value.isNullOrBlank()) {
            return Result.success(0)
        }
        return runCatching {
            val local = learningActivityRepository.listForSync()
            val payload = local.mapNotNull { it.toDto() }
            val response = learningService.sync(LearningActivitySyncRequest(activities = payload))
            val merged = response.activities.map { it.toEntity() }
            learningActivityRepository.mergeFromRemote(merged)
            merged.size
        }
    }
}

private fun LearningActivityEntity.toDto(): LearningActivityDto? {
    val clientId = remoteId ?: return null
    return LearningActivityDto(
        clientId = clientId,
        title = title,
        summary = summary,
        activityType = activityType,
        languageCode = languageCode,
        outputLanguageCode = outputLanguageCode,
        inputText = inputText,
        outputText = outputText,
        tagsJson = tagsJson,
        isSaved = isSaved,
        createdAtMs = createdAt,
        updatedAtMs = updatedAt,
    )
}

private fun LearningActivityDto.toEntity() = LearningActivityEntity(
    id = 0,
    documentId = null,
    title = title,
    summary = summary,
    activityType = activityType,
    languageCode = languageCode,
    outputLanguageCode = outputLanguageCode,
    inputText = inputText,
    outputText = outputText,
    tagsJson = tagsJson,
    isSaved = isSaved,
    remoteId = clientId,
    createdAt = createdAtMs,
    updatedAt = updatedAtMs,
)
