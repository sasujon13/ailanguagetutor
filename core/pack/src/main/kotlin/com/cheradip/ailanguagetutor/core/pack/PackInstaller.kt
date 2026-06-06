package com.cheradip.ailanguagetutor.core.pack

import java.io.File
import java.util.zip.ZipInputStream

object PackInstaller {
    /** Installs a downloaded pack archive or returns the JSON file path unchanged. */
    fun installPack(archiveOrJson: File, languageCode: String, packsRoot: File): File {
        if (!archiveOrJson.name.endsWith(".zip", ignoreCase = true)) {
            return archiveOrJson
        }
        val destDir = File(packsRoot, languageCode).apply { mkdirs() }
        ZipInputStream(archiveOrJson.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val out = File(destDir, entry.name)
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        archiveOrJson.delete()
        return File(destDir, "dictionary.db")
    }

    fun packDirectory(localPath: String?): File? {
        if (localPath.isNullOrBlank()) return null
        val file = File(localPath)
        return when {
            file.isDirectory -> file
            file.parentFile?.let { dir ->
                dir.isDirectory && File(dir, "dictionary.db").isFile
            } == true -> file.parentFile
            else -> null
        }
    }

    fun dictionaryDb(localPath: String?): File? {
        if (localPath.isNullOrBlank()) return null
        val file = File(localPath)
        return when {
            file.name.equals("dictionary.db", ignoreCase = true) && file.isFile -> file
            file.isDirectory -> File(file, "dictionary.db").takeIf { it.isFile }
            file.parentFile?.let { File(it, "dictionary.db").isFile } == true -> File(file.parentFile, "dictionary.db")
            else -> null
        }
    }

    fun translationDb(localPath: String?): File? {
        val dir = packDirectory(localPath) ?: return null
        return File(dir, "translation.db").takeIf { it.isFile }
    }
}
