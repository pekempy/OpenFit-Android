package com.openfit.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.openfit.mobile.data.healthconnect.HealthConnectManager
import com.openfit.mobile.data.settings.HealthDataSourceKind
import com.openfit.mobile.data.settings.UserHealthGoals
import com.openfit.mobile.ui.activity.ActivityScreen
import com.openfit.mobile.ui.calendar.CalendarScreen
import com.openfit.mobile.ui.body.BodyScreen
import com.openfit.mobile.ui.coach.CoachScreen
import com.openfit.mobile.ui.common.CustomInAppToast
import com.openfit.mobile.ui.common.LocalMetricInspector
import com.openfit.mobile.ui.common.MetricDetailSheet
import com.openfit.mobile.ui.common.MetricInspectorState
import com.openfit.mobile.ui.common.SimpleViewModelFactory
import com.openfit.mobile.ui.data.DataScreen
import com.openfit.mobile.ui.health.HealthScreen
import com.openfit.mobile.ui.metrics.MetricSubTab
import com.openfit.mobile.ui.metrics.MetricsScreen
import com.openfit.mobile.ui.navigation.Destination
import com.openfit.mobile.ui.navigation.ROUTE_ONBOARDING
import com.openfit.mobile.ui.onboarding.OnboardingScreen
import com.openfit.mobile.ui.settings.SettingsScreen
import kotlinx.coroutines.launch
import com.openfit.mobile.ui.sleep.SleepScreen
import com.openfit.mobile.ui.theme.OpenFitTheme
import com.openfit.mobile.ui.today.TodayScreen
import com.openfit.mobile.ui.today.TodayViewModel
import kotlinx.coroutines.flow.first

class MainActivity : ComponentActivity() {

    private val container: AppContainer by lazy { (application as OpenFitApplication).container }

    // Result of the browser round-trip for the Google OAuth consent screen;
    // handed off to GoogleAuthManager.handleAuthorizationResponse().
    private var onAuthResult: ((android.content.Intent?) -> Unit)? = null
    private val authLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        onAuthResult?.invoke(result.data)
    }

    // Result of the Health Connect system permission screen.
    private var onHealthConnectResult: ((Set<String>) -> Unit)? = null
    private val healthConnectLauncher = registerForActivityResult(HealthConnectManager.requestPermissionContract()) { granted ->
        onHealthConnectResult?.invoke(granted)
    }

    // Android 13+ requires an explicit runtime grant for POST_NOTIFICATIONS.
    // We request it once on first launch; declining is fine — summaries still
    // run and are saved in the Coach tab, just without a system notification.
    private val notificationPermLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op: best-effort */ }


    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val display by container.settingsRepository.settingsFlow.collectAsState(initial = null)
            val displaySettings = display?.display ?: com.openfit.mobile.data.settings.AppDisplaySettings()
            // Capture the intent at composition time so the deep-link only
            // fires once even if the Activity re-enters the composition.
            val navigateTo = remember { intent.getStringExtra(com.openfit.mobile.notifications.NotificationHelper.EXTRA_NAVIGATE_TO) }
            OpenFitTheme(
                darkTheme = when (displaySettings.themeMode) {
                    com.openfit.mobile.data.settings.AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
                    com.openfit.mobile.data.settings.AppThemeMode.LIGHT -> false
                    com.openfit.mobile.data.settings.AppThemeMode.DARK -> true
                },
                dynamicColor = displaySettings.useDynamicColor,
                accentColor = displaySettings.accentColor,
                customAccentColorHex = displaySettings.customAccentColorHex,
            ) {
                OpenFitApp(
                    container = container,
                    navigateTo = navigateTo,
                    launchAuthIntent = { intent, onResult ->
                        onAuthResult = onResult
                        authLauncher.launch(intent)
                    },
                    launchHealthConnectPermission = { onResult ->
                        onHealthConnectResult = onResult
                        healthConnectLauncher.launch(HealthConnectManager.ALL_PERMISSIONS)
                    },
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        container.authManager.dispose()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OpenFitApp(
    container: AppContainer,
    navigateTo: String? = null,
    launchAuthIntent: (android.content.Intent, (android.content.Intent?) -> Unit) -> Unit,
    launchHealthConnectPermission: ((Set<String>) -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    var isConnected by remember { mutableStateOf<Boolean?>(null) }
    val healthViewModel: TodayViewModel = viewModel(
        factory = SimpleViewModelFactory { TodayViewModel(container) },
    )
    val healthState by healthViewModel.uiState.collectAsState()
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = null)

    LaunchedEffect(Unit) {
        val currentSettings = container.settingsRepository.settingsFlow.first()
        isConnected = container.activeHealthDataSource(currentSettings.dataSourceKind).isConnected()
    }

    LaunchedEffect(Unit) { container.resumeDrive() }

    // Deep-link from a summary notification: navigate to the Coach tab once
    // the connection check completes and the nav graph is ready.
    LaunchedEffect(navigateTo, isConnected) {
        if (navigateTo == com.openfit.mobile.notifications.NotificationHelper.NAVIGATE_TO_COACH && isConnected == true) {
            navController.navigate(Destination.Coach.route) {
                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    val connected = isConnected
    if (connected == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    val startDestination = if (connected) Destination.Today.route else ROUTE_ONBOARDING
    val coachEnabled = settings?.isCoachConfigured == true

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val bottomBarItems = Destination.bottomBarItems.filter { it != Destination.Coach || coachEnabled }
    val isMetricsActive = currentRoute == Destination.Metrics.route || currentRoute in listOf("activity", "sleep", "health", "body")
    val isDevicesActive = currentRoute == Destination.Devices.route || currentRoute == "data"
    val showBottomBar = Destination.bottomBarItems.any { it.route == currentRoute } || isMetricsActive || isDevicesActive

    val inspectorState = remember { MetricInspectorState() }
    val scope = rememberCoroutineScope()
    val goals = settings?.goals ?: UserHealthGoals()
    val units = settings?.units ?: com.openfit.mobile.data.settings.AppUnitSettings()
    var quickLogCategory by remember { mutableStateOf<com.openfit.mobile.ui.common.QuickLogCategory?>(null) }

    // Auto-trigger the Health Connect permission dialog when background access is
    // missing and summaries are enabled. Fires every time the app opens (including
    // from the "grant it in the app" notification tap) until the user grants it.
    val summariesEnabled = settings?.morningSleepSummary?.enabled == true ||
        settings?.eveningActivitySummary?.enabled == true
    val needsBgPermission = connected &&
        settings?.dataSourceKind == com.openfit.mobile.data.settings.HealthDataSourceKind.HEALTH_CONNECT &&
        summariesEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(needsBgPermission) {
        if (!needsBgPermission) return@LaunchedEffect
        val hasBg = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            com.openfit.mobile.data.healthconnect.HealthConnectManager.BACKGROUND_READ_PERMISSION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasBg) launchHealthConnectPermission { /* dialog handled; next open will see it granted */ }
    }


    CompositionLocalProvider(LocalMetricInspector provides inspectorState) {
        Box(Modifier.fillMaxSize()) {
            Scaffold(
                contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (showBottomBar) {
                        NavigationBar {
                            bottomBarItems.forEach { destination ->
                                val isSelected = when (destination) {
                                    Destination.Metrics -> isMetricsActive
                                    Destination.Devices -> isDevicesActive
                                    else -> currentRoute == destination.route
                                }
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = { Icon(destination.icon, contentDescription = destination.label) },
                                    label = { Text(destination.label) },
                                )
                            }
                        }
                    }
                },
            ) { padding ->
                NavHost(
                    navController = navController,
                    startDestination = startDestination,
                    modifier = Modifier
                        .padding(bottom = padding.calculateBottomPadding())
                        .consumeWindowInsets(
                            PaddingValues(bottom = padding.calculateBottomPadding())
                        ),
                    enterTransition = {
                        fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.96f)
                    },
                    exitTransition = {
                        fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.96f)
                    },
                    popEnterTransition = {
                        fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.96f)
                    },
                    popExitTransition = {
                        fadeOut(tween(200)) + scaleOut(tween(200), targetScale = 0.96f)
                    },
                ) {
                    composable(ROUTE_ONBOARDING) {
                        OnboardingScreen(
                            container = container,
                            launchAuthIntent = launchAuthIntent,
                            launchHealthConnectPermission = launchHealthConnectPermission,
                            onConnected = {
                                isConnected = true
                                healthViewModel.refresh()
                                navController.navigate(Destination.Today.route) {
                                    popUpTo(ROUTE_ONBOARDING) { inclusive = true }
                                }
                            },
                        )
                    }
                    composable(Destination.Today.route) {
                        TodayScreen(
                            container = container,
                            state = healthState,
                            onRefresh = { healthViewModel.refresh() },
                            onConnectRequested = { navController.navigate(ROUTE_ONBOARDING) },
                        )
                    }
                    composable(Destination.Calendar.route) {
                        CalendarScreen(container = container)
                    }
                    composable(Destination.Metrics.route) {
                        MetricsScreen(container = container, state = healthState, onRefresh = { healthViewModel.refresh() })
                    }
                    composable(Destination.Devices.route) {
                        DataScreen(container = container, state = healthState, onRefresh = { healthViewModel.refresh() })
                    }
                    composable(Destination.Activity.route) {
                        MetricsScreen(container = container, state = healthState, onRefresh = { healthViewModel.refresh() }, initialTab = MetricSubTab.ACTIVITY)
                    }
                    composable(Destination.Sleep.route) {
                        MetricsScreen(container = container, state = healthState, onRefresh = { healthViewModel.refresh() }, initialTab = MetricSubTab.SLEEP)
                    }
                    composable(Destination.Health.route) {
                        MetricsScreen(container = container, state = healthState, onRefresh = { healthViewModel.refresh() }, initialTab = MetricSubTab.VITALS)
                    }
                    composable(Destination.Body.route) {
                        MetricsScreen(container = container, state = healthState, onRefresh = { healthViewModel.refresh() }, initialTab = MetricSubTab.BODY)
                    }
                    composable("data") {
                        DataScreen(container = container, state = healthState, onRefresh = { healthViewModel.refresh() })
                    }
                    composable(Destination.Coach.route) {
                        CoachScreen(container = container, healthState = healthState)
                    }
                    composable(Destination.Settings.route) {
                        SettingsScreen(
                            container = container,
                            launchAuthIntent = launchAuthIntent,
                            launchHealthConnectPermission = launchHealthConnectPermission,
                            onDataSourceChanged = { healthViewModel.refresh() },
                            onSignedOut = {
                                isConnected = false
                                navController.navigate(ROUTE_ONBOARDING) {
                                    popUpTo(0)
                                }
                            },
                        )
                    }
                }
            }

            // Quick Log SpeedDial FAB (shown on health data screens; hidden on
            // Coach, whose own message input occupies the same bottom-end corner)
            if (showBottomBar && currentRoute != Destination.Coach.route && currentRoute != Destination.Settings.route) {
                com.openfit.mobile.ui.common.QuickLogSpeedDial(
                    onSelectCategory = { cat -> quickLogCategory = cat },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 16.dp, bottom = 96.dp),
                )
            }

            // Quick Log Modal Bottom Sheet
            quickLogCategory?.let { cat ->
                com.openfit.mobile.ui.common.QuickLogModalSheet(
                    container = container,
                    initialCategory = cat,
                    goals = goals,
                    units = units,
                    onDismiss = { quickLogCategory = null },
                    onLogged = { msg ->
                        healthViewModel.refresh()
                        inspectorState.showToast(
                            com.openfit.mobile.ui.common.MetricDetail(
                                title = "Logged",
                                currentValue = null,
                                icon = cat.icon,
                                accentColor = cat.color,
                                summary = msg,
                            )
                        )
                    },
                )
            }

            // Custom floating in-app toast overlay
            CustomInAppToast(
                detail = inspectorState.activeToast,
                onDismiss = { inspectorState.dismissToast() },
                onOpenSheet = { inspectorState.showSheet(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = if (showBottomBar) 90.dp else 16.dp),
            )

            // Full interactive metric detail & goal configuration sheet
            MetricDetailSheet(
                detail = inspectorState.activeSheet,
                goals = goals,
                onUpdateGoals = { updated -> scope.launch { container.settingsRepository.updateGoals(updated) } },
                onDismiss = { inspectorState.dismissSheet() },
            )
        }
    }
}
