package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.network.ApiSettingsRepository
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HomeAiSettingsInitializer @Inject constructor(
    private val homeAiSettings: HomeAiSettingsRepository,
    private val apiSettings: ApiSettingsRepository,
) {
    /** Call from [android.app.Application.onCreate] — enable Home AI + canonical URLs. */
    fun ensureProductionSettings() {
        runBlocking {
            apiSettings.migrateLegacyHomeAiReachabilityTimeoutIfNeeded()
            homeAiSettings.ensureProductionSettings()
        }
    }
}
