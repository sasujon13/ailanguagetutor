package com.cheradip.ailanguagetutor.core.image

import java.io.File

enum class ExportConflictResolution {
    /** Overwrite existing files at the proposed paths. */
    REPLACE,
    /** Pick the next free letter prefix (a, b, … z, aa, …). */
    RENAME,
}

data class ExportPlan(
    val targetFiles: List<File>,
    val conflictingFiles: List<File>,
    /** Paths used when [ExportConflictResolution.RENAME] is chosen. */
    val renameTargets: List<File>,
) {
    val hasConflict: Boolean get() = conflictingFiles.isNotEmpty()
}

object ExportFileNaming {
    fun planExport(exportDir: File, options: ExportOptions, pageCount: Int): ExportPlan {
        val defaultTargets = resolveTargets(exportDir, options, pageCount, preferPlainName = true)
        val conflicts = defaultTargets.filter { it.exists() }
        val renameTargets = if (conflicts.isEmpty()) {
            defaultTargets
        } else {
            resolveTargets(exportDir, options, pageCount, preferPlainName = false)
        }
        return ExportPlan(
            targetFiles = defaultTargets,
            conflictingFiles = conflicts,
            renameTargets = renameTargets,
        )
    }

    fun resolveTargets(
        exportDir: File,
        options: ExportOptions,
        pageCount: Int,
        resolution: ExportConflictResolution,
    ): List<File> {
        val preferPlain = resolution == ExportConflictResolution.REPLACE
        val plan = planExport(exportDir, options, pageCount)
        return when {
            !plan.hasConflict -> plan.targetFiles
            resolution == ExportConflictResolution.REPLACE -> plan.targetFiles
            else -> plan.renameTargets
        }
    }

    private fun resolveTargets(
        exportDir: File,
        options: ExportOptions,
        pageCount: Int,
        preferPlainName: Boolean,
    ): List<File> = when (options.format) {
        ExportFormat.PDF -> listOf(resolveSingleFile(exportDir, options, "pdf", preferPlainName))
        ExportFormat.LONG_IMAGE -> listOf(resolveSingleFile(exportDir, options, "jpg", preferPlainName))
        ExportFormat.IMAGES -> resolveMultiImageFiles(exportDir, options, pageCount, preferPlainName)
    }

    private fun resolveSingleFile(
        dir: File,
        options: ExportOptions,
        extension: String,
        preferPlainName: Boolean,
    ): File {
        val base = exportBaseName(options, options.format)
        if (preferPlainName && !File(dir, "$base.$extension").exists()) {
            return File(dir, "$base.$extension")
        }
        val prefix = nextAvailableLetterPrefix(dir, base, extension, pageCount = 1)
        return File(dir, "${base}_${prefix}1.$extension")
    }

    private fun resolveMultiImageFiles(
        dir: File,
        options: ExportOptions,
        pageCount: Int,
        preferPlainName: Boolean,
    ): List<File> {
        val base = if (options.documentName.isBlank()) "image" else sanitizedName(options.documentName)
        val prefix = nextAvailableLetterPrefix(dir, base, "jpg", pageCount)
        return (1..pageCount).map { index ->
            File(dir, "${base}_${prefix}$index.jpg")
        }
    }

    /** Next letter prefix where no `{base}_{prefix}{1..pageCount}.jpg` exist. */
    fun nextAvailableLetterPrefix(
        dir: File,
        base: String,
        extension: String,
        pageCount: Int,
    ): String {
        val used = dir.listFiles()
            ?.mapNotNull { file ->
                Regex("""^${Regex.escape(base)}_([a-z]+)\d+\.${Regex.escape(extension)}$""")
                    .find(file.name)?.groupValues?.get(1)
            }            ?.toMutableSet().orEmpty()
        var length = 1
        while (length <= 6) {
            for (candidate in generateLetterPrefixes(length)) {
                if (candidate in used) continue
                if (isPrefixAvailable(dir, base, candidate, extension, pageCount)) {
                    return candidate
                }
            }
            length++
        }
        return "exp${System.currentTimeMillis()}"
    }

    private fun isPrefixAvailable(
        dir: File,
        base: String,
        letterPrefix: String,
        extension: String,
        pageCount: Int,
    ): Boolean {
        for (index in 1..pageCount) {
            if (File(dir, "${base}_${letterPrefix}$index.$extension").exists()) return false
        }
        return true
    }

    private fun generateLetterPrefixes(length: Int): List<String> {
        val out = mutableListOf<String>()
        fun recurse(current: String, remaining: Int) {
            if (remaining == 0) {
                out.add(current)
                return
            }
            for (c in 'a'..'z') recurse(current + c, remaining - 1)
        }
        recurse("", length)
        return out
    }

    fun exportBaseName(options: ExportOptions, format: ExportFormat): String = when {
        options.documentName.isNotBlank() -> sanitizedName(options.documentName)
        format == ExportFormat.LONG_IMAGE -> "long_image"
        else -> "Document"
    }

    fun sanitizedName(name: String): String =
        name.trim().replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "Document" }

    fun subfolder(format: ExportFormat): String = when (format) {
        ExportFormat.PDF -> "PDF"
        ExportFormat.IMAGES -> "Images"
        ExportFormat.LONG_IMAGE -> "LongImages"
    }
}
