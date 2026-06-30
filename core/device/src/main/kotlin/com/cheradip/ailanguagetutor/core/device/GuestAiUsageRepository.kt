package com.cheradip.ailanguagetutor.core.device

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.cheradip.ailanguagetutor.core.auth.AuthRepository
import com.cheradip.ailanguagetutor.core.model.GUEST_AI_REQUEST_LIMIT
import com.cheradip.ailanguagetutor.core.model.GuestAiLimitReachedException
import com.cheradip.ailanguagetutor.core.network.AiltDeviceService
import com.cheradip.ailanguagetutor.core.network.GuestAiRecordRequest
import com.cheradip.ailanguagetutor.core.network.GuestAiSyncRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.guestAiDataStore: DataStore<Preferences> by preferencesDataStore(name = "guest_ai_usage")

@Singleton
class GuestAiUsageRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceFingerprintProvider: DeviceFingerprintProvider,
    private val deviceService: AiltDeviceService,
    private val authRepository: AuthRepository,
    private val guestAiGateNotifier: GuestAiGateNotifier,
) {
    private val keyCount = intPreferencesKey("request_count")
    private val keyLimit = intPreferencesKey("request_limit")

    val requestCount: Flow<Int> = context.guestAiDataStore.data.map { prefs ->
        prefs[keyCount] ?: 0
    }

    val guestAiLimit: Flow<Int> = context.guestAiDataStore.data.map { prefs ->
        prefs[keyLimit] ?: GUEST_AI_REQUEST_LIMIT
    }

    val requiresLogin: Flow<Boolean> = context.guestAiDataStore.data.map { prefs ->
        val count = prefs[keyCount] ?: 0
        val limit = prefs[keyLimit] ?: GUEST_AI_REQUEST_LIMIT
        count >= limit
    }

    suspend fun isLoggedIn(): Boolean = authRepository.currentUser.first() != null

    suspend fun syncFromServer() {
        if (isLoggedIn()) return
        val local = readLocalCount()
        val deviceId = deviceFingerprintProvider.deviceId()
        runCatching {
            deviceService.syncGuestAiUsage(GuestAiSyncRequest(deviceId = deviceId, localCount = local))
        }.onSuccess { response ->
            saveLocalCount(response.count.coerceAtLeast(local))
            saveLocalLimit(response.limit)
        }
    }

    suspend fun ensureGuestCanUseAi() {
        if (isLoggedIn()) return
        syncFromServer()
        val count = readLocalCount()
        val limit = readLocalLimit()
        if (count >= limit) {
            guestAiGateNotifier.notifyLoginRequired()
            throw GuestAiLimitReachedException(count, limit)
        }
    }

    suspend fun recordGuestAiUsage() {
        if (isLoggedIn()) return
        val nextLocal = readLocalCount() + 1
        saveLocalCount(nextLocal)
        val deviceId = deviceFingerprintProvider.deviceId()
        runCatching {
            deviceService.recordGuestAiUsage(GuestAiRecordRequest(deviceId = deviceId))
        }.onSuccess { response ->
            saveLocalCount(maxOf(nextLocal, response.count))
            saveLocalLimit(response.limit)
            if (response.requiresLogin) {
                guestAiGateNotifier.notifyLoginRequired()
            }
        }
    }

    private suspend fun readLocalCount(): Int =
        context.guestAiDataStore.data.first()[keyCount] ?: 0

    private suspend fun readLocalLimit(): Int =
        context.guestAiDataStore.data.first()[keyLimit] ?: GUEST_AI_REQUEST_LIMIT

    private suspend fun saveLocalCount(count: Int) {
        context.guestAiDataStore.edit { prefs ->
            prefs[keyCount] = count.coerceAtLeast(0)
        }
    }

    private suspend fun saveLocalLimit(limit: Int) {
        if (limit <= 0) return
        context.guestAiDataStore.edit { prefs ->
            prefs[keyLimit] = limit
        }
    }
}
