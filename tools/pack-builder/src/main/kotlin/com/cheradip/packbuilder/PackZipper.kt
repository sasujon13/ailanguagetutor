package com.cheradip.packbuilder

import java.io.BufferedInputStream
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.io.path.outputStream

object PackZipper {
    data class ZipResult(val zipPath: Path, val sha256: String, val sizeBytes: Long)

    fun zip(workDir: Path, zipPath: Path, entries: List<String>): ZipResult {
        zipPath.parent?.createDirectories()
        zipPath.deleteIfExists()
        zipPath.outputStream().use { fos ->
            ZipOutputStream(fos).use { zos ->
                entries.forEach { name ->
                    val file = workDir.resolve(name)
                    check(Files.isRegularFile(file)) { "Missing pack file: $name" }
                    zos.putNextEntry(ZipEntry(name))
                    file.inputStream().use { input -> input.copyTo(zos) }
                    zos.closeEntry()
                }
            }
        }
        val sha = sha256File(zipPath)
        return ZipResult(zipPath, sha, Files.size(zipPath))
    }

    fun sha256File(path: Path): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestInputStream(BufferedInputStream(FileInputStream(path.toFile())), digest).use { dis ->
            val buffer = ByteArray(8192)
            while (dis.read(buffer) != -1) { /* drain */ }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    fun listEntries(zipPath: Path): List<String> {
        return zipPath.inputStream().use { fis ->
            ZipInputStream(fis).use { zis ->
                generateSequence { zis.nextEntry }.map { it.name }.toList()
            }
        }
    }
}
