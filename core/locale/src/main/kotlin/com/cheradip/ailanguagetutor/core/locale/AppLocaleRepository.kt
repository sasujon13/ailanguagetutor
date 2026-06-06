package com.cheradip.ailanguagetutor.core.locale

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.model.LanguageCatalogEntry
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.appLocaleDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_locale")

data class AppLocaleState(
    val languageCode: String = AppStrings.DEFAULT_LANG,
    val regionCode: String = AppStrings.DEFAULT_REGION,
    val flagEmoji: String = "🇺🇸",
    val displayName: String = "English (US)",
)

@Singleton
class AppLocaleRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    moshi: Moshi,
) {
    private val keyLang = stringPreferencesKey("preferred_lang")
    private val keyRegion = stringPreferencesKey("preferred_lang_region")
    private val keyFlag = stringPreferencesKey("preferred_lang_flag")
    private val keyDisplay = stringPreferencesKey("preferred_lang_display")
    private val keyStringsPrefix = "app_strings_"

    private val mapAdapter = moshi.adapter<Map<String, String>>(
        Types.newParameterizedType(Map::class.java, String::class.java, String::class.java),
    )

    val localeState: Flow<AppLocaleState> = context.appLocaleDataStore.data.map { prefs ->
        AppLocaleState(
            languageCode = prefs[keyLang] ?: AppStrings.DEFAULT_LANG,
            regionCode = prefs[keyRegion] ?: AppStrings.DEFAULT_REGION,
            flagEmoji = prefs[keyFlag] ?: "🇺🇸",
            displayName = prefs[keyDisplay] ?: "English (US)",
        )
    }

    val languageCode: Flow<String> = localeState.map { it.languageCode }

    suspend fun current(): AppLocaleState = localeState.first()

    suspend fun currentLanguageCode(): String = current().languageCode

    suspend fun saveLocale(entry: LanguageCatalogEntry, regionCode: String = entry.flagCountry ?: "") {
        context.appLocaleDataStore.edit { prefs ->
            prefs[keyLang] = entry.code.lowercase()
            prefs[keyRegion] = regionCode.ifBlank { entry.flagCountry ?: AppStrings.DEFAULT_REGION }
            prefs[keyFlag] = entry.flagEmoji
            prefs[keyDisplay] = displayName(entry)
        }
    }

    suspend fun translatedStrings(languageCode: String): Map<String, String> {
        if (languageCode.equals(AppStrings.DEFAULT_LANG, ignoreCase = true)) {
            return AppStrings.english
        }
        val prefs = context.appLocaleDataStore.data.first()
        val json = prefs[stringPreferencesKey(keyStringsPrefix + languageCode.lowercase())] ?: return AppStrings.english
        return runCatching { mapAdapter.fromJson(json) }.getOrNull() ?: AppStrings.english
    }

    suspend fun saveTranslatedStrings(languageCode: String, strings: Map<String, String>) {
        val json = mapAdapter.toJson(strings) ?: return
        context.appLocaleDataStore.edit { prefs ->
            prefs[stringPreferencesKey(keyStringsPrefix + languageCode.lowercase())] = json
        }
    }

    fun displayName(entry: LanguageCatalogEntry): String {
        val native = entry.nativeName.takeIf { it.isNotBlank() && it != entry.name }
        return if (native != null) "${entry.name} ($native)" else entry.name
    }

    companion object {
        val POPULAR_CODES = listOf("en", "es", "fr", "de", "bn", "hi", "zh", "ar", "pt", "ja")
    }
}
