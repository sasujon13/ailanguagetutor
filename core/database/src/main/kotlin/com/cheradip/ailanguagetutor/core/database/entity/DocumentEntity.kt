package com.cheradip.ailanguagetutor.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val languageCode: String,
    val pageCount: Int,
    val sourceType: String = "scan",
    val createdAt: Long,
    val updatedAt: Long,
)
