package com.cheradip.ailanguagetutor.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cheradip.ailanguagetutor.core.audio.PronunciationEngine
import com.cheradip.ailanguagetutor.core.auth.AuthRepository
import com.cheradip.ailanguagetutor.core.auth.AuthUser
import com.cheradip.ailanguagetutor.core.billing.AccessState
import com.cheradip.ailanguagetutor.core.billing.BillingRepository
import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.billing.PromoRepository
import com.cheradip.ailanguagetutor.core.billing.ReferralRepository
import com.cheradip.ailanguagetutor.feature.auth.LoginScreen
import com.cheradip.ailanguagetutor.feature.billing.AdminConsoleScreen
import com.cheradip.ailanguagetutor.feature.billing.PaywallScreen
import com.cheradip.ailanguagetutor.feature.billing.ReferralScreen
import com.cheradip.ailanguagetutor.feature.home.HomeScreen
import com.cheradip.ailanguagetutor.feature.journal.LibraryScreen
import com.cheradip.ailanguagetutor.feature.languages.LanguagesScreen
import com.cheradip.ailanguagetutor.feature.onboarding.OnboardingScreen
import com.cheradip.ailanguagetutor.feature.practice.ModeSelectionScreen
import com.cheradip.ailanguagetutor.feature.practice.PracticeHubScreen
import com.cheradip.ailanguagetutor.feature.reader.OcrProcessingScreen
import com.cheradip.ailanguagetutor.feature.reader.ReaderScreen
import com.cheradip.ailanguagetutor.feature.scanner.ScannerScreen
import com.cheradip.ailanguagetutor.feature.settings.SettingsScreen

@Composable
fun AppNavHost(
    showOnboarding: Boolean,
    onOnboardingComplete: () -> Unit,
    authRepository: AuthRepository,
    billingRepository: BillingRepository,
    checkAppAccessUseCase: CheckAppAccessUseCase,
    promoRepository: PromoRepository,
    referralRepository: ReferralRepository,
    pronunciationEngine: PronunciationEngine,
    currentUser: AuthUser?,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val accessState by checkAppAccessUseCase.accessState.collectAsStateWithLifecycle()

    val mainRoutes = setOf(Routes.HOME, Routes.PRACTICE_HUB, Routes.LIBRARY, Routes.LANGUAGES, Routes.SETTINGS)
    val showBottomBar = currentRoute in mainRoutes

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(accessState, currentRoute) {
        if (accessState == AccessState.TRIAL_EXPIRED &&
            currentRoute !in setOf(Routes.PAYWALL, Routes.LOGIN, Routes.ONBOARDING)
        ) {
            navController.navigate(Routes.PAYWALL) { launchSingleTop = true }
        }
    }

    LaunchedEffect(currentRoute) {
        selectedTab = when (currentRoute) {
            Routes.HOME -> 0
            Routes.PRACTICE_HUB -> 1
            Routes.LIBRARY -> 2
            Routes.LANGUAGES -> 3
            Routes.SETTINGS -> 4
            else -> selectedTab
        }
    }

    if (accessState == AccessState.TRIAL_EXPIRED && currentRoute == Routes.PAYWALL) {
        // Paywall is shown via navigation
    }

    Scaffold(
        modifier = modifier,
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val tabs = listOf(
                        Triple(Routes.HOME, "Home", Icons.Default.Home),
                        Triple(Routes.PRACTICE_HUB, "Practice", Icons.Default.Mic),
                        Triple(Routes.LIBRARY, "Learning", Icons.Default.MenuBook),
                        Triple(Routes.LANGUAGES, "Languages", Icons.Default.Public),
                        Triple(Routes.SETTINGS, "Settings", Icons.Default.Settings),
                    )
                    tabs.forEachIndexed { index, (route, label, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                navController.navigate(route) {
                                    popUpTo(Routes.HOME) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = if (showOnboarding) Routes.ONBOARDING else Routes.HOME,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onComplete = {
                    onOnboardingComplete()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onScanClick = { navController.navigate(Routes.SCANNER) },
                    onImportClick = { navController.navigate(Routes.SCANNER) },
                    onPracticeClick = {
                        selectedTab = 1
                        navController.navigate(Routes.PRACTICE_HUB)
                    },
                    onTypeClick = {
                        selectedTab = 1
                        navController.navigate(Routes.PRACTICE_HUB)
                    },
                    onVoiceClick = {
                        selectedTab = 1
                        navController.navigate(Routes.PRACTICE_HUB)
                    },
                    onListenClick = {
                        selectedTab = 1
                        navController.navigate(Routes.PRACTICE_HUB)
                    },
                    onLearningClick = {
                        selectedTab = 2
                        navController.navigate(Routes.LIBRARY)
                    },
                )
            }
            composable(Routes.PRACTICE_HUB) {
                PracticeHubScreen(
                    onOpenModeSelection = { navController.navigate(Routes.MODE_SELECTION) },
                    onScanClick = { navController.navigate(Routes.SCANNER) },
                )
            }
            composable(Routes.MODE_SELECTION) {
                ModeSelectionScreen(
                    onDone = { navController.popBackStack() },
                    onNavigatePaywall = { navController.navigate(Routes.PAYWALL) },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(onOpenDocument = { id ->
                    navController.navigate(Routes.reader(id))
                })
            }
            composable(Routes.LANGUAGES) { LanguagesScreen() }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateReferral = { navController.navigate(Routes.REFERRAL) },
                    onNavigatePaywall = { navController.navigate(Routes.PAYWALL) },
                    onNavigateAdmin = { navController.navigate(Routes.ADMIN) },
                    onNavigateAdminAi = { navController.navigate(Routes.ADMIN_AI) },
                    onNavigateLogin = { navController.navigate(Routes.LOGIN) },
                    onNavigateModeSelection = { navController.navigate(Routes.MODE_SELECTION) },
                    isAdmin = currentUser?.role == "admin",
                    pronunciationEngine = pronunciationEngine,
                )
            }
            composable(Routes.SCANNER) {
                ScannerScreen(
                    documentId = null,
                    onBack = { navController.popBackStack() },
                    onDone = { docId ->
                        navController.navigate(Routes.ocrProcessing(docId)) {
                            popUpTo(Routes.SCANNER) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.OCR_PROCESSING,
                arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
            ) { entry ->
                val docId = entry.arguments?.getLong("documentId") ?: return@composable
                OcrProcessingScreen(
                    documentId = docId,
                    onComplete = { id ->
                        navController.navigate(Routes.reader(id)) {
                            popUpTo(Routes.HOME)
                        }
                    },
                )
            }
            composable(
                route = Routes.READER,
                arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
            ) { entry ->
                val docId = entry.arguments?.getLong("documentId") ?: return@composable
                ReaderScreen(
                    documentId = docId,
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.LOGIN) {
                LoginScreen(
                    authRepository = authRepository,
                    onLoggedIn = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PAYWALL) {
                PaywallScreen(
                    referralRepository = referralRepository,
                    userEmail = currentUser?.email,
                    userWhatsapp = currentUser?.whatsapp,
                    onSubscribed = { navController.popBackStack() },
                )
            }
            composable(Routes.REFERRAL) {
                ReferralScreen(
                    referralRepository = referralRepository,
                    userEmail = currentUser?.email,
                    userWhatsapp = currentUser?.whatsapp,
                )
            }
            composable(Routes.ADMIN) {
                AdminConsoleScreen(initialTab = 0)
            }
            composable(Routes.ADMIN_AI) {
                AdminConsoleScreen(initialTab = 1)
            }
        }
    }
}
