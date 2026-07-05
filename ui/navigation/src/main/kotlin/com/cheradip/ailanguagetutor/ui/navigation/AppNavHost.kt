package com.cheradip.ailanguagetutor.ui.navigation

import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.cheradip.ailanguagetutor.core.billing.grantsLearningAccess
import com.cheradip.ailanguagetutor.core.billing.CheckAppAccessUseCase
import com.cheradip.ailanguagetutor.core.billing.ReferralRepository
import com.cheradip.ailanguagetutor.core.device.GuestAiGateNotifier
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowRepository
import com.cheradip.ailanguagetutor.core.device.ScanWorkflowStage
import com.cheradip.ailanguagetutor.core.database.repository.DocumentRepository
import com.cheradip.ailanguagetutor.core.database.repository.LearningActivitySyncRepository
import com.cheradip.ailanguagetutor.feature.auth.ChangeEmailScreen
import com.cheradip.ailanguagetutor.feature.auth.ForgotPasswordScreen
import com.cheradip.ailanguagetutor.feature.auth.LoginScreen
import com.cheradip.ailanguagetutor.feature.auth.ProfileScreen
import com.cheradip.ailanguagetutor.feature.auth.SignUpScreen
import com.cheradip.ailanguagetutor.feature.auth.UpdatePasswordScreen
import com.cheradip.ailanguagetutor.feature.billing.AdminConsoleScreen
import com.cheradip.ailanguagetutor.feature.billing.AdminReportsScreen
import com.cheradip.ailanguagetutor.feature.billing.PaywallScreen
import com.cheradip.ailanguagetutor.feature.billing.ReferralScreen
import com.cheradip.ailanguagetutor.feature.grammar.GrammarBookScreen
import com.cheradip.ailanguagetutor.feature.help.ManualType
import com.cheradip.ailanguagetutor.feature.help.UserManualDetailScreen
import com.cheradip.ailanguagetutor.feature.help.UserManualHubScreen
import com.cheradip.ailanguagetutor.ui.components.SupportActions
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
import com.cheradip.ailanguagetutor.ui.components.AppMenuDestination
import com.cheradip.ailanguagetutor.ui.components.AppMenuNavigation
import com.cheradip.ailanguagetutor.ui.components.LocalAppMenuNavigation
import com.cheradip.ailanguagetutor.ui.components.LocalNavBack

@Composable
fun AppNavHost(
    showOnboarding: Boolean,
    onOnboardingComplete: () -> Unit,
    authRepository: AuthRepository,
    checkAppAccessUseCase: CheckAppAccessUseCase,
    referralRepository: ReferralRepository,
    learningActivitySyncRepository: LearningActivitySyncRepository,
    guestAiGateNotifier: GuestAiGateNotifier,
    scanWorkflowRepository: ScanWorkflowRepository,
    documentRepository: DocumentRepository,
    pronunciationEngine: PronunciationEngine,
    appLocaleManager: AppLocaleManager,
    currentUser: AuthUser?,
    modifier: Modifier = Modifier,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val openSupport = { SupportActions.openWhatsAppSupport(context) }
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val accessState by checkAppAccessUseCase.accessState.collectAsStateWithLifecycle()
    val hasLearningAccess = accessState.grantsLearningAccess()
    val localeUi by appLocaleManager.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(localeUi.snackbarMessage) {
        localeUi.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            appLocaleManager.clearSnackbar()
        }
    }

    LaunchedEffect(guestAiGateNotifier) {
        guestAiGateNotifier.loginRequired.collect {
            navController.navigate(Routes.login("guest_ai")) {
                launchSingleTop = true
            }
        }
    }

    val strings = localeUi.strings

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            referralRepository.refresh()
            learningActivitySyncRepository.syncIfLoggedIn()
        }
    }

    var scanResumeHandled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(showOnboarding, scanResumeHandled, hasLearningAccess) {
        if (showOnboarding || scanResumeHandled) return@LaunchedEffect
        scanResumeHandled = true
        val session = scanWorkflowRepository.currentSession() ?: return@LaunchedEffect
        if (session.scanOnly) {
            if (documentRepository.getDocument(session.documentId) == null) {
                scanWorkflowRepository.clear()
            } else if (session.stage == ScanWorkflowStage.SCANNER) {
                navController.navigate(Routes.scanner(session.mode, scanOnly = true, launchCapture = false)) {
                    launchSingleTop = true
                }
            }
            return@LaunchedEffect
        }
        if (!hasLearningAccess) {
            scanWorkflowRepository.clear()
            return@LaunchedEffect
        }
        if (documentRepository.getDocument(session.documentId) == null) {
            scanWorkflowRepository.clear()
            return@LaunchedEffect
        }
        when (session.stage) {
            ScanWorkflowStage.SCANNER -> {
                if (!documentRepository.isOcrComplete(session.documentId)) {
                    navController.navigate(Routes.scannerDoc(session.documentId)) {
                        launchSingleTop = true
                    }
                } else {
                    scanWorkflowRepository.clear()
                }
            }
            ScanWorkflowStage.OCR -> {
                if (!documentRepository.isOcrComplete(session.documentId)) {
                    navController.navigate(Routes.ocrProcessing(session.documentId)) {
                        launchSingleTop = true
                    }
                } else {
                    scanWorkflowRepository.clear()
                }
            }
        }
    }

    LaunchedEffect(currentRoute) {
        pronunciationEngine.stop()
    }

    val routeBase = currentRoute?.substringBefore('?')
    val showBottomBar = Routes.showsBottomNavigation(routeBase)
    val nestedNavBack: (() -> Unit)? = remember(routeBase, navController) {
        if (
            routeBase != null &&
            routeBase != Routes.ONBOARDING &&
            !Routes.isMainTabRoute(routeBase) &&
            navController.previousBackStackEntry != null
        ) {
            { navController.popBackStack() }
        } else {
            null
        }
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    fun navigateToPaywallForLearning() {
        navController.navigate(Routes.PAYWALL) { launchSingleTop = true }
    }

    fun requireLearningAccess(onAllowed: () -> Unit) {
        if (hasLearningAccess) onAllowed() else navigateToPaywallForLearning()
    }

    LaunchedEffect(currentRoute, hasLearningAccess) {
        if (hasLearningAccess) return@LaunchedEffect
        val route = currentRoute ?: return@LaunchedEffect
        if (route == Routes.PAYWALL) return@LaunchedEffect
        // currentRoute is the route *template* (scanner?mode={mode}&scanOnly={scanOnly}),
        // so read the actual arg — scan-only scanning/editing/saving stays free forever.
        if (backStack?.arguments?.getBoolean("scanOnly") == true) return@LaunchedEffect
        if (!Routes.requiresLearningSubscription(route)) return@LaunchedEffect
        navController.navigate(Routes.PAYWALL) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(currentRoute) {
        selectedTab = when {
            routeBase == Routes.HOME -> 0
            routeBase?.startsWith("practice") == true -> 1
            routeBase == Routes.LIBRARY -> 2
            routeBase == Routes.PROFILE -> 3
            routeBase == Routes.SETTINGS -> 4
            else -> selectedTab
        }
    }

    fun navigateMainTab(index: Int, route: String) {
        val isLearningTab = route.startsWith("practice") || route == Routes.LIBRARY
        if (isLearningTab && !hasLearningAccess) {
            navigateToPaywallForLearning()
            return
        }
        selectedTab = index
        val destination = when (route) {
            Routes.HOME -> Routes.HOME
            Routes.PRACTICE_HUB -> Routes.practiceHub()
            else -> route
        }
        navController.navigate(destination) {
            if (routeBase == Routes.ONBOARDING) {
                popUpTo(Routes.ONBOARDING) { inclusive = true }
            } else {
                popUpTo(Routes.HOME) { saveState = true }
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun openPracticeActivity(activityId: Long) {
        requireLearningAccess {
            selectedTab = 1
            navController.navigate(Routes.practiceHub(activityId = activityId)) {
                popUpTo(Routes.HOME) { saveState = true }
                launchSingleTop = true
            }
        }
    }

    val appMenuNavigation = AppMenuNavigation(
        isAdmin = currentUser?.role == "admin",
        onNavigate = { dest ->
            when (dest) {
                AppMenuDestination.HOME -> navigateMainTab(0, Routes.HOME)
                AppMenuDestination.PRACTICE -> navigateMainTab(1, Routes.PRACTICE_HUB)
                AppMenuDestination.LEARNING -> navigateMainTab(2, Routes.LIBRARY)
                AppMenuDestination.LANGUAGES -> requireLearningAccess {
                    navController.navigate(Routes.LANGUAGES) { launchSingleTop = true }
                }
                AppMenuDestination.GRAMMAR -> requireLearningAccess {
                    navController.navigate(Routes.GRAMMAR) { launchSingleTop = true }
                }
                AppMenuDestination.REFERRAL -> navController.navigate(Routes.REFERRAL) { launchSingleTop = true }
                AppMenuDestination.PAYWALL -> navController.navigate(Routes.PAYWALL) { launchSingleTop = true }
                AppMenuDestination.MODE_SELECTION -> requireLearningAccess {
                    navController.navigate(Routes.MODE_SELECTION) { launchSingleTop = true }
                }
                AppMenuDestination.PROFILE -> navigateMainTab(3, Routes.PROFILE)
                AppMenuDestination.SETTINGS -> navigateMainTab(4, Routes.SETTINGS)
                AppMenuDestination.ADMIN -> navController.navigate(Routes.ADMIN) { launchSingleTop = true }
                AppMenuDestination.ADMIN_AI -> navController.navigate(Routes.ADMIN_AI) { launchSingleTop = true }
            }
        },
    )

    Scaffold(
        modifier = modifier,
        // Inner screens (CheradipScrollScreen) own the status-bar inset on their top bar.
        // Applying top inset here too doubled the gap above "Learning", "Languages", etc.
        contentWindowInsets = ScaffoldDefaults.contentWindowInsets.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showBottomBar) {
                val menuTeal = MaterialTheme.colorScheme.primary
                NavigationBar {
                    val tabs = listOf(
                        Triple(Routes.HOME, AppStrings.text("nav_home", strings), Icons.Default.Home),
                        Triple(Routes.PRACTICE_HUB, AppStrings.text("nav_practice", strings), Icons.AutoMirrored.Filled.MenuBook),
                        Triple(Routes.LIBRARY, AppStrings.text("nav_learning", strings), Icons.Default.Refresh),
                        Triple(Routes.PROFILE, AppStrings.text("nav_profile", strings), Icons.Default.Person),
                        Triple(Routes.SETTINGS, AppStrings.text("nav_settings", strings), Icons.Default.Settings),
                    )
                    val navItemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = menuTeal,
                        selectedTextColor = menuTeal,
                        unselectedIconColor = menuTeal,
                        unselectedTextColor = menuTeal,
                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    )
                    tabs.forEachIndexed { index, (route, label, icon) ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { navigateMainTab(index, route) },
                            icon = { Icon(icon, contentDescription = label, tint = menuTeal) },
                            label = { Text(label, color = menuTeal) },
                            colors = navItemColors,
                        )
                    }
                }
            }
        },
    ) { padding ->
        CompositionLocalProvider(
            LocalAppStrings provides strings,
            LocalAppMenuNavigation provides appMenuNavigation,
            LocalNavBack provides nestedNavBack,
        ) {
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
                    hasLearningAccess = hasLearningAccess,
                    onRequireLearningSubscription = ::navigateToPaywallForLearning,
                    onScanClick = { scanOnly ->
                        if (scanOnly) {
                            navController.navigate(Routes.scanner("camera", true))
                        } else {
                            requireLearningAccess {
                                navController.navigate(Routes.scanner("camera", false))
                            }
                        }
                    },
                    onCameraClick = {
                        requireLearningAccess { navController.navigate(Routes.scanner("camera", false)) }
                    },
                    onImportClick = {
                        requireLearningAccess { navController.navigate(Routes.scanner("import", false)) }
                    },
                    onPracticeClick = {
                        requireLearningAccess {
                            selectedTab = 1
                            navController.navigate(Routes.practiceHub())
                        }
                    },
                    onTypeClick = {
                        requireLearningAccess {
                            selectedTab = 1
                            navController.navigate(Routes.practiceHub())
                        }
                    },
                    onVoiceClick = {
                        requireLearningAccess {
                            selectedTab = 1
                            navController.navigate(Routes.practiceHub(startVoice = true))
                        }
                    },
                    onListenClick = {
                        requireLearningAccess {
                            selectedTab = 1
                            navController.navigate(Routes.practiceHub())
                        }
                    },
                    onLearningClick = {
                        requireLearningAccess {
                            selectedTab = 2
                            navController.navigate(Routes.LIBRARY)
                        }
                    },
                    onGrammarClick = {
                        requireLearningAccess { navController.navigate(Routes.GRAMMAR) }
                    },
                )
            }
            composable(Routes.GRAMMAR) { GrammarBookScreen() }
            composable(
                route = Routes.PRACTICE_HUB,
                arguments = listOf(
                    navArgument("activityId") {
                        type = NavType.LongType
                        defaultValue = -1L
                    },
                    navArgument("startVoice") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val startVoice = entry.arguments?.getBoolean("startVoice") ?: false
                val activityId = entry.arguments?.getLong("activityId") ?: -1L
                PracticeHubScreen(
                    startVoiceInput = startVoice,
                    restoreActivityId = activityId.takeIf { it > 0L },
                    onOpenModeSelection = { navController.navigate(Routes.MODE_SELECTION) },
                    onScanClick = { navController.navigate(Routes.scanner("camera", false)) },
                    onCameraClick = { navController.navigate(Routes.scanner("camera", false)) },
                    onImportClick = { navController.navigate(Routes.scanner("import", false)) },
                )
            }
            composable(Routes.MODE_SELECTION) {
                ModeSelectionScreen(
                    onDone = { navController.popBackStack() },
                    onNavigatePaywall = { navController.navigate(Routes.PAYWALL) },
                    onOpenLanguages = {
                        navController.navigate(Routes.LANGUAGES) { launchSingleTop = true }
                    },
                )
            }
            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenDocument = { id ->
                        navController.navigate(Routes.reader(id))
                    },
                    onOpenPracticeActivity = { activityId ->
                        openPracticeActivity(activityId)
                    },
                )
            }
            composable(Routes.LANGUAGES) { LanguagesScreen() }
            composable(Routes.PROFILE) {
                ProfileScreen(
                    currentUser = currentUser,
                    authRepository = authRepository,
                    onNavigateLogin = { navController.navigate(Routes.login()) },
                    onNavigateSignUp = { navController.navigate(Routes.register()) },
                    onNavigateUpdatePassword = { navController.navigate(Routes.UPDATE_PASSWORD) },
                    onNavigateChangeEmail = { navController.navigate(Routes.CHANGE_EMAIL) },
                    onOpenSupport = openSupport,
                    onNavigateReferral = { navController.navigate(Routes.REFERRAL) },
                    onNavigatePaywall = { navController.navigate(Routes.PAYWALL) },
                    onNavigateUserManual = {
                        navController.navigate(Routes.USER_MANUAL) { launchSingleTop = true }
                    },
                    onNavigateAdminReports = {
                        navController.navigate(Routes.ADMIN_REPORTS) { launchSingleTop = true }
                    },
                    isAdmin = currentUser?.role == "admin",
                )
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    onNavigateReferral = { navController.navigate(Routes.REFERRAL) },
                    onNavigatePaywall = { navController.navigate(Routes.PAYWALL) },
                    onNavigateAdmin = { navController.navigate(Routes.ADMIN) },
                    onNavigateAdminAi = { navController.navigate(Routes.ADMIN_AI) },
                    onNavigateAdminReports = {
                        navController.navigate(Routes.ADMIN_REPORTS) { launchSingleTop = true }
                    },
                    onNavigateModeSelection = {
                        requireLearningAccess { navController.navigate(Routes.MODE_SELECTION) { launchSingleTop = true } }
                    },
                    onOpenSupport = openSupport,
                    isAdmin = currentUser?.role == "admin",
                    pronunciationEngine = pronunciationEngine,
                )
            }
            composable(
                route = Routes.SCANNER_DOC,
                arguments = listOf(navArgument("documentId") { type = NavType.LongType }),
            ) { entry ->
                val docId = entry.arguments?.getLong("documentId") ?: return@composable
                ScannerScreen(
                    documentId = docId,
                    launchMode = ScannerLaunchMode.CAMERA,
                    scanOnly = false,
                    onBack = { navController.popBackStack() },
                    onDone = { id ->
                        navController.navigate(Routes.ocrProcessing(id)) {
                            popUpTo(Routes.scannerDoc(docId)) { inclusive = true }
                        }
                    },
                )
            }
            composable(
                route = Routes.SCANNER,
                arguments = listOf(
                    navArgument("mode") {
                        type = NavType.StringType
                        defaultValue = "camera"
                    },
                    navArgument("scanOnly") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                    navArgument("launchCapture") {
                        type = NavType.BoolType
                        defaultValue = true
                    },
                ),
            ) { entry ->
                val mode = entry.arguments?.getString("mode") ?: "camera"
                val scanOnly = entry.arguments?.getBoolean("scanOnly") ?: false
                val launchCapture = entry.arguments?.getBoolean("launchCapture") ?: true
                ScannerScreen(
                    documentId = null,
                    launchMode = if (mode == "import") ScannerLaunchMode.IMPORT else ScannerLaunchMode.CAMERA,
                    scanOnly = scanOnly,
                    launchCapture = launchCapture,
                    onBack = { navController.popBackStack() },
                    onDone = { docId ->
                        navController.navigate(Routes.ocrProcessing(docId)) {
                            popUpTo(Routes.scanner(mode, scanOnly)) { inclusive = true }
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
                    onNavigateForgotPassword = {
                        navController.navigate(Routes.FORGOT_PASSWORD)
                    },
                    subtitle = when (returnTo) {
                        "paywall" -> "Sign in to subscribe"
                        "guest_ai" -> AppStrings.text("login_subtitle_guest_ai", strings)
                        else -> "Email address"
                    },
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
            composable(Routes.FORGOT_PASSWORD) {
                ForgotPasswordScreen(
                    authRepository = authRepository,
                    onResetComplete = {
                        navController.navigate(Routes.login()) {
                            popUpTo(Routes.FORGOT_PASSWORD) { inclusive = true }
                        }
                    },
                    onNavigateLogin = {
                        navController.navigate(Routes.login()) {
                            popUpTo(Routes.FORGOT_PASSWORD) { inclusive = true }
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.UPDATE_PASSWORD) {
                UpdatePasswordScreen(
                    authRepository = authRepository,
                    onUpdated = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.CHANGE_EMAIL) {
                val email = currentUser?.email.orEmpty()
                if (email.isBlank()) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    ChangeEmailScreen(
                        authRepository = authRepository,
                        currentEmail = email,
                        onEmailChanged = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }
            }
            composable(Routes.PAYWALL) {
                PaywallScreen(
                    referralRepository = referralRepository,
                    isLoggedIn = currentUser != null,
                    onRequireLogin = {
                        navController.navigate(Routes.login("paywall"))
                    },
                    onSubscribed = {
                        navController.popBackStack()
                    },
                )
            }
            composable(Routes.REFERRAL) {
                ReferralScreen(
                    referralRepository = referralRepository,
                    currentUser = currentUser,
                    userEmail = currentUser?.email,
                    onNavigateLogin = { navController.navigate(Routes.login()) },
                )
            }
            composable(Routes.ADMIN) {
                AdminConsoleScreen(initialTab = 0)
            }
            composable(Routes.ADMIN_AI) {
                AdminConsoleScreen(initialTab = 1)
            }
            composable(Routes.ADMIN_REPORTS) {
                val isAdmin = currentUser?.role == "admin"
                if (!isAdmin) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
                AdminReportsScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.USER_MANUAL) {
                val isAdmin = currentUser?.role == "admin"
                UserManualHubScreen(
                    isAdmin = isAdmin,
                    onOpenManual = { manual ->
                        navController.navigate(Routes.userManualRead(manual.id))
                    },
                )
            }
            composable(
                route = Routes.USER_MANUAL_READ,
                arguments = listOf(navArgument("manualId") { type = NavType.StringType }),
            ) { entry ->
                val manualId = entry.arguments?.getString("manualId")
                val manualType = ManualType.fromId(manualId)
                if (manualType == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
                val isAdmin = currentUser?.role == "admin"
                if (manualType != ManualType.USER && !isAdmin) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                    return@composable
                }
                UserManualDetailScreen(manualType = manualType)
            }
            }
        }
    }
}
