package com.cheradip.packbuilder

import com.cheradip.packbuilder.model.WordSeed
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import kotlin.io.path.deleteIfExists

object DictionaryImporter {
    private val ddl = """
        CREATE TABLE pack_meta (
            key     TEXT PRIMARY KEY,
            value   TEXT NOT NULL
        );
        CREATE TABLE words (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            word            TEXT NOT NULL,
            lemma           TEXT NOT NULL,
            language        TEXT NOT NULL,
            frequency_score REAL NOT NULL DEFAULT 0,
            ipa             TEXT,
            UNIQUE(word, language)
        );
        CREATE INDEX idx_words_lemma ON words(lemma, language);
        CREATE INDEX idx_words_freq ON words(frequency_score DESC);
        CREATE TABLE meanings (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            word_id         INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
            meaning         TEXT NOT NULL,
            usage_example   TEXT,
            rank            INTEGER NOT NULL CHECK(rank BETWEEN 1 AND 3),
            UNIQUE(word_id, rank)
        );
        CREATE TABLE synonyms (
            id              INTEGER PRIMARY KEY AUTOINCREMENT,
            word_id         INTEGER NOT NULL REFERENCES words(id) ON DELETE CASCADE,
            synonym         TEXT NOT NULL
        );
        CREATE INDEX idx_synonyms_word ON synonyms(word_id);
        CREATE TABLE pronunciation (
            word_id         INTEGER PRIMARY KEY REFERENCES words(id) ON DELETE CASCADE,
            ipa             TEXT,
            phoneme         TEXT
        );
    """.trimIndent()

    fun import(languageCode: String, words: List<WordSeed>, outputPath: Path): Int {
        outputPath.deleteIfExists()
        DriverManager.getConnection("jdbc:sqlite:${outputPath.toAbsolutePath()}").use { conn ->
            conn.autoCommit = false
            conn.createStatement().use { it.executeUpdate("PRAGMA foreign_keys = ON") }
            ddl.split(";").filter { it.isNotBlank() }.forEach { stmt ->
                conn.createStatement().use { it.executeUpdate(stmt.trim()) }
            }
            conn.prepareStatement(
                "INSERT INTO pack_meta (key, value) VALUES (?, ?)",
            ).use { ps ->
                ps.setString(1, "schema_version")
                ps.setString(2, "1")
                ps.executeUpdate()
                ps.setString(1, "language_code")
                ps.setString(2, languageCode.lowercase())
                ps.executeUpdate()
            }
            words.forEach { insertWord(conn, it) }
            conn.commit()
            return words.size
        }
    }

    private fun insertWord(conn: Connection, seed: WordSeed) {
        conn.prepareStatement(
            """
            INSERT OR IGNORE INTO words (word, lemma, language, frequency_score, ipa)
            VALUES (?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { ps ->
            ps.setString(1, seed.word)
            ps.setString(2, seed.lemma.lowercase())
            ps.setString(3, seed.language.lowercase())
            ps.setDouble(4, seed.frequencyScore)
            ps.setString(5, seed.ipa)
            ps.executeUpdate()
        }

        val wordId = conn.prepareStatement(
            "SELECT id FROM words WHERE word = ? AND language = ?",
        ).use { sel ->
            sel.setString(1, seed.word)
            sel.setString(2, seed.language.lowercase())
            sel.executeQuery().use { rs ->
                check(rs.next()) { "Failed to resolve word id for ${seed.word}" }
                rs.getLong(1)
            }
        }

        seed.meanings.take(3).forEachIndexed { index, meaning ->
            conn.prepareStatement(
                """
                INSERT OR REPLACE INTO meanings (word_id, meaning, usage_example, rank)
                VALUES (?, ?, ?, ?)
                """.trimIndent(),
            ).use { ps ->
                ps.setLong(1, wordId)
                ps.setString(2, meaning)
                ps.setString(3, seed.examples.getOrNull(index))
                ps.setInt(4, index + 1)
                ps.executeUpdate()
            }
        }

        seed.synonyms.forEach { synonym ->
            conn.prepareStatement(
                "INSERT INTO synonyms (word_id, synonym) VALUES (?, ?)",
            ).use { ps ->
                ps.setLong(1, wordId)
                ps.setString(2, synonym)
                ps.executeUpdate()
            }
        }

        if (seed.ipa != null) {
            conn.prepareStatement(
                "INSERT OR REPLACE INTO pronunciation (word_id, ipa, phoneme) VALUES (?, ?, ?)",
            ).use { ps ->
                ps.setLong(1, wordId)
                ps.setString(2, seed.ipa)
                ps.setString(3, null)
                ps.executeUpdate()
            }
        }
    }
}
