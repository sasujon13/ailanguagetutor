package com.cheradip.ailanguagetutor.core.pack

import android.content.Context
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.database.dao.LanguagePackDao
import com.cheradip.ailanguagetutor.core.database.entity.LanguagePackStateEntity
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.WordDefinition
import com.cheradip.ailanguagetutor.core.model.WorldLanguageCatalog
import com.cheradip.ailanguagetutor.core.network.AiltLanguageService
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@JsonClass(generateAdapter = true)
data class SamplePackFile(
    val version: Int = 1,
    val languageCode: String = "en",
    val entries: Map<String, List<String>> = emptyMap(),
    val phrases: Map<String, String> = emptyMap(),
    val translations: Map<String, String> = emptyMap(),
)

@Singleton
class PackDatabaseConnector @Inject constructor(
    @ApplicationContext private val context: Context,
    private val languagePackDao: LanguagePackDao,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val packAdapter = moshi.adapter(SamplePackFile::class.java)
    private val jsonPackCache = mutableMapOf<String, SamplePackFile>()

    suspend fun loadPack(languageCode: String): SamplePackFile? = withContext(Dispatchers.IO) {
        jsonPackCache[languageCode]?.let { return@withContext it }
        val state = languagePackDao.getByCode(languageCode)
        val pack = when {
            state?.localPath != null -> state.localPath?.let { loadJsonPackIfPresent(it) }
            languageCode.equals("en", ignoreCase = true) -> {
                context.assets.open("packs/en/sample_dictionary.json").bufferedReader().use {
                    packAdapter.fromJson(it.readText())
                }
            }
            else -> null
        }
        pack?.also { jsonPackCache[languageCode] = it }
    }

    private fun loadJsonPackIfPresent(localPath: String): SamplePackFile? {
        val file = File(localPath)
        if (!file.isFile || !file.name.endsWith(".json", ignoreCase = true)) return null
        return file.readText().let { packAdapter.fromJson(it) }
    }

    suspend fun lookupWord(word: String, preferredLanguageCode: String): List<String>? = withContext(Dispatchers.IO) {
        val normalized = normalizeToken(word)
        if (normalized.isBlank()) return@withContext null

        val tried = mutableSetOf<String>()
        suspend fun tryLanguage(lang: String): List<String>? {
            val code = lang.lowercase()
            if (!tried.add(code)) return null
            return lookupInLanguage(normalized, code)
        }

        tryLanguage(preferredLanguageCode)?.let { return@withContext it }

        languagePackDao.listDownloaded()
            .sortedWith(compareByDescending<LanguagePackStateEntity> { it.isActive }.thenByDescending { it.downloadedAt })
            .forEach { state ->
                tryLanguage(state.languageCode)?.let { return@withContext it }
            }

        tryLanguage("en")
    }

    private suspend fun lookupInLanguage(normalized: String, languageCode: String): List<String>? {
        val state = languagePackDao.getByCode(languageCode)
        PackInstaller.dictionaryDb(state?.localPath)?.let { db ->
            PackSqliteReader.lookupWord(db, normalized, languageCode)?.let { return it }
        }
        return loadPack(languageCode)?.entries?.get(normalized)
    }

    suspend fun lookupPhrase(phrase: String, languageCode: String): String? = withContext(Dispatchers.IO) {
        val normalized = phrase.lowercase().trim()
        val state = languagePackDao.getByCode(languageCode)
        PackInstaller.translationDb(state?.localPath)?.let { db ->
            PackSqliteReader.lookupPhrase(db, normalized, languageCode)?.let { return@withContext it }
        }
        loadPack(languageCode)?.phrases?.get(normalized)
    }

    suspend fun pivotTranslation(text: String, targetLang: String): String? = withContext(Dispatchers.IO) {
        val normalized = text.lowercase().trim()
        val enState = languagePackDao.getByCode("en")
        PackInstaller.translationDb(enState?.localPath)?.let { db ->
            PackSqliteReader.pivotTranslation(db, normalized, "en", targetLang)?.let { return@withContext it }
        }
        val sourceState = languagePackDao.getByCode(targetLang)
        PackInstaller.translationDb(sourceState?.localPath)?.let { db ->
            PackSqliteReader.pivotTranslation(db, normalized, targetLang, "en")?.let { return@withContext it }
        }
        loadPack("en")?.translations?.get("$normalized|$targetLang")
    }

    private fun normalizeToken(word: String): String =
        word.lowercase().trim().trim('(', ')', '.', ',', ';', ':', '!', '?', '"')
}

@Singleton
class LanguageCatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(WorldLanguageCatalog::class.java)
    private var cache: List<LanguageCatalogEntry>? = null

    suspend fun getAll(): List<LanguageCatalogEntry> = withContext(Dispatchers.IO) {
        cache ?: run {
            context.assets.open("catalog/world_languages.json").bufferedReader().use { reader ->
                adapter.fromJson(reader.readText())?.languages.orEmpty()
            }.also { cache = it }
        }
    }

    suspend fun getByCode(code: String): LanguageCatalogEntry? =
        getAll().firstOrNull { it.code.equals(code, ignoreCase = true) }
}

@Singleton
class DictionaryRepository @Inject constructor(
    private val packConnector: PackDatabaseConnector,
) {
    suspend fun lookup(word: String, languageCode: String): WordDefinition? =
        withContext(Dispatchers.Default) {
            val normalized = word.lowercase().trim().trim('(', ')', '.', ',', ';', ':', '!', '?', '"')
            if (normalized.isBlank()) return@withContext null
            val fromPack = packConnector.lookupWord(word, languageCode)
            val meanings = fromPack ?: fallback[normalized]
            meanings?.let {
                WordDefinition(
                    word = word,
                    languageCode = languageCode,
                    meanings = it.take(3),
                )
            }
        }

    companion object {
        private val fallback = mapOf(
            "bonjour" to listOf("French greeting: good day"),
            "merci" to listOf("French: thank you"),
            "français" to listOf("French language", "French people"),
        )
    }
}

@Singleton
class LanguagePackRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val languagePackDao: LanguagePackDao,
    private val languageService: AiltLanguageService,
    private val packConnector: PackDatabaseConnector,
    private val okHttpClient: OkHttpClient,
    private val appConfig: AppConfig,
) {
    companion object {
        const val MAX_ACTIVE_PACKS = 3
    }

    fun observeActive(): Flow<List<LanguagePackStateEntity>> = languagePackDao.observeActive()

    fun observeActiveLanguageCodes(): Flow<List<String>> =
        observeActive().map { packs -> packs.map { it.languageCode.lowercase() } }

    fun observeAll(): Flow<List<LanguagePackStateEntity>> = languagePackDao.observeAll()

    suspend fun downloadAndActivate(languageCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val info = runCatching { languageService.downloadInfo(languageCode) }.getOrNull()
            val packsDir = File(context.filesDir, "packs").apply { mkdirs() }
            val zipDest = File(packsDir, "$languageCode.zip")
            val jsonDest = File(packsDir, "$languageCode.json")
            val existingState = languagePackDao.getByCode(languageCode)
            val hasSqlitePack = PackInstaller.dictionaryDb(existingState?.localPath)?.isFile == true
            if (!hasSqlitePack || info != null) {
                val downloadUrls = buildList {
                    info?.downloadUrl?.let { add(it) }
                    add(packFileUrl(languageCode))
                }.distinct()
                var downloaded = false
                for (url in downloadUrls) {
                    if (downloadPackFromUrl(url, zipDest)) {
                        downloaded = true
                        break
                    }
                }
                val localPath = if (downloaded && zipDest.isFile) {
                    val installedDb = PackInstaller.installPack(zipDest, languageCode, packsDir)
                    installedDb.parentFile?.absolutePath ?: installedDb.absolutePath
                } else if (jsonDest.isFile) {
                    jsonDest.absolutePath
                } else if (languageCode.equals("en", ignoreCase = true)) {
                    copyBundledFallback(jsonDest)
                } else {
                    error("SQLite pack for $languageCode not available — start cloud-api and retry")
                }
                val version = info?.version ?: 1
                val existing = languagePackDao.getByCode(languageCode)
                val activeCount = languagePackDao.activeCount()
                val shouldActivate = existing?.isActive == true || activeCount < MAX_ACTIVE_PACKS
                languagePackDao.upsert(
                    LanguagePackStateEntity(
                        id = existing?.id ?: 0,
                        languageCode = languageCode,
                        version = version,
                        localPath = localPath,
                        isActive = shouldActivate,
                        downloadedAt = System.currentTimeMillis(),
                    ),
                )
            }
            packConnector.loadPack(languageCode)
            Unit
        }
    }

    private fun packFileUrl(languageCode: String): String {
        val base = appConfig.apiBaseUrl.trimEnd('/')
        return "$base/languages/${languageCode.lowercase()}/file"
    }

    private fun downloadPackFromUrl(url: String, dest: File): Boolean {
        return runCatching {
            val request = Request.Builder().url(url).get().build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return false
                val body = response.body ?: return false
                dest.outputStream().use { out -> body.byteStream().copyTo(out) }
            }
            dest.length() > 0
        }.getOrDefault(false)
    }

    private fun copyBundledFallback(dest: File): String {
        context.assets.open("packs/en/sample_dictionary.json").use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        return dest.absolutePath
    }

    suspend fun setActive(languageCode: String, active: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val state = languagePackDao.getByCode(languageCode)
                ?: error("Download pack first")
            if (active && !state.isActive && languagePackDao.activeCount() >= MAX_ACTIVE_PACKS) {
                throw MaxActivePacksException()
            }
            languagePackDao.update(state.copy(isActive = active))
        }
    }
}
