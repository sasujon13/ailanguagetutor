package com.cheradip.packbuilder

import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager
import java.util.zip.ZipInputStream
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.createTempDirectory
import kotlin.io.path.deleteRecursively
import kotlin.io.path.inputStream

@OptIn(ExperimentalPathApi::class)
object PackValidator {
    data class ValidationResult(val ok: Boolean, val messages: List<String>)

    private val requiredZipEntries = listOf(
        "dictionary.db",
        "translation.db",
        "metadata.json",
        "frequency.json",
    )

    fun validateZip(zipPath: Path): ValidationResult {
        val messages = mutableListOf<String>()
        if (!Files.isRegularFile(zipPath)) {
            return ValidationResult(false, listOf("ZIP not found: $zipPath"))
        }

        val found = mutableSetOf<String>()
        ZipInputStream(zipPath.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                found += entry.name
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        requiredZipEntries.forEach { name ->
            if (name !in found) messages += "Missing entry: $name"
        }
        if (messages.isNotEmpty()) {
            return ValidationResult(false, messages)
        }

        val tempDir = createTempDirectory("pack-validate-")
        try {
            ZipInputStream(zipPath.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        val out = tempDir.resolve(entry.name)
                        out.parent?.let { Files.createDirectories(it) }
                        Files.newOutputStream(out).use { zis.copyTo(it) }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
            validateDictionaryDb(tempDir.resolve("dictionary.db"), messages)
            validateTranslationDb(tempDir.resolve("translation.db"), messages)
        } finally {
            tempDir.deleteRecursively()
        }

        return ValidationResult(messages.isEmpty(), messages)
    }

    private fun validateDictionaryDb(path: Path, messages: MutableList<String>) {
        runCatching {
            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery("SELECT value FROM pack_meta WHERE key = 'schema_version'").use { rs ->
                        if (!rs.next() || rs.getString(1) != "1") {
                            messages += "dictionary.db schema_version != 1"
                        }
                    }
                    st.executeQuery("SELECT COUNT(*) FROM words").use { rs ->
                        rs.next()
                        if (rs.getInt(1) < 10) messages += "dictionary.db has fewer than 10 words"
                    }
                }
            }
        }.onFailure { messages += "dictionary.db error: ${it.message}" }
    }

    private fun validateTranslationDb(path: Path, messages: MutableList<String>) {
        runCatching {
            DriverManager.getConnection("jdbc:sqlite:${path.toAbsolutePath()}").use { conn ->
                conn.createStatement().use { st ->
                    st.executeQuery(
                        "SELECT name FROM sqlite_master WHERE type='table' AND name='phrase_translations'",
                    ).use { rs ->
                        if (!rs.next()) messages += "translation.db missing phrase_translations"
                    }
                }
            }
        }.onFailure { messages += "translation.db error: ${it.message}" }
    }
}
