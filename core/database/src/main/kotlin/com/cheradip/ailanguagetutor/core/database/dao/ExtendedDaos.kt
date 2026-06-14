package com.cheradip.ailanguagetutor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.cheradip.ailanguagetutor.core.database.entity.AiCacheEntity
import com.cheradip.ailanguagetutor.core.database.entity.LanguagePackStateEntity
import com.cheradip.ailanguagetutor.core.database.entity.LearningActivityEntity
import com.cheradip.ailanguagetutor.core.database.entity.TranslationCacheEntity
import com.cheradip.ailanguagetutor.core.database.entity.TrialStateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LanguagePackDao {
    @Query("SELECT * FROM language_pack_state ORDER BY downloadedAt DESC")
    fun observeAll(): Flow<List<LanguagePackStateEntity>>

    @Query("SELECT * FROM language_pack_state WHERE isActive = 1")
    fun observeActive(): Flow<List<LanguagePackStateEntity>>

    @Query("SELECT COUNT(*) FROM language_pack_state WHERE isActive = 1")
    suspend fun activeCount(): Int

    @Query("SELECT * FROM language_pack_state WHERE LOWER(languageCode) = LOWER(:code) LIMIT 1")
    suspend fun getByCode(code: String): LanguagePackStateEntity?

    @Query("SELECT * FROM language_pack_state WHERE localPath IS NOT NULL AND localPath != ''")
    suspend fun listDownloaded(): List<LanguagePackStateEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LanguagePackStateEntity)

    @Update
    suspend fun update(entity: LanguagePackStateEntity)
}

@Dao
interface TranslationCacheDao {
    @Query(
        """
        SELECT * FROM translation_cache
        WHERE sourceTextHash = :hash AND sourceLang = :sourceLang AND targetLang = :targetLang
        LIMIT 1
        """,
    )
    suspend fun find(hash: String, sourceLang: String, targetLang: String): TranslationCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: TranslationCacheEntity)

    @Query("DELETE FROM translation_cache WHERE LOWER(sourceLang) = LOWER(:lang) OR LOWER(targetLang) = LOWER(:lang)")
    suspend fun clearForLanguage(lang: String)
}

@Dao
interface LearningActivityDao {
    @Query("SELECT * FROM learning_activities ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<LearningActivityEntity>>

    @Query(
        """
        SELECT * FROM learning_activities
        WHERE title LIKE '%' || :query || '%'
           OR summary LIKE '%' || :query || '%'
           OR inputText LIKE '%' || :query || '%'
           OR outputText LIKE '%' || :query || '%'
        ORDER BY createdAt DESC
        """,
    )
    fun search(query: String): Flow<List<LearningActivityEntity>>

    @Query("SELECT * FROM learning_activities WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): LearningActivityEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LearningActivityEntity): Long

    @Query("UPDATE learning_activities SET isSaved = :saved, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSaved(id: Long, saved: Boolean, updatedAt: Long)

    @Query("SELECT * FROM learning_activities WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): LearningActivityEntity?

    @Query(
        """
        DELETE FROM learning_activities
        WHERE isSaved = 0
          AND id IN (
            SELECT id FROM learning_activities
            WHERE isSaved = 0
            ORDER BY createdAt ASC
            LIMIT MAX(0, (SELECT COUNT(*) FROM learning_activities WHERE isSaved = 0) - :maxUnsaved)
          )
        """,
    )
    suspend fun trimUnsaved(maxUnsaved: Int)
}

@Dao
interface AiCacheDao {
    @Query("SELECT * FROM ai_cache WHERE cacheKey = :key LIMIT 1")
    suspend fun get(key: String): AiCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entity: AiCacheEntity)

    @Query("SELECT COUNT(*) FROM ai_cache")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM ai_cache WHERE cacheKey IN (
            SELECT cacheKey FROM ai_cache ORDER BY cachedAt ASC
            LIMIT MAX(0, (SELECT COUNT(*) FROM ai_cache) - :maxEntries)
        )
        """,
    )
    suspend fun trimToMax(maxEntries: Int)
}

@Dao
interface TrialStateDao {
    @Query("SELECT * FROM trial_state WHERE id = 1 LIMIT 1")
    suspend fun get(): TrialStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TrialStateEntity)
}
