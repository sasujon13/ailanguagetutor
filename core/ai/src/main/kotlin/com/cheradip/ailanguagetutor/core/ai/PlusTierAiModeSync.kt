package com.cheradip.ailanguagetutor.core.ai

import com.cheradip.ailanguagetutor.core.billing.AccessState
import com.cheradip.ailanguagetutor.core.billing.BillingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** When subscription upgrades to Plus, default AI mode to High Quality (mode 5). */
@Singleton
class PlusTierAiModeSync @Inject constructor(
    billingRepository: BillingRepository,
    aiModePrefs: AiModePreferenceRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    init {
        scope.launch {
            var wasPlus = billingRepository.accessState.value == AccessState.PLUS_ACTIVE
            billingRepository.accessState.collect { state ->
                    val isPlus = state == AccessState.PLUS_ACTIVE
                    if (isPlus && !wasPlus) {
                        aiModePrefs.activateHighQualityForPlus()
                    }
                    wasPlus = isPlus
                }
        }
    }
}
