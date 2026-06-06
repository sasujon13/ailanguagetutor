package com.cheradip.ailanguagetutor.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_words",
    indices = [Index(value = ["word", "languageCode"], unique = true)],
)
data class SavedWordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val word: String,
    val languageCode: String,
    val meaning: String,
    val savedAt: Long,
)
