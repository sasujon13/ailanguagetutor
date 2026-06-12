package com.cheradip.ailanguagetutor.core.model

data class Document(
    val id: Long,
    val title: String,
    val languageCode: String,
    val pageCount: Int,
    val sourceType: String = "scan",
    val createdAt: Long,
    val updatedAt: Long,
)

data class DocumentPage(
    val id: Long,
    val documentId: Long,
    val pageIndex: Int,
    val imagePath: String,
    val ocrText: String?,
    val wordMapJson: String?,
    val width: Int,
    val height: Int,
)

data class WordSpan(
    val text: String,
    val startOffset: Int,
    val endOffset: Int,
    val boundingBox: WordBoundingBox? = null,
)

data class WordBoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class WordDefinition(
    val word: String,
    val languageCode: String,
    val meanings: List<String>,
    val phonetic: String? = null,
    val example: String? = null,
)

data class SavedWord(
    val id: Long,
    val word: String,
    val languageCode: String,
    val meaning: String,
    val savedAt: Long,
)
