package com.cheradip.packbuilder

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.file.Path
import java.time.Instant
import kotlin.io.path.writeText

object ManifestWriter {
    @JsonClass(generateAdapter = true)
    data class PackMetadata(
        val languageCode: String,
        val version: String,
        val schemaVersion: Int = 1,
        val wordCount: Int,
        val builtAt: String,
        val sha256: String,
        val sizeBytes: Long,
        val format: String = "sqlite-zip",
    )

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(PackMetadata::class.java)

    fun write(
        languageCode: String,
        version: String,
        wordCount: Int,
        sha256: String,
        sizeBytes: Long,
        outputPath: Path,
    ) {
        val metadata = PackMetadata(
            languageCode = languageCode.lowercase(),
            version = version,
            wordCount = wordCount,
            builtAt = Instant.now().toString(),
            sha256 = sha256,
            sizeBytes = sizeBytes,
        )
        outputPath.writeText(adapter.toJson(metadata))
    }
}
