package com.cheradip.ailanguagetutor.core.database.repository

import com.cheradip.ailanguagetutor.core.database.dao.LearningActivityDao
import com.cheradip.ailanguagetutor.core.database.dao.SavedWordDao
import com.cheradip.ailanguagetutor.core.database.entity.LearningActivityEntity
import com.cheradip.ailanguagetutor.core.database.entity.SavedWordEntity
import com.cheradip.ailanguagetutor.core.model.LearningActivityFilter
import com.cheradip.ailanguagetutor.core.model.matches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
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

    fun observeFiltered(
        filter: LearningActivityFilter,
        query: String,
    ): Flow<List<LearningActivityEntity>> {
        val source = if (query.isBlank()) learningActivityDao.observeAll()
        else learningActivityDao.search(query)
        return source.map { list ->
            list.filter { filter.matches(it.activityType, it.isSaved) }
        }
    }

    fun search(query: String): Flow<List<LearningActivityEntity>> = learningActivityDao.search(query)

    suspend fun getById(id: Long): LearningActivityEntity? = learningActivityDao.getById(id)

    suspend fun record(
        title: String,
        activityType: String,
        languageCode: String,
        documentId: Long? = null,
        summary: String? = null,
        inputText: String? = null,
        outputText: String? = null,
        outputLanguageCode: String? = null,
        tags: List<String> = emptyList(),
        isSaved: Boolean = false,
    ): Long {
        val now = System.currentTimeMillis()
        val id = learningActivityDao.insert(
            LearningActivityEntity(
                documentId = documentId,
                title = title,
                summary = summary,
                activityType = activityType,
                languageCode = languageCode,
                outputLanguageCode = outputLanguageCode,
                inputText = inputText,
                outputText = outputText,
                tagsJson = if (tags.isEmpty()) null else tags.joinToString(","),
                isSaved = isSaved,
                remoteId = UUID.randomUUID().toString(),
                createdAt = now,
                updatedAt = now,
            ),
        )
        if (!isSaved) {
            learningActivityDao.trimUnsaved(MAX_UNSAVED)
        }
        return id
    }

    suspend fun markSaved(id: Long): LearningActivityEntity? {
        val existing = learningActivityDao.getById(id) ?: return null
        val now = System.currentTimeMillis()
        val remoteId = existing.remoteId ?: UUID.randomUUID().toString()
        learningActivityDao.insert(
            existing.copy(
                isSaved = true,
                remoteId = remoteId,
                updatedAt = now,
            ),
        )
        return learningActivityDao.getById(id)
    }

    suspend fun mergeFromRemote(incoming: List<LearningActivityEntity>) {
        if (incoming.isEmpty()) return
        incoming.forEach { remote ->
            val remoteId = remote.remoteId ?: return@forEach
            val local = learningActivityDao.getByRemoteId(remoteId)
            if (local != null) {
                val merged = local.copy(
                    title = remote.title,
                    summary = remote.summary ?: local.summary,
                    activityType = remote.activityType,
                    languageCode = remote.languageCode,
                    outputLanguageCode = remote.outputLanguageCode ?: local.outputLanguageCode,
                    inputText = remote.inputText ?: local.inputText,
                    outputText = remote.outputText ?: local.outputText,
                    tagsJson = remote.tagsJson ?: local.tagsJson,
                    isSaved = local.isSaved || remote.isSaved,
                    updatedAt = maxOf(local.updatedAt, remote.updatedAt),
                )
                learningActivityDao.insert(merged)
            } else {
                learningActivityDao.insert(remote.copy(id = 0))
            }
        }
        learningActivityDao.trimUnsaved(MAX_UNSAVED)
    }

    suspend fun listForSync(): List<LearningActivityEntity> = learningActivityDao.observeAll().first()

    companion object {
        const val MAX_UNSAVED = 99
    }
}
