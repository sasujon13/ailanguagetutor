package com.cheradip.ailanguagetutor.core.database.repository

import com.cheradip.ailanguagetutor.core.database.dao.LearningActivityDao
import com.cheradip.ailanguagetutor.core.database.dao.SavedWordDao
import com.cheradip.ailanguagetutor.core.database.entity.LearningActivityEntity
import com.cheradip.ailanguagetutor.core.database.entity.SavedWordEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SavedWordRepository @Inject constructor(
    private val savedWordDao: SavedWordDao,
) {
    fun observeAll(): Flow<List<SavedWordEntity>> = savedWordDao.observeAll()

    suspend fun save(word: String, languageCode: String, meaning: String) {
        savedWordDao.insert(
            SavedWordEntity(
                word = word,
                languageCode = languageCode,
                meaning = meaning,
                savedAt = System.currentTimeMillis(),
            ),
        )
    }
}

@Singleton
class LearningActivityRepository @Inject constructor(
    private val learningActivityDao: LearningActivityDao,
) {
    fun observeAll(): Flow<List<LearningActivityEntity>> = learningActivityDao.observeAll()

    fun search(query: String): Flow<List<LearningActivityEntity>> = learningActivityDao.search(query)

    suspend fun record(
        title: String,
        activityType: String,
        languageCode: String,
        documentId: Long? = null,
        summary: String? = null,
        tags: List<String> = emptyList(),
    ) {
        learningActivityDao.insert(
            LearningActivityEntity(
                documentId = documentId,
                title = title,
                summary = summary,
                activityType = activityType,
                languageCode = languageCode,
                tagsJson = if (tags.isEmpty()) null else tags.joinToString(","),
                createdAt = System.currentTimeMillis(),
            ),
        )
    }
}
