package com.cheradip.packbuilder

import com.cheradip.packbuilder.model.LanguagePackSeed
import com.cheradip.packbuilder.model.PhraseTranslation
import com.cheradip.packbuilder.model.QaPair
import com.cheradip.packbuilder.model.WordTranslation
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.DriverManager
import kotlin.io.path.deleteIfExists

object TranslationImporter {
    private val ddl = """
        CREATE TABLE pack_meta (
            key     TEXT PRIMARY KEY,
            value   TEXT NOT NULL
        );
        CREATE TABLE phrase_translations (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            phrase_hash     TEXT NOT NULL,
            source_phrase   TEXT NOT NULL,
            source_lang     TEXT NOT NULL,
            target_lang     TEXT NOT NULL,
            translated_text TEXT NOT NULL,
            UNIQUE(phrase_hash, source_lang, target_lang)
        );
        CREATE INDEX idx_phrase_hash ON phrase_translations(phrase_hash, source_lang, target_lang);
        CREATE TABLE word_translations (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            source_word     TEXT NOT NULL,
            source_lang     TEXT NOT NULL,
            target_word     TEXT NOT NULL,
            target_lang     TEXT NOT NULL,
            frequency       REAL DEFAULT 0,
            UNIQUE(source_word, source_lang, target_lang)
        );
        CREATE INDEX idx_word_trans_src ON word_translations(source_word, source_lang, target_lang);
        CREATE TABLE qa_pairs (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            question_hash   TEXT NOT NULL,
            question_text   TEXT NOT NULL,
            source_lang     TEXT NOT NULL,
            answer_text     TEXT NOT NULL,
            answer_lang     TEXT NOT NULL,
            UNIQUE(question_hash, source_lang, answer_lang)
        );
        CREATE INDEX idx_qa_hash ON qa_pairs(question_hash, source_lang);
    """.trimIndent()

    fun import(languageCode: String, seed: LanguagePackSeed, outputPath: Path) {
        outputPath.deleteIfExists()
        DriverManager.getConnection("jdbc:sqlite:${outputPath.toAbsolutePath()}").use { conn ->
            conn.autoCommit = false
            ddl.split(";").filter { it.isNotBlank() }.forEach { stmt ->
                conn.createStatement().use { it.executeUpdate(stmt.trim()) }
            }
            conn.prepareStatement("INSERT INTO pack_meta (key, value) VALUES (?, ?)").use { ps ->
                ps.setString(1, "schema_version"); ps.setString(2, "1"); ps.executeUpdate()
                ps.setString(1, "language_code"); ps.setString(2, languageCode.lowercase()); ps.executeUpdate()
            }
            seed.phrases.forEach { insertPhrase(conn, it) }
            seed.wordTranslations.forEach { insertWordTranslation(conn, it) }
            seed.qaPairs.forEach { insertQa(conn, it) }
            conn.commit()
        }
    }

    private fun insertPhrase(conn: java.sql.Connection, phrase: PhraseTranslation) {
        val hash = sha256("${phrase.sourcePhrase.lowercase().trim()}|${phrase.sourceLang}")
        conn.prepareStatement(
            """
            INSERT OR REPLACE INTO phrase_translations
            (phrase_hash, source_phrase, source_lang, target_lang, translated_text)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, hash)
            ps.setString(2, phrase.sourcePhrase)
            ps.setString(3, phrase.sourceLang.lowercase())
            ps.setString(4, phrase.targetLang.lowercase())
            ps.setString(5, phrase.translatedText)
            ps.executeUpdate()
        }
    }

    private fun insertWordTranslation(conn: java.sql.Connection, wt: WordTranslation) {
        conn.prepareStatement(
            """
            INSERT OR REPLACE INTO word_translations
            (source_word, source_lang, target_word, target_lang, frequency)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, wt.sourceWord.lowercase())
            ps.setString(2, wt.sourceLang.lowercase())
            ps.setString(3, wt.targetWord)
            ps.setString(4, wt.targetLang.lowercase())
            ps.setDouble(5, wt.frequency)
            ps.executeUpdate()
        }
    }

    private fun insertQa(conn: java.sql.Connection, qa: QaPair) {
        val hash = sha256("${qa.questionText.lowercase().trim()}|${qa.sourceLang}")
        conn.prepareStatement(
            """
            INSERT OR REPLACE INTO qa_pairs
            (question_hash, question_text, source_lang, answer_text, answer_lang)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, hash)
            ps.setString(2, qa.questionText)
            ps.setString(3, qa.sourceLang.lowercase())
            ps.setString(4, qa.answerText)
            ps.setString(5, qa.answerLang.lowercase())
            ps.executeUpdate()
        }
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
