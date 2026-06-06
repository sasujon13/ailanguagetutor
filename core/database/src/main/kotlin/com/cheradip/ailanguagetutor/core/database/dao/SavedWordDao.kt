package com.cheradip.ailanguagetutor.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.cheradip.ailanguagetutor.core.database.entity.SavedWordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedWordDao {
    @Query("SELECT * FROM saved_words ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<SavedWordEntity>>

    @Query("SELECT * FROM saved_words WHERE languageCode = :languageCode ORDER BY savedAt DESC")
    fun observeByLanguage(languageCode: String): Flow<List<SavedWordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(word: SavedWordEntity): Long

    @Query("DELETE FROM saved_words WHERE id = :id")
    suspend fun deleteById(id: Long)
}
