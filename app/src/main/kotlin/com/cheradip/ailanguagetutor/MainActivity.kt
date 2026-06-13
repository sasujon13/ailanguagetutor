package com.cheradip.ailanguagetutor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.audio.VoicePreferenceRepository
import com.cheradip.ailanguagetutor.core.auth.AuthRepository
import com.cheradip.ailanguagetutor.core.billing.BillingRepository
import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.billing.PromoRepository
import com.cheradip.ailanguagetutor.core.billing.ReferralRepository
import com.cheradip.ailanguagetutor.core.locale.AppLocaleManager
import com.cheradip.ailanguagetutor.core.ai.PlusTierAiModeSync
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivitySyncRepository
import com.cheradip.ailanguagetutor.core.device.GuestAiGateNotifier
import com.cheradip.ailanguagetutor.feature.onboarding.OnboardingPreferences
import com.cheradip.ailanguagetutor.ui.navigation.AppNavHost
import com.cheradip.ailanguagetutor.ui.theme.CheradipTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var billingRepository: BillingRepository
    @Inject lateinit var checkAppAccessUseCase: CheckAppAccessUseCase
    @Inject lateinit var promoRepository: PromoRepository
    @Inject lateinit var referralRepository: ReferralRepository
    @Inject lateinit var pronunciationEngine: PronunciationEngine
    @Inject lateinit var voicePreferenceRepository: VoicePreferenceRepository
    @Inject lateinit var onboardingPreferences: OnboardingPreferences
    @Inject lateinit var appLocaleManager: AppLocaleManager
    @Inject lateinit var plusTierAiModeSync: PlusTierAiModeSync
    @Inject lateinit var learningActivitySyncRepository: LearningActivitySyncRepository
    @Inject lateinit var guestAiGateNotifier: GuestAiGateNotifier
    @Inject lateinit var bundledPackSeeder: com.cheradip.ailanguagetutor.core.pack.BundledPackSeeder

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pronunciationEngine.init()
        lifecycleScope.launch {
            bundledPackSeeder.ensureBundledPacks()
        }
        lifecycleScope.launch {
            voicePreferenceRepository.gender.collect { pronunciationEngine.setGender(it) }
        }
        lifecycleScope.launch {
            authRepository.syncSessionFromStore()
        }
        lifecycleScope.launch {
            onboardingPreferences.isComplete()
        }
        setContent {
            CheradipTheme {
                val currentUser by authRepository.currentUser.collectAsState(initial = null)
                var showOnboarding by remember { mutableStateOf(true) }

                androidx.compose.runtime.LaunchedEffect(Unit) {
                    showOnboarding = !onboardingPreferences.isComplete()
                }

                AppNavHost(
                    showOnboarding = showOnboarding,
                    onOnboardingComplete = { showOnboarding = false },
                    authRepository = authRepository,
                    billingRepository = billingRepository,
                    checkAppAccessUseCase = checkAppAccessUseCase,
                    promoRepository = promoRepository,
                    referralRepository = referralRepository,
                    learningActivitySyncRepository = learningActivitySyncRepository,
                    guestAiGateNotifier = guestAiGateNotifier,
                    pronunciationEngine = pronunciationEngine,
                    appLocaleManager = appLocaleManager,
                    currentUser = currentUser,
                    modifier = Modifier,
                )
            }
        }
    }
}
