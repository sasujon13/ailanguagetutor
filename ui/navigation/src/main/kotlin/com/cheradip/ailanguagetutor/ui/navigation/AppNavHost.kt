package com.cheradip.ailanguagetutor.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cheradip.ailanguagetutor.core.locale.AppLocaleManager
import com.cheradip.ailanguagetutor.core.locale.AppStrings
import com.cheradip.ailanguagetutor.core.locale.LocalAppStrings
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
import com.cheradip.ailanguagetutor.feature.auth.SignUpScreen
import com.cheradip.ailanguagetutor.feature.billing.AdminConsoleScreen
import com.cheradip.ailanguagetutor.feature.billing.PaywallScreen
import com.cheradip.ailanguagetutor.feature.billing.ReferralScreen
import com.cheradip.ailanguagetutor.feature.grammar.GrammarBookScreen
import com.cheradip.ailanguagetutor.feature.home.HomeScreen
import com.cheradip.ailanguagetutor.feature.journal.LibraryScreen
import com.cheradip.ailanguagetutor.feature.languages.LanguagesScreen
import com.cheradip.ailanguagetutor.feature.onboarding.OnboardingScreen
import com.cheradip.ailanguagetutor.feature.practice.ModeSelectionScreen
import com.cheradip.ailanguagetutor.feature.practice.PracticeHubScreen
import com.cheradip.ailanguagetutor.feature.reader.OcrProcessingScreen
import com.cheradip.ailanguagetutor.feature.reader.ReaderScreen
import com.cheradip.ailanguagetutor.feature.scanner.ScannerLaunchMode
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
    appLocaleManager: AppLocaleManager,
    currentUser: AuthUser?,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val accessState by checkAppAccessUseCase.accessState.collectAsStateWithLifecycle()
    val localeUi by appLocaleManager.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(localeUi.snackbarMessage) {
        localeUi.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            appLocaleManager.clearSnackbar()
        }
    }

    val strings = localeUi.strings

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            referralRepository.refresh()
        }
    }

    val mainRoutes = setOf(
        Routes.HOME,
        "practice",
        Routes.LIBRARY,
        Routes.LANGUAGES,
        Routes.SETTINGS,
    )
    val routeBase = currentRoute?.substringBefore('?')
    val showBottomBar = routeBase in mainRoutes

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(accessState, currentRoute) {
        if (accessState == AccessState.TRIAL_EXPIRED &&
            routeBase !in setOf(Routes.PAYWALL, "login", "register", Routes.ONBOARDING)
        ) {
            navController.navigate(Routes.PAYWALL) { launchSingleTop = true }
        }
    }

    LaunchedEffect(currentRoute) {
        selectedTab = when (routeBase) {
            Routes.HOME -> 0
            "practice" -> 1
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    val tabs = listOf(
                        Triple(Routes.HOME, AppStrings.text("nav_home", strings), Icons.Default.Home),
                        Triple(Routes.PRACTICE_HUB, AppStrings.text("nav_practice", strings), Icons.Default.Mic),
                        Triple(Routes.LIBRARY, AppStrings.text("nav_learning", strings), Icons.Default.MenuBook),
                        Triple(Routes.LANGUAGES, AppStrings.text("nav_languages", strings), Icons.Default.Public),
                        Triple(Routes.SETTINGS, AppStrings.text("nav_settings", strings), Icons.Default.Settings),
                    )
                    tabs.forEachIndexed { index, (route, label, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = {
                                selectedTab = index
                                val destination = when (route) {
                                    Routes.HOME -> Routes.HOME
                                    Routes.PRACTICE_HUB -> Routes.practiceHub()
                                    else -> route
                                }
                                navController.navigate(destination) {
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
        CompositionLocalProvider(LocalAppStrings provides strings) {
            NavHost(
                navController = navController,
                startDestination = if (showOnboarding) Routes.ONBOARDING else Routes.HOME,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
            composable(Routes.ONBOARDING) {
                OnboardingScreen(onComplete = {
                    onOnboardingComplete()
                    navController.navigate(Routes.HOME) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
                })
            }
            composable(Routes.HOME) {
                HomeScreen(
                    onScanClick = { navController.navigate(Routes.scanner("camera")) },
                    onCameraClick = { navController.navigate(Routes.scanner("camera")) },
                    onImportClick = { navController.navigate(Routes.scanner("import")) },
                    onPracticeClick = {
                        selectedTab = 1
                        navController.navigate(Routes.practiceHub())
                    },
                    onTypeClick = {
                        selectedTab = 1
                        navController.navigate(Routes.practiceHub())
                    },
                    onVoiceClick = {
                        selectedTab = 1
                        navController.navigate(Routes.practiceHub(startVoice = true))
                    },
                    onListenClick = {
                        selectedTab = 1
                        navController.navigate(Routes.practiceHub())
                    },
                    onLearningClick = {
                        selectedTab = 2
                        navController.navigate(Routes.LIBRARY)
                    },
                    onGrammarClick = {
                        navController.navigate(Routes.GRAMMAR)
                    },
                )
            }
            composable(Routes.GRAMMAR) { GrammarBookScreen() }
            composable(
                route = Routes.PRACTICE_HUB,
                arguments = listOf(
                    navArgument("startVoice") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val startVoice = entry.arguments?.getBoolean("startVoice") ?: false
                PracticeHubScreen(
                    startVoiceInput = startVoice,
                    onOpenModeSelection = { navController.navigate(Routes.MODE_SELECTION) },
                    onScanClick = { navController.navigate(Routes.scanner("camera")) },
                    onCameraClick = { navController.navigate(Routes.scanner("camera")) },
                    onImportClick = { navController.navigate(Routes.scanner("import")) },
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
                    onNavigateLogin = { navController.navigate(Routes.login()) },
                    onNavigateModeSelection = { navController.navigate(Routes.MODE_SELECTION) },
                    isAdmin = currentUser?.role == "admin",
                    pronunciationEngine = pronunciationEngine,
                )
            }
            composable(
                route = Routes.SCANNER,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "camera"
                    },
                ),
            ) { entry ->
                val mode = entry.arguments?.getString("mode") ?: "camera"
                ScannerScreen(
                    documentId = null,
                    launchMode = if (mode == "import") ScannerLaunchMode.IMPORT else ScannerLaunchMode.CAMERA,
                    onBack = { navController.popBackStack() },
                    onDone = { docId ->
                        navController.navigate(Routes.ocrProcessing(docId)) {
                            popUpTo(Routes.scanner(mode)) { inclusive = true }
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
            composable(
                route = Routes.LOGIN,
                arguments = listOf(
                    navArgument("returnTo") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val returnTo = entry.arguments?.getString("returnTo").orEmpty()
                LoginScreen(
                    authRepository = authRepository,
                    onLoggedIn = {
                        if (returnTo == "paywall") {
                            navController.navigate(Routes.PAYWALL) {
                                popUpTo(Routes.LOGIN) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onBack = { navController.popBackStack() },
                    onNavigateSignUp = {
                        navController.navigate(Routes.register(returnTo)) {
                            popUpTo(Routes.login(returnTo))
                        }
                    },
                    subtitle = if (returnTo == "paywall") "Sign in to subscribe" else "Email or WhatsApp",
                )
            }
            composable(
                route = Routes.REGISTER,
                arguments = listOf(
                    navArgument("returnTo") {
                        type = NavType.StringType
                        defaultValue = ""
                    },
                ),
            ) { entry ->
                val returnTo = entry.arguments?.getString("returnTo").orEmpty()
                SignUpScreen(
                    authRepository = authRepository,
                    onSignedUp = {
                        if (returnTo == "paywall") {
                            navController.navigate(Routes.PAYWALL) {
                                popUpTo(Routes.REGISTER) { inclusive = true }
                            }
                        } else {
                            navController.popBackStack()
                        }
                    },
                    onNavigateLogin = {
                        navController.navigate(Routes.login(returnTo)) {
                            popUpTo(Routes.register(returnTo))
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.PAYWALL) {
                PaywallScreen(
                    referralRepository = referralRepository,
                    userEmail = currentUser?.email,
                    userWhatsapp = currentUser?.whatsapp,
                    isLoggedIn = currentUser != null,
                    onRequireLogin = {
                        navController.navigate(Routes.login("paywall"))
                    },
                    onSubscribed = {
                        if (accessState != AccessState.TRIAL_EXPIRED) {
                            navController.popBackStack()
                        }
                    },
                )
            }
            composable(Routes.REFERRAL) {
                ReferralScreen(
                    referralRepository = referralRepository,
                    currentUser = currentUser,
                    userEmail = currentUser?.email,
                    userWhatsapp = currentUser?.whatsapp,
                    onNavigateLogin = { navController.navigate(Routes.login()) },
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
}
