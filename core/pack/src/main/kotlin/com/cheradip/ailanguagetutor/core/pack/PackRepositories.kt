package com.cheradip.ailanguagetutor.core.pack

import android.content.Context
import com.cheradip.ailanguagetutor.core.common.AppConfig
import com.cheradip.ailanguagetutor.core.database.dao.LanguagePackDao
import com.cheradip.ailanguagetutor.core.database.dao.TranslationCacheDao
import com.cheradip.ailanguagetutor.core.database.entity.LanguagePackStateEntity
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.cheradip.ailanguagetutor.core.model.WordDefinition
import com.cheradip.ailanguagetutor.core.model.WorldLanguageCatalog
import com.cheradip.ailanguagetutor.core.network.AiltLanguageService
import com.cheradip.ailanguagetutor.core.network.NetworkErrorFormatter
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
    private val packUsageTracker: PackUsageTracker,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val packAdapter = moshi.adapter(SamplePackFile::class.java)
    private val jsonPackCache = mutableMapOf<String, SamplePackFile>()

    fun invalidateCache(languageCode: String) {
        for (code in LanguageCodeResolver.packFallbackChain(languageCode)) {
            jsonPackCache.remove(code.lowercase())
        }
    }

    suspend fun loadPack(languageCode: String): SamplePackFile? = withContext(Dispatchers.IO) {
        for (code in LanguageCodeResolver.packFallbackChain(languageCode)) {
            jsonPackCache[code]?.let { return@withContext it }
            val state = languagePackDao.getByCode(code)
            val downloaded = when {
                state?.localPath != null -> state.localPath?.let { loadJsonPackIfPresent(it) }
                else -> null
            }
            val bundled = RichBundledLexicon.samplePack(code)
                ?: if (LanguageCodeResolver.normalizePackCode(code) == "en") RichBundledLexicon.samplePack("en") else null
            val pack = mergeSamplePacks(bundled, downloaded)
            if (pack != null) {
                jsonPackCache[code] = pack
                packUsageTracker.record("load_pack", languageCode, code, "json_or_bundled")
                return@withContext pack
            }
        }
        null
    }

    private fun mergeSamplePacks(vararg packs: SamplePackFile?): SamplePackFile? {
        val valid = packs.filterNotNull()
        if (valid.isEmpty()) return null
        return valid.reduce { acc, next ->
            SamplePackFile(
                version = maxOf(acc.version, next.version),
                languageCode = next.languageCode.ifBlank { acc.languageCode },
                entries = acc.entries + next.entries,
                phrases = acc.phrases + next.phrases,
                translations = acc.translations + next.translations,
            )
        }
    }

    private fun loadJsonPackIfPresent(localPath: String): SamplePackFile? {
        val file = File(localPath)
        if (!file.isFile || !file.name.endsWith(".json", ignoreCase = true)) return null
        return file.readText().let { packAdapter.fromJson(it) }
    }

    suspend fun lookupWordTranslation(word: String, sourceLang: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            val src = LanguageCodeResolver.normalizePackCode(sourceLang)
            val tgt = LanguageCodeResolver.normalizePackCode(targetLang)
            if (src == tgt) return@withContext DictionaryLookupHelper.normalize(word).ifBlank { null }

            for (form in DictionaryLookupHelper.lookupForms(word, sourceLang)) {
                lookupWordTranslationOnce(form, sourceLang, targetLang, src, tgt)?.let { return@withContext it }
            }
            null
        }

    private suspend fun lookupWordTranslationOnce(
        word: String,
        sourceLang: String,
        targetLang: String,
        src: String,
        tgt: String,
    ): String? {
        val normalized = DictionaryLookupHelper.normalize(word)
        if (normalized.isBlank()) return null

        val triedPacks = mutableSetOf<String>()
        suspend fun tryPack(packCode: String): String? {
            val key = packCode.lowercase()
            if (!triedPacks.add(key)) return null
            return lookupWordDirect(normalized, key, src, tgt)
        }

        for (packCode in LanguageCodeResolver.packFallbackChain(sourceLang)) {
            tryPack(packCode)?.let { hit ->
                packUsageTracker.record("word_translation", sourceLang, packCode, "direct")
                return hit
            }
        }
        for (packCode in LanguageCodeResolver.packFallbackChain(targetLang)) {
            tryPack(packCode)?.let { hit ->
                packUsageTracker.record("word_translation", sourceLang, packCode, "direct_target")
                return hit
            }
        }
        for (state in downloadedPackStates()) {
            tryPack(state.languageCode)?.let { hit ->
                packUsageTracker.record("word_translation", sourceLang, state.languageCode, "downloaded")
                return hit
            }
        }
        lookupViaEnglishPivot(normalized, sourceLang, src, tgt)?.let { hit ->
            packUsageTracker.record("word_translation", sourceLang, "en+pivot", "english_pivot")
            return hit
        }
        return null
    }

    private suspend fun downloadedPackStates(): List<LanguagePackStateEntity> =
        languagePackDao.listDownloaded()
            .sortedWith(
                compareByDescending<LanguagePackStateEntity> { it.isActive }
                    .thenByDescending { it.downloadedAt },
            )

    private suspend fun lookupWordDirect(
        normalized: String,
        packCode: String,
        sourceLang: String,
        targetLang: String,
    ): String? {
        languagePackDao.getByCode(packCode)?.localPath?.let { path ->
            PackInstaller.translationDb(path)?.let { db ->
                PackSqliteReader.pivotTranslation(db, normalized, sourceLang, targetLang)?.let { return it }
            }
        }
        loadPack(packCode)?.translations?.get("$normalized|$targetLang")?.let { return it }
        if (sourceLang == "en") {
            loadPack("en")?.translations?.get("$normalized|$targetLang")?.let { return it }
        }
        if (targetLang != "en" && sourceLang != "en") {
            resolveEnglishLemma(normalized, sourceLang)?.let { english ->
                lookupEnglishToTarget(english, targetLang)
                    ?: lookupWordDirect(english, "en", "en", targetLang)
                    ?: lookupJsonTranslation(english, "en", targetLang)
            }?.let { return it }
        }
        return null
    }

    private suspend fun lookupViaEnglishPivot(
        normalized: String,
        sourceLang: String,
        src: String,
        tgt: String,
    ): String? {
        if (src == "en") {
            return lookupEnglishToTarget(normalized, tgt)
        }
        var english: String? = null
        for (packCode in LanguageCodeResolver.packFallbackChain(sourceLang)) {
            val hit = lookupWordDirect(normalized, packCode, src, "en")
            if (hit != null) {
                english = hit
                break
            }
        }
        if (english == null) {
            for (state in downloadedPackStates()) {
                val hit = lookupWordDirect(normalized, state.languageCode, src, "en")
                if (hit != null) {
                    english = hit
                    break
                }
            }
        }
        english = english ?: lookupJsonTranslation(normalized, sourceLang, "en")
        english = english ?: resolveEnglishLemma(normalized, sourceLang)
        if (english == null) return null
        return lookupEnglishToTarget(english, tgt)
    }

    private suspend fun lookupEnglishToTarget(english: String, targetLang: String): String? {
        if (targetLang == "en") return english
        val triedPacks = mutableSetOf<String>()
        suspend fun tryPack(packCode: String): String? {
            val key = packCode.lowercase()
            if (!triedPacks.add(key)) return null
            for (form in DictionaryLookupHelper.lookupForms(english, "en")) {
                lookupWordDirect(form, key, "en", targetLang)?.let { return it }
                lookupJsonTranslation(form, "en", targetLang)?.let { return it }
            }
            return null
        }
        for (packCode in LanguageCodeResolver.packFallbackChain(targetLang)) {
            tryPack(packCode)?.let { return it }
        }
        for (state in downloadedPackStates()) {
            tryPack(state.languageCode)?.let { return it }
        }
        return null
    }

    private suspend fun resolveEnglishLemma(word: String, sourceLang: String): String? {
        if (LanguageCodeResolver.isEnglish(sourceLang)) {
            return DictionaryLookupHelper.normalize(word).ifBlank { null }
        }
        for (form in DictionaryLookupHelper.lookupForms(word, sourceLang)) {
            for (packCode in LanguageCodeResolver.packFallbackChain(sourceLang)) {
                englishLemmaFromPack(form, packCode)?.let { return it }
            }
            for (state in downloadedPackStates()) {
                englishLemmaFromPack(form, state.languageCode)?.let { return it }
            }
        }
        return null
    }

    private suspend fun englishLemmaFromPack(form: String, packCode: String): String? {
        val code = LanguageCodeResolver.normalizePackCode(packCode)
        loadPack(code)?.entries?.get(form)?.firstOrNull()?.let { gloss ->
            return DictionaryLookupHelper.englishLemmaFromGloss(gloss)
        }
        languagePackDao.getByCode(packCode)?.localPath?.let { path ->
            PackInstaller.dictionaryDb(path)?.let { db ->
                for (candidate in DictionaryLookupHelper.lookupForms(form, code)) {
                    PackSqliteReader.lookupWord(db, candidate, code)?.firstOrNull()?.let { gloss ->
                        return DictionaryLookupHelper.englishLemmaFromGloss(gloss)
                    }
                }
            }
        }
        return null
    }

    private suspend fun lookupJsonTranslation(word: String, sourceLang: String, targetLang: String): String? {
        for (form in DictionaryLookupHelper.lookupForms(word, sourceLang)) {
            for (packCode in LanguageCodeResolver.packFallbackChain(sourceLang)) {
                loadPack(packCode)?.translations?.get("$form|$targetLang")?.let { return it }
            }
        }
        return null
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

        for (code in LanguageCodeResolver.packFallbackChain(preferredLanguageCode)) {
            tryLanguage(code)?.let { return@withContext it }
        }

        languagePackDao.listDownloaded()
            .sortedWith(compareByDescending<LanguagePackStateEntity> { it.isActive }.thenByDescending { it.downloadedAt })
            .forEach { state ->
                tryLanguage(state.languageCode)?.let { return@withContext it }
            }

        tryLanguage("en")
    }

    private suspend fun lookupInLanguage(normalized: String, languageCode: String): List<String>? {
        val state = languagePackDao.getByCode(languageCode)
        val packCode = LanguageCodeResolver.normalizePackCode(languageCode)
        for (form in DictionaryLookupHelper.lookupForms(normalized, languageCode)) {
            PackInstaller.dictionaryDb(state?.localPath)?.let { db ->
                PackSqliteReader.lookupWord(db, form, packCode)?.let { return it }
            }
        }
        loadPack(languageCode)?.entries?.let { entries ->
            lookupInEntries(entries, normalized, languageCode)?.let { return it }
        }
        return null
    }

    private fun lookupInEntries(
        entries: Map<String, List<String>>,
        normalized: String,
        languageCode: String,
    ): List<String>? {
        for (form in DictionaryLookupHelper.lookupForms(normalized, languageCode)) {
            entries[form]?.let { return it }
        }
        return null
    }

    /** Phrase-table lookup only (no word pivot) — safe to call from [pivotTranslation]. */
    private suspend fun lookupPhraseInPacks(phrase: String, sourceLang: String): String? {
        val normalized = phrase.lowercase().trim()
        val src = LanguageCodeResolver.normalizePackCode(sourceLang)
        for (packCode in LanguageCodeResolver.packFallbackChain(sourceLang)) {
            languagePackDao.getByCode(packCode)?.localPath?.let { path ->
                PackInstaller.translationDb(path)?.let { db ->
                    PackSqliteReader.lookupPhrase(db, normalized, src)?.let { hit ->
                        packUsageTracker.record("phrase", sourceLang, packCode, "sqlite")
                        return hit
                    }
                }
            }
            loadPack(packCode)?.phrases?.get(normalized)?.let { hit ->
                packUsageTracker.record("phrase", sourceLang, packCode, "json")
                return hit
            }
        }
        return null
    }

    suspend fun lookupPhrase(phrase: String, sourceLang: String, targetLang: String? = null): String? =
        withContext(Dispatchers.IO) {
            lookupPhraseInPacks(phrase, sourceLang)?.let { return@withContext it }
            val src = LanguageCodeResolver.normalizePackCode(sourceLang)
            val tgt = targetLang?.let { LanguageCodeResolver.normalizePackCode(it) }
            if (tgt != null && !src.equals(tgt, ignoreCase = true)) {
                pivotTranslation(phrase, src, tgt)?.let { return@withContext it }
            }
            null
        }

    suspend fun pivotTranslation(text: String, sourceLang: String, targetLang: String): String? =
        withContext(Dispatchers.IO) {
            val normalized = text.lowercase().trim()
            val src = LanguageCodeResolver.normalizePackCode(sourceLang)
            val tgt = LanguageCodeResolver.normalizePackCode(targetLang)
            if (src == tgt) return@withContext text

            lookupPhraseInPacks(text, sourceLang)?.let { return@withContext it }

            for (packCode in LanguageCodeResolver.packFallbackChain(src)) {
                languagePackDao.getByCode(packCode)?.localPath?.let { path ->
                    PackInstaller.translationDb(path)?.let { db ->
                        PackSqliteReader.pivotTranslation(db, normalized, src, tgt)?.let { hit ->
                            packUsageTracker.record("pivot", sourceLang, packCode, "sqlite")
                            return@withContext hit
                        }
                    }
                }
            }

            val parts = text.split(Regex("""(\s+)"""))
            var anyHit = false
            val built = buildString {
                for (part in parts) {
                    if (part.isBlank() || part.none { ch -> ch.isLetterOrDigit() || Character.isLetterOrDigit(ch) }) {
                        append(part)
                        continue
                    }
                    val translated = lookupWordTranslation(part, src, tgt)
                    if (translated != null) {
                        anyHit = true
                        append(translated)
                    } else {
                        append(part)
                    }
                }
            }
            built.takeIf { anyHit }
        }

    private fun normalizeToken(word: String): String = DictionaryLookupHelper.normalize(word)
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
            if (DictionaryLookupHelper.isNumericToken(word)) {
                return@withContext DictionaryLookupHelper.placeholderDefinition(word, languageCode, true)
            }
            for (form in DictionaryLookupHelper.lookupForms(word, languageCode)) {
                val fromPack = packConnector.lookupWord(form, languageCode)
                if (fromPack != null) {
                    return@withContext WordDefinition(
                        word = word,
                        languageCode = languageCode,
                        meanings = fromPack.take(3),
                    )
                }
            }
            fallback[DictionaryLookupHelper.normalize(word)]?.let {
                return@withContext WordDefinition(
                    word = word,
                    languageCode = languageCode,
                    meanings = it.take(3),
                )
            }
            null
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
    private val translationCacheDao: TranslationCacheDao,
    private val okHttpClient: OkHttpClient,
    private val appConfig: AppConfig,
    private val networkErrors: NetworkErrorFormatter,
) {
    companion object {
        const val MAX_ACTIVE_PACKS = 3
    }

    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val packAdapter = moshi.adapter(SamplePackFile::class.java)

    fun observeActive(): Flow<List<LanguagePackStateEntity>> = languagePackDao.observeActive()

    fun observeActiveLanguageCodes(): Flow<List<String>> =
        observeActive().map { packs -> packs.map { it.languageCode.lowercase() } }

    fun observeAll(): Flow<List<LanguagePackStateEntity>> = languagePackDao.observeAll()

    suspend fun hasDownloadedPacks(): Boolean =
        languagePackDao.listDownloaded().isNotEmpty()

    suspend fun downloadAndActivate(languageCode: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val info = runCatching { languageService.downloadInfo(languageCode) }.getOrNull()
            val packsDir = File(context.filesDir, "packs").apply { mkdirs() }
            val zipDest = File(packsDir, "$languageCode.zip")
            val jsonDest = File(packsDir, "$languageCode.json")
            val existingState = languagePackDao.getByCode(languageCode)
            val hasSqlitePack = PackInstaller.dictionaryDb(existingState?.localPath)?.isFile == true
            if (!hasSqlitePack || info != null) {
                val downloadUrls = resolvePackDownloadUrls(languageCode, info?.downloadUrl)
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
                } else if (RichBundledLexicon.BUNDLED_LANGUAGE_CODES.contains(languageCode.lowercase())) {
                    val rich = RichBundledLexicon.samplePack(languageCode.lowercase()) ?: error("Bundled pack missing")
                    val jsonDest = File(packsDir, "$languageCode-rich.json")
                    jsonDest.writeText(
                        packAdapter.toJson(rich) ?: error("serialize failed"),
                    )
                    jsonDest.absolutePath
                } else if (languageCode.equals("en", ignoreCase = true)) {
                    copyBundledFallback(jsonDest, languageCode)
                } else {
                    error("SQLite pack for $languageCode not available — check cheradip.com/ailt/api and retry")
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
            for (code in LanguageCodeResolver.packFallbackChain(languageCode)) {
                packConnector.invalidateCache(code)
                translationCacheDao.clearForLanguage(code)
            }
            translationCacheDao.clearForLanguage("en")
            packConnector.loadPack(languageCode)
            Unit
        }.recoverCatching { error ->
            throw IllegalStateException(networkErrors.present(error, "Download failed"))
        }
    }

    private fun packFileUrl(languageCode: String): String {
        val base = appConfig.apiBaseUrl.trimEnd('/')
        return "$base/languages/${languageCode.lowercase()}/file"
    }

    /** Canonical App API file URL first — ignore stale download_url hosts from the server DB. */
    private fun resolvePackDownloadUrls(languageCode: String, apiDownloadUrl: String?): List<String> {
        val canonical = packFileUrl(languageCode)
        val normalizedApi = apiDownloadUrl
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { normalizePackDownloadUrl(it, languageCode) }
            ?.takeIf { it != canonical }
        return listOfNotNull(canonical, normalizedApi)
    }

    private fun normalizePackDownloadUrl(url: String, languageCode: String): String {
        val code = languageCode.lowercase()
        val suffix = "/languages/$code/file"
        return if (url.contains(suffix, ignoreCase = true)) {
            packFileUrl(code)
        } else {
            url
        }
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

    private fun copyBundledFallback(dest: File, languageCode: String = "en"): String {
        val rich = RichBundledLexicon.samplePack(languageCode.lowercase())
            ?: RichBundledLexicon.samplePack("en")!!
        dest.writeText(packAdapter.toJson(rich) ?: "{}")
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
