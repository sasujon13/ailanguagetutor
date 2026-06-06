package com.cheradip.ailanguagetutor.core.ai

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.model.GrammarDepth
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.grammarDataStore: DataStore<Preferences> by preferencesDataStore(name = "grammar_prefs")

@Singleton
class GrammarPreferenceRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyDepth = intPreferencesKey("grammar_depth")

    val depth: Flow<GrammarDepth> = context.grammarDataStore.data.map { prefs ->
        GrammarDepth.fromId(prefs[keyDepth] ?: GrammarDepth.WORD.id)
    }

    suspend fun current(): GrammarDepth = depth.first()

    suspend fun save(depth: GrammarDepth) {
        context.grammarDataStore.edit { prefs ->
            prefs[keyDepth] = depth.id
        }
    }
}
