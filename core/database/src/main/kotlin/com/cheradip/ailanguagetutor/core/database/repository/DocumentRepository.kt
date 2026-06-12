package com.cheradip.ailanguagetutor.core.database.repository

import com.cheradip.ailanguagetutor.core.database.dao.DocumentDao
import com.cheradip.ailanguagetutor.core.database.dao.DocumentPageDao
import com.cheradip.ailanguagetutor.core.database.entity.DocumentEntity
import com.cheradip.ailanguagetutor.core.database.entity.DocumentPageEntity
import com.cheradip.ailanguagetutor.core.model.Document
import com.cheradip.ailanguagetutor.core.model.DocumentPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: DocumentDao,
    private val documentPageDao: DocumentPageDao,
) {
    fun observeDocuments(): Flow<List<Document>> =
        documentDao.observeAll().map { list -> list.map { it.toModel() } }

    fun observeDocument(id: Long): Flow<Document?> =
        documentDao.observeById(id).map { it?.toModel() }

    fun observePages(documentId: Long): Flow<List<DocumentPage>> =
        documentPageDao.observeByDocument(documentId).map { list -> list.map { it.toModel() } }

    suspend fun getDocument(id: Long): Document? = withContext(Dispatchers.IO) {
        documentDao.getById(id)?.toModel()
    }

    suspend fun getPages(documentId: Long): List<DocumentPage> = withContext(Dispatchers.IO) {
        documentPageDao.getByDocument(documentId).map { it.toModel() }
    }

    suspend fun getPage(pageId: Long): DocumentPage? = withContext(Dispatchers.IO) {
        documentPageDao.getById(pageId)?.toModel()
    }

    suspend fun createDocument(
        title: String,
        languageCode: String = "en",
        sourceType: String = "scan",
    ): Long =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            documentDao.insert(
                DocumentEntity(
                    title = title,
                    languageCode = languageCode,
                    pageCount = 0,
                    sourceType = sourceType,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }

    suspend fun addPage(
        documentId: Long,
        imagePath: String,
        width: Int,
        height: Int,
    ): Long = withContext(Dispatchers.IO) {
        val pageIndex = documentPageDao.countForDocument(documentId)
        val pageId = documentPageDao.insert(
            DocumentPageEntity(
                documentId = documentId,
                pageIndex = pageIndex,
                imagePath = imagePath,
                width = width,
                height = height,
            ),
        )
        documentDao.getById(documentId)?.let { doc ->
            documentDao.update(
                doc.copy(
                    pageCount = pageIndex + 1,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
        pageId
    }

    suspend fun updatePageOcr(
        pageId: Long,
        ocrText: String,
        wordMapJson: String,
    ) = withContext(Dispatchers.IO) {
        documentPageDao.getById(pageId)?.let { page ->
            documentPageDao.update(
                page.copy(ocrText = ocrText, wordMapJson = wordMapJson),
            )
        }
    }

    suspend fun deleteDocument(id: Long) = withContext(Dispatchers.IO) {
        documentDao.deleteById(id)
    }

    private fun DocumentEntity.toModel() = Document(
        id = id,
        title = title,
        languageCode = languageCode,
        pageCount = pageCount,
        sourceType = sourceType,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun DocumentPageEntity.toModel() = DocumentPage(
        id = id,
        documentId = documentId,
        pageIndex = pageIndex,
        imagePath = imagePath,
        ocrText = ocrText,
        wordMapJson = wordMapJson,
        width = width,
        height = height,
    )
}
