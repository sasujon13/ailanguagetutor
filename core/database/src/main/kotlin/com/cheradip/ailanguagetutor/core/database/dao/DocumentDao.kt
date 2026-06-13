package com.cheradip.ailanguagetutor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cheradip.ailanguagetutor.core.database.entity.DocumentEntity
import com.cheradip.ailanguagetutor.core.database.entity.DocumentPageEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getById(id: Long): DocumentEntity?

    @Query("SELECT * FROM documents WHERE id = :id")
    fun observeById(id: Long): Flow<DocumentEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(document: DocumentEntity): Long

    @Update
    suspend fun update(document: DocumentEntity)

    @Query("DELETE FROM documents WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface DocumentPageDao {
    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    fun observeByDocument(documentId: Long): Flow<List<DocumentPageEntity>>

    @Query("SELECT * FROM document_pages WHERE documentId = :documentId ORDER BY pageIndex ASC")
    suspend fun getByDocument(documentId: Long): List<DocumentPageEntity>

    @Query("SELECT * FROM document_pages WHERE id = :id")
    suspend fun getById(id: Long): DocumentPageEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(page: DocumentPageEntity): Long

    @Update
    suspend fun update(page: DocumentPageEntity)

    @Query("SELECT COUNT(*) FROM document_pages WHERE documentId = :documentId")
    suspend fun countForDocument(documentId: Long): Int

    @Query("DELETE FROM document_pages WHERE id = :pageId")
    suspend fun deleteById(pageId: Long)
}
