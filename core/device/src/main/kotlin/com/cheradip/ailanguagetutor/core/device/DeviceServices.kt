package com.cheradip.ailanguagetutor.core.device

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.cheradip.ailanguagetutor.core.database.dao.TrialStateDao
import com.cheradip.ailanguagetutor.core.database.entity.TrialStateEntity
import com.cheradip.ailanguagetutor.core.network.AiltDeviceService
import com.cheradip.ailanguagetutor.core.network.DeviceRegisterRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DeviceFingerprintProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun deviceId(): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        return "ailt-${androidId ?: "unknown"}"
    }

    fun model(): String = "${Build.MANUFACTURER} ${Build.MODEL}"

    fun osVersion(): String = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})"
}

@Singleton
class TrialRepository @Inject constructor(
    private val trialStateDao: TrialStateDao,
    private val deviceFingerprintProvider: DeviceFingerprintProvider,
    private val deviceService: AiltDeviceService,
) {
    private val _daysRemaining = MutableStateFlow(7)
    val daysRemaining: StateFlow<Int> = _daysRemaining.asStateFlow()

    suspend fun ensureTrialRegistered() {
        val deviceId = deviceFingerprintProvider.deviceId()
        val existing = trialStateDao.get()
        if (existing != null) {
            refreshDaysRemaining(existing.trialEndsAt)
            return
        }
        val now = System.currentTimeMillis()
        val trialDays = runCatching {
            deviceService.register(
                DeviceRegisterRequest(
                    deviceId = deviceId,
                    model = deviceFingerprintProvider.model(),
                    osVersion = deviceFingerprintProvider.osVersion(),
                ),
            ).trialDaysRemaining
        }.getOrDefault(7)
        val endsAt = now + TimeUnit.DAYS.toMillis(trialDays.toLong())
        trialStateDao.upsert(
            TrialStateEntity(
                deviceId = deviceId,
                trialStartedAt = now,
                trialEndsAt = endsAt,
            ),
        )
        refreshDaysRemaining(endsAt)
    }

    suspend fun isTrialActive(): Boolean {
        val state = trialStateDao.get() ?: return true
        return System.currentTimeMillis() < state.trialEndsAt
    }

    private fun refreshDaysRemaining(trialEndsAt: Long) {
        val remainingMs = trialEndsAt - System.currentTimeMillis()
        _daysRemaining.value = (remainingMs / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(0).toInt()
    }
}
