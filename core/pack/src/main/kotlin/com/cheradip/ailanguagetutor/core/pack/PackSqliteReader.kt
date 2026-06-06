package com.cheradip.ailanguagetutor.core.pack

import android.database.sqlite.SQLiteDatabase
import java.io.File

internal object PackSqliteReader {
    fun lookupWord(dbFile: File, lemma: String, languageCode: String): List<String>? {
        if (!dbFile.isFile) return null
        val normalized = lemma.lowercase().trim()
        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery(
                """
                SELECT m.meaning FROM words w
                JOIN meanings m ON m.word_id = w.id
                WHERE w.lemma = ? AND w.language = ?
                ORDER BY m.rank ASC LIMIT 3
                """.trimIndent(),
                arrayOf(normalized, languageCode.lowercase()),
            ).use { cursor ->
                if (!cursor.moveToFirst()) return null
                buildList {
                    do {
                        add(cursor.getString(0))
                    } while (cursor.moveToNext())
                }
            }
        } finally {
            db.close()
        }
    }

    fun lookupPhrase(dbFile: File, phrase: String, sourceLang: String): String? {
        if (!dbFile.isFile) return null
        val normalized = phrase.lowercase().trim()
        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery(
                """
                SELECT translated_text FROM phrase_translations
                WHERE LOWER(source_phrase) = ? AND source_lang = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(normalized, sourceLang.lowercase()),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } finally {
            db.close()
        }
    }

    fun pivotTranslation(dbFile: File, sourceWord: String, sourceLang: String, targetLang: String): String? {
        if (!dbFile.isFile) return null
        val normalized = sourceWord.lowercase().trim()
        val db = SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READONLY)
        return try {
            db.rawQuery(
                """
                SELECT target_word FROM word_translations
                WHERE LOWER(source_word) = ? AND source_lang = ? AND target_lang = ?
                LIMIT 1
                """.trimIndent(),
                arrayOf(normalized, sourceLang.lowercase(), targetLang.lowercase()),
            ).use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        } finally {
            db.close()
        }
    }
}
