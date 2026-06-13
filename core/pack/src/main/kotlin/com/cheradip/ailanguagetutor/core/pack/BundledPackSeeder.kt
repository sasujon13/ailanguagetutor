package com.cheradip.ailanguagetutor.core.pack

import android.content.Context
import com.cheradip.ailanguagetutor.core.database.dao.LanguagePackDao
import com.cheradip.ailanguagetutor.core.database.entity.LanguagePackStateEntity
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BundledPackSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val languagePackDao: LanguagePackDao,
    private val packConnector: PackDatabaseConnector,
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
    private val packAdapter = moshi.adapter(SamplePackFile::class.java)

    suspend fun ensureBundledPacks() = withContext(Dispatchers.IO) {
        val packsDir = File(context.filesDir, "packs").apply { mkdirs() }
        for (code in RichBundledLexicon.BUNDLED_LANGUAGE_CODES) {
            val existing = languagePackDao.getByCode(code)
            val needsUpgrade = existing == null ||
                existing.version < RichBundledLexicon.PACK_VERSION ||
                PackInstaller.dictionaryDb(existing.localPath)?.isFile != true
            if (!needsUpgrade) continue

            val rich = RichBundledLexicon.samplePack(code) ?: continue
            val jsonDest = File(packsDir, "$code-rich-v${RichBundledLexicon.PACK_VERSION}.json")
            jsonDest.writeText(packAdapter.toJson(rich) ?: continue)
            val activeCount = languagePackDao.activeCount()
            val shouldActivate = existing?.isActive == true || activeCount < LanguagePackRepository.MAX_ACTIVE_PACKS
            languagePackDao.upsert(
                LanguagePackStateEntity(
                    id = existing?.id ?: 0,
                    languageCode = code,
                    version = RichBundledLexicon.PACK_VERSION,
                    localPath = jsonDest.absolutePath,
                    isActive = shouldActivate || code == "en",
                    downloadedAt = System.currentTimeMillis(),
                ),
            )
            packConnector.invalidateCache(code)
            packConnector.loadPack(code)
        }
    }
}
