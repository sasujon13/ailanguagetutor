package com.cheradip.packbuilder

import com.cheradip.packbuilder.model.WordSeed
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.file.Path
import kotlin.io.path.writeText

object FrequencyImporter {
    @JsonClass(generateAdapter = true)
    data class FrequencyEntry(val lemma: String, val score: Double)

    @JsonClass(generateAdapter = true)
    data class FrequencyFile(
        val languageCode: String,
        val entries: List<FrequencyEntry>,
    )

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(FrequencyFile::class.java)

    fun write(languageCode: String, words: List<WordSeed>, outputPath: Path) {
        val payload = FrequencyFile(
            languageCode = languageCode.lowercase(),
            entries = words
                .sortedByDescending { it.frequencyScore }
                .map { FrequencyEntry(it.lemma.lowercase(), it.frequencyScore) },
        )
        outputPath.writeText(adapter.toJson(payload))
    }
}
