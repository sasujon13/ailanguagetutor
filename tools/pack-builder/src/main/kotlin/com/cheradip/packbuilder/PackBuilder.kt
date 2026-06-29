package com.cheradip.packbuilder

import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.WorldLanguageCatalog
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.Properties
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.deleteRecursively

@OptIn(ExperimentalPathApi::class)
class PackBuilder(private val projectRoot: Path) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val catalogAdapter = moshi.adapter(WorldLanguageCatalog::class.java)

    private val outputRoot: Path = projectRoot.resolve("tools/pack-builder/output")
    private val catalogPath: Path = projectRoot.resolve("catalog/world_languages.json")

    fun buildLanguage(languageCode: String, version: String): Path {
        val catalog = loadCatalog()
        val entry = catalog.languages.firstOrNull { it.code.equals(languageCode, ignoreCase = true) }
            ?: error("Language not found in catalog: $languageCode")
        return buildForEntry(entry, version)
    }

    fun buildTier1(version: String): List<Path> {
        val catalog = loadCatalog()
        return catalog.languages.filter { it.tier == 1 }.map { buildForEntry(it, version) }
    }

    fun buildAll(version: String): List<Path> {
        val catalog = loadCatalog()
        return catalog.languages.map { buildForEntry(it, version) }
    }

    private fun buildForEntry(entry: LanguageCatalogEntry, version: String): Path {
        val code = entry.code.lowercase()
        val versionInt = versionMajorInt(version)
        val workDir = Files.createTempDirectory("pack-build-$code-")
        try {
            val seed = StarterVocabularyGenerator.generate(entry)
            val dictPath = workDir.resolve("dictionary.db")
            val transPath = workDir.resolve("translation.db")
            val freqPath = workDir.resolve("frequency.json")
            val metaPath = workDir.resolve("metadata.json")

            val wordCount = DictionaryImporter.import(code, seed.words, dictPath)
            TranslationImporter.import(code, seed, transPath)
            FrequencyImporter.write(code, seed.words, freqPath)

            val outDir = outputRoot.resolve(code)
            Files.createDirectories(outDir)
            val zipPath = outDir.resolve("v$versionInt.zip")

            val zipResult = PackZipper.zip(
                workDir,
                zipPath,
                listOf("dictionary.db", "translation.db", "frequency.json"),
            )

            ManifestWriter.write(code, version, wordCount, zipResult.sha256, zipResult.sizeBytes, metaPath)

            // Re-zip with metadata.json included
            val finalZip = PackZipper.zip(
                workDir,
                zipPath,
                listOf("dictionary.db", "translation.db", "frequency.json", "metadata.json"),
            )

            val validation = PackValidator.validateZip(finalZip.zipPath)
            check(validation.ok) { "Validation failed for $code: ${validation.messages}" }

            println("Built ${finalZip.zipPath} (${finalZip.sizeBytes} bytes, $wordCount words, sha256=${finalZip.sha256.take(12)}…)")
            return finalZip.zipPath
        } finally {
            workDir.deleteRecursively()
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun syncToAiltApi(version: String): Int {
        val versionInt = versionMajorInt(version)
        val ailtPacks = resolveAiltPacksDir()
        var count = 0
        if (!Files.isDirectory(outputRoot)) return 0
        Files.list(outputRoot).use { stream ->
            stream.filter { Files.isDirectory(it) }.forEach { langDir ->
                val zip = langDir.resolve("v$versionInt.zip")
                if (!Files.isRegularFile(zip)) return@forEach
                val destDir = ailtPacks.resolve(langDir.fileName.toString())
                Files.createDirectories(destDir)
                Files.copy(zip, destDir.resolve("v$versionInt.zip"), StandardCopyOption.REPLACE_EXISTING)
                count++
            }
        }
        println("Synced $count packs to $ailtPacks")
        return count
    }

    private fun resolveAiltPacksDir(): Path {
        System.getenv("AILT_PACKS_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return Paths.get(it)
        }
        val localEnv = projectRoot.resolve("local.env.properties")
        if (Files.isRegularFile(localEnv)) {
            val props = Properties()
            localEnv.toFile().inputStream().use { props.load(it) }
            props.getProperty("AILT_PACKS_DIR")?.trim()?.takeIf { it.isNotEmpty() }?.let {
                return Paths.get(it)
            }
        }
        return Paths.get("D:/VSCode/cheradip/bcheradip/ailt_api/packs")
    }

    private fun loadCatalog(): WorldLanguageCatalog {
        val json = catalogPath.toFile().readText()
        return catalogAdapter.fromJson(json) ?: error("Failed to parse catalog")
    }

    private fun versionMajorInt(version: String): Int =
        version.substringBefore('.').toIntOrNull() ?: 1
}
