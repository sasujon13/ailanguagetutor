package com.cheradip.ailanguagetutor.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "language_pack_state",
    indices = [Index(value = ["languageCode"], unique = true)],
)
data class LanguagePackStateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val languageCode: String,
    val version: Int = 1,
    val localPath: String? = null,
    val isActive: Boolean = false,
    val downloadedAt: Long? = null,
)

@Entity(
    tableName = "translation_cache",
    indices = [Index(value = ["sourceTextHash", "sourceLang", "targetLang"], unique = true)],
)
data class TranslationCacheEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceTextHash: String,
    val sourceText: String,
    val sourceLang: String,
    val targetLang: String,
    val translatedText: String,
    val strategy: String,
    val cachedAt: Long,
)

@Entity(
    tableName = "learning_activities",
    indices = [Index(value = ["documentId"]), Index(value = ["title"])],
)
data class LearningActivityEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val documentId: Long? = null,
    val title: String,
    val summary: String? = null,
    val activityType: String,
    val languageCode: String,
    val outputLanguageCode: String? = null,
    val inputText: String? = null,
    val outputText: String? = null,
    val tagsJson: String? = null,
    val isSaved: Boolean = false,
    val remoteId: String? = null,
    val createdAt: Long,
    val updatedAt: Long = createdAt,
)

@Entity(tableName = "ai_cache")
data class AiCacheEntity(
    @PrimaryKey val cacheKey: String,
    val responseJson: String,
    val cachedAt: Long,
)

@Entity(tableName = "trial_state")
data class TrialStateEntity(
    @PrimaryKey val id: Int = 1,
    val deviceId: String,
    val trialStartedAt: Long,
    val trialEndsAt: Long,
)
