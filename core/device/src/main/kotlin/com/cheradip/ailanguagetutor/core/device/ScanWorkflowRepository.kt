package com.cheradip.ailanguagetutor.core.device

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ScanWorkflowStage {
    SCANNER,
    OCR,
}

data class ScanWorkflowSession(
    val documentId: Long,
    val stage: ScanWorkflowStage,
    val mode: String = "camera",
    val scanOnly: Boolean = false,
    val selectedPageId: Long? = null,
    val activeTool: String? = null,
)

private val Context.scanWorkflowDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "scan_workflow",
)

@Singleton
class ScanWorkflowRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyDocumentId = longPreferencesKey("document_id")
    private val keyStage = stringPreferencesKey("stage")
    private val keyMode = stringPreferencesKey("mode")
    private val keyScanOnly = booleanPreferencesKey("scan_only")
    private val keySelectedPageId = longPreferencesKey("selected_page_id")
    private val keyActiveTool = stringPreferencesKey("active_tool")

    val session: Flow<ScanWorkflowSession?> = context.scanWorkflowDataStore.data.map { prefs ->
        val docId = prefs[keyDocumentId] ?: return@map null
        if (docId <= 0L) return@map null
        ScanWorkflowSession(
            documentId = docId,
            stage = when (prefs[keyStage]) {
                ScanWorkflowStage.OCR.name -> ScanWorkflowStage.OCR
                else -> ScanWorkflowStage.SCANNER
            },
            mode = prefs[keyMode] ?: "camera",
            scanOnly = prefs[keyScanOnly] ?: false,
            selectedPageId = prefs[keySelectedPageId]?.takeIf { it > 0L },
            activeTool = prefs[keyActiveTool],
        )
    }

    suspend fun currentSession(): ScanWorkflowSession? = session.first()

    suspend fun save(session: ScanWorkflowSession) {
        context.scanWorkflowDataStore.edit { prefs ->
            prefs[keyDocumentId] = session.documentId
            prefs[keyStage] = session.stage.name
            prefs[keyMode] = session.mode
            prefs[keyScanOnly] = session.scanOnly
            if (session.selectedPageId != null) {
                prefs[keySelectedPageId] = session.selectedPageId
            } else {
                prefs.remove(keySelectedPageId)
            }
            if (session.activeTool != null) {
                prefs[keyActiveTool] = session.activeTool
            } else {
                prefs.remove(keyActiveTool)
            }
        }
    }

    suspend fun clear() {
        context.scanWorkflowDataStore.edit { it.clear() }
    }
}
