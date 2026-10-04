package com.openfit.mobile.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.healthconnect.HealthConnectManager
import com.openfit.mobile.data.oauth.OAuthConfig
import com.openfit.mobile.data.settings.*
import com.openfit.mobile.model.AiProviderConfig
import com.openfit.mobile.model.AiProviderKind
import com.openfit.mobile.work.WorkScheduler
import androidx.compose.runtime.Composable
import androidx.activity.result.IntentSenderRequest
import com.openfit.mobile.data.backup.DriveAuth
import kotlinx.coroutines.launch

enum class SettingsCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    APPEARANCE(
        "Appearance",
        "Theme mode, high contrast, compact layout",
        Icons.Filled.Palette,
    ),
    UNITS(
        "Units & Measurements",
        "Metric, Imperial, Stone & lbs, Height, Energy",
        Icons.Filled.Straighten,
    ),
    CONNECTIONS(
        "Connections & Sources",
        "Health Connect, Google Health OAuth, AI Coach",
        Icons.Filled.Link,
    ),
    GOALS(
        "Health Goals",
        "Daily targets for steps, active time, sleep, water",
        Icons.Filled.TrackChanges,
    ),
    REMINDERS(
        "Reminders & Briefings",
        "Morning sleep analysis, evening activity summary",
        Icons.Filled.Notifications,
    ),
    ABOUT(
        "About OpenFit",
        "Version 1.0.0, architecture, privacy & licenses",
        Icons.Filled.Info,
    ),
}

/** Theme-adaptive icon color for each settings category. Uses M3 colour-scheme
  * roles so it responds to Material You, dark/light mode, and accent presets. */
val SettingsCategory.themeColor: Color
    @Composable get() = when (this) {
        SettingsCategory.APPEARANCE  -> MaterialTheme.colorScheme.primary
        SettingsCategory.UNITS       -> MaterialTheme.colorScheme.secondary
        SettingsCategory.CONNECTIONS -> MaterialTheme.colorScheme.tertiary
        SettingsCategory.GOALS       -> MaterialTheme.colorScheme.primary
        SettingsCategory.REMINDERS   -> MaterialTheme.colorScheme.secondary
        SettingsCategory.ABOUT       -> MaterialTheme.colorScheme.tertiary
    }


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    launchAuthIntent: (android.content.Intent, (android.content.Intent?) -> Unit) -> Unit,
    launchHealthConnectPermission: ((Set<String>) -> Unit) -> Unit,
    onDataSourceChanged: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = null)
    var selectedCategory by remember { mutableStateOf<SettingsCategory?>(null) }

    val s = settings
    if (s == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    if (selectedCategory != null) {
        BackHandler { selectedCategory = null }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(selectedCategory!!.title, fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = { selectedCategory = null }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                when (selectedCategory!!) {
                    SettingsCategory.APPEARANCE -> AppearanceSettingsSection(
                        display = s.display,
                        onChange = { updated -> scope.launch { container.settingsRepository.updateDisplaySettings(updated) } },
                    )
                    SettingsCategory.UNITS -> UnitsSettingsSection(
                        units = s.units,
                        onChange = { updated -> scope.launch { container.settingsRepository.updateUnits(updated) } },
                    )
                    SettingsCategory.CONNECTIONS -> ConnectionsSettingsSection(
                        container = container,
                        settings = s,
                        launchAuthIntent = launchAuthIntent,
                        launchHealthConnectPermission = launchHealthConnectPermission,
                        onDataSourceChanged = onDataSourceChanged,
                        onSignedOut = onSignedOut,
                    )
                    SettingsCategory.REMINDERS -> RemindersSettingsSection(
                        container = container,
                        reminders = s.reminders,
                        onChange = { updated ->
                            scope.launch {
                                container.settingsRepository.updateReminders(updated)
                                WorkScheduler.scheduleMorningSummary(container, updated.morningSleepSummary)
                                WorkScheduler.scheduleEveningSummary(container, updated.eveningActivitySummary)
                                WorkScheduler.scheduleHydrationReminder(
                                    context       = container.appContext,
                                    enabled       = updated.hydrationReminder,
                                    morningHour   = updated.morningSleepSummary.hour,
                                    morningMinute = updated.morningSleepSummary.minute,
                                )
                                WorkScheduler.scheduleMoveReminder(
                                    context       = container.appContext,
                                    enabled       = updated.moveReminder,
                                    morningHour   = updated.morningSleepSummary.hour,
                                    morningMinute = updated.morningSleepSummary.minute,
                                )
                            }
                        },
                    )
                    SettingsCategory.GOALS -> HealthGoalsSection(
                        goals = s.goals,
                        onUpdateGoals = { updated -> scope.launch { container.settingsRepository.updateGoals(updated) } },
                    )
                    SettingsCategory.ABOUT -> AboutSettingsSection()
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.Bold) },
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Header Banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            modifier = Modifier.size(48.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(26.dp),
                                )
                            }
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Preferences & Integrations",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                "Configure your data sources, wearables, AI coach personality, and display units.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))

                // Category List Items
                SettingsCategory.entries.forEach { category ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedCategory = category },
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    category.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            },
                            supportingContent = {
                                Text(
                                    category.subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingContent = {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = category.themeColor.copy(alpha = 0.12f),
                                    modifier = Modifier.size(44.dp),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            category.icon,
                                            contentDescription = null,
                                            tint = category.themeColor,
                                            modifier = Modifier.size(24.dp),
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Filled.ChevronRight,
                                    contentDescription = "Open ${category.title}",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

// ============================================================================
// 1. APPEARANCE SECTION
// ============================================================================
@Composable
private fun AppearanceSettingsSection(
    display: AppDisplaySettings,
    onChange: (AppDisplaySettings) -> Unit,
) {
    SectionHeader("Theme Mode", Icons.Filled.Brightness6)
    Text(
        "Choose how OpenFit looks on your device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            AppThemeMode.entries.forEach { mode ->
                val label = when (mode) {
                    AppThemeMode.SYSTEM -> "System default"
                    AppThemeMode.LIGHT -> "Light mode"
                    AppThemeMode.DARK -> "Dark mode"
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChange(display.copy(themeMode = mode)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(selected = display.themeMode == mode, onClick = { onChange(display.copy(themeMode = mode)) })
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    HorizontalDivider()

    SectionHeader("Colour Theming", Icons.Filled.Palette)
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Material You Dynamic Colour", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Samples dominant accent colours from your wallpaper (Android 12+).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = display.useDynamicColor, onCheckedChange = { onChange(display.copy(useDynamicColor = it)) })
            }

            if (!display.useDynamicColor) {
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("Fixed Accent Palette", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AccentColorTheme.entries.forEach { accent ->
                        val isSelected = display.accentColor == accent
                        val label = when (accent) {
                            AccentColorTheme.TEAL -> "Teal"
                            AccentColorTheme.PIXEL_BLUE -> "Pixel Blue"
                            AccentColorTheme.CORAL -> "Coral"
                            AccentColorTheme.EMERALD -> "Emerald"
                            AccentColorTheme.VIOLET -> "Violet"
                            AccentColorTheme.CUSTOM -> "Custom"
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = { onChange(display.copy(accentColor = accent)) },
                            label = { Text(label) },
                        )
                    }
                }

                if (display.accentColor == AccentColorTheme.CUSTOM) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    var hexText by remember(display.customAccentColorHex) {
                        mutableStateOf(display.customAccentColorHex ?: "#00BFA5")
                    }
                    val parsed = remember(hexText) { com.openfit.mobile.ui.theme.parseHexColorOrNull(hexText) }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(parsed ?: MaterialTheme.colorScheme.surfaceVariant),
                        )
                        OutlinedTextField(
                            value = hexText,
                            onValueChange = { input ->
                                hexText = input
                                com.openfit.mobile.ui.theme.parseHexColorOrNull(input)?.let {
                                    onChange(display.copy(customAccentColorHex = input.trim()))
                                }
                            },
                            label = { Text("Hex colour, e.g. #6750A4") },
                            isError = hexText.isNotBlank() && parsed == null,
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (hexText.isNotBlank() && parsed == null) {
                        Text(
                            "Enter a valid 6-digit hex colour (e.g. #FF7043).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider()

    SectionHeader("Data Visibility", Icons.Filled.VisibilityOff)
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("Reproductive health", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Menstruation, cervical mucus, ovulation, and related tracking on the Body tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = display.showReproductiveHealth,
                onCheckedChange = { onChange(display.copy(showReproductiveHealth = it)) },
            )
        }
    }
}

// ============================================================================
// 2. UNITS SECTION
// ============================================================================
@Composable
private fun UnitsSettingsSection(
    units: AppUnitSettings,
    onChange: (AppUnitSettings) -> Unit,
) {
    SectionHeader("Unit System", Icons.Filled.Straighten)
    Text(
        "Choose between Metric and Imperial standards. You can also customise individual measurements below.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(
            onClick = {
                onChange(
                    units.copy(
                        system = UnitSystem.METRIC,
                        weightUnit = WeightUnit.KG,
                        distanceUnit = DistanceUnit.KILOMETERS,
                        waterUnit = WaterUnit.LITERS,
                        temperatureUnit = TemperatureUnit.CELSIUS,
                    )
                )
            },
            colors = if (units.system == UnitSystem.METRIC) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
            modifier = Modifier.weight(1f),
        ) {
            Text("Metric (kg, km, L, °C)")
        }

        OutlinedButton(
            onClick = {
                onChange(
                    units.copy(
                        system = UnitSystem.IMPERIAL,
                        weightUnit = WeightUnit.LBS,
                        distanceUnit = DistanceUnit.MILES,
                        waterUnit = WaterUnit.FLUID_OUNCES,
                        temperatureUnit = TemperatureUnit.FAHRENHEIT,
                    )
                )
            },
            colors = if (units.system == UnitSystem.IMPERIAL) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
            modifier = Modifier.weight(1f),
        ) {
            Text("Imperial (lbs, mi, oz, °F)")
        }
    }

    HorizontalDivider()

    SectionHeader("Body Weight", Icons.Filled.MonitorWeight)
    Text(
        "Select your preferred weight unit. Both standard and UK stone formats are fully supported.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            listOf(
                WeightUnit.KG to "Kilograms (kg)",
                WeightUnit.STONE to "Stone & Pounds (st / lbs)",
                WeightUnit.LBS to "Pounds (lbs)",
            ).forEach { (unit, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChange(units.copy(weightUnit = unit)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(selected = units.weightUnit == unit, onClick = { onChange(units.copy(weightUnit = unit)) })
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    HorizontalDivider()

    SectionHeader("Distance & Elevation", Icons.Filled.DirectionsWalk)
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            listOf(
                DistanceUnit.KILOMETERS to "Kilometers & meters (km / m)",
                DistanceUnit.MILES to "Miles & feet (mi / ft)",
            ).forEach { (unit, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChange(units.copy(distanceUnit = unit)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(selected = units.distanceUnit == unit, onClick = { onChange(units.copy(distanceUnit = unit)) })
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    HorizontalDivider()

    SectionHeader("Hydration Volume", Icons.Filled.WaterDrop)
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            listOf(
                WaterUnit.LITERS to "Liters (L)",
                WaterUnit.MILLILITERS to "Milliliters (ml)",
                WaterUnit.FLUID_OUNCES to "Fluid Ounces (fl oz)",
            ).forEach { (unit, label) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChange(units.copy(waterUnit = unit)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                ) {
                    RadioButton(selected = units.waterUnit == unit, onClick = { onChange(units.copy(waterUnit = unit)) })
                    Spacer(Modifier.width(12.dp))
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    HorizontalDivider()

    SectionHeader("Energy & Temperature", Icons.Filled.LocalFireDepartment)
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Energy", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(EnergyUnit.KCAL to "kcal", EnergyUnit.KILOJOULES to "kJ").forEach { (unit, label) ->
                FilterChip(
                    selected = units.energyUnit == unit,
                    onClick = { onChange(units.copy(energyUnit = unit)) },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Temperature", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            listOf(TemperatureUnit.CELSIUS to "Celsius (°C)", TemperatureUnit.FAHRENHEIT to "Fahrenheit (°F)").forEach { (unit, label) ->
                FilterChip(
                    selected = units.temperatureUnit == unit,
                    onClick = { onChange(units.copy(temperatureUnit = unit)) },
                    label = { Text(label) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

// ============================================================================
// 3. CONNECTIONS SECTION (Health Connect, Google Health API, AI Coach)
// ============================================================================
@Composable
private fun ConnectionsSettingsSection(
    container: AppContainer,
    settings: AppSettings,
    launchAuthIntent: (android.content.Intent, (android.content.Intent?) -> Unit) -> Unit,
    launchHealthConnectPermission: ((Set<String>) -> Unit) -> Unit,
    onDataSourceChanged: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var hcGranted by remember { mutableStateOf<Boolean?>(null) }
    val hcAvailable = remember { HealthConnectManager.isAvailable(container.appContext) }

    LaunchedEffect(Unit) {
        hcGranted = HealthConnectManager.hasPermissions(container.appContext)
    }

    // Health Connect
    SectionHeader("Health Connect", Icons.Filled.Favorite)
    Text(
        "On-device health platform — always used when connected. When both sources are active, the primary source wins for each metric; the other fills any gaps.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val hcConnected = hcGranted == true
            val hcIsPrimary = settings.dataSourceKind == HealthDataSourceKind.HEALTH_CONNECT

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        hcConnected && hcIsPrimary -> Icons.Filled.CheckCircle
                        hcConnected               -> Icons.Filled.CheckCircle
                        else                      -> Icons.Filled.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = when {
                        hcConnected && hcIsPrimary -> Color(0xFF00C853)
                        hcConnected               -> MaterialTheme.colorScheme.secondary
                        else                      -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        when {
                            hcConnected && hcIsPrimary -> "Connected — Primary source"
                            hcConnected               -> "Connected — Supplement (API is primary)"
                            else                      -> "Not connected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (hcConnected && !hcIsPrimary) {
                        Text(
                            "HC data fills gaps where the API returns nothing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (hcConnected) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = {
                            launchHealthConnectPermission {
                                scope.launch {
                                    hcGranted = HealthConnectManager.hasPermissions(container.appContext)
                                    onDataSourceChanged()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    ) { Text("Permissions") }

                    if (!hcIsPrimary) {
                        Button(
                            onClick = {
                                scope.launch {
                                    container.settingsRepository.setDataSourceKind(HealthDataSourceKind.HEALTH_CONNECT)
                                    onDataSourceChanged()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        ) { Text("Set as primary") }
                    }
                }
            } else {
                Button(
                    onClick = {
                        launchHealthConnectPermission { granted ->
                            scope.launch {
                                val hasPerms = HealthConnectManager.hasPermissions(container.appContext) || granted.isNotEmpty()
                                hcGranted = hasPerms
                                if (hasPerms) {
                                    container.settingsRepository.setDataSourceKind(HealthDataSourceKind.HEALTH_CONNECT)
                                    onDataSourceChanged()
                                }
                            }
                        }
                    },
                    enabled = hcAvailable,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Connect Health Connect") }
            }
        }
    }

    HorizontalDivider()

    // Google Health API (Cloud OAuth)
    SectionHeader("Google Health API (Cloud OAuth)", Icons.Filled.Cloud)
    Text(
        "Cloud connector for historical Google Health / Fit data. Used alongside Health Connect when both are active — whichever is not primary fills metrics the primary didn't provide.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            val apiConnected = container.authManager.isConnected()
            val apiIsPrimary = settings.dataSourceKind == HealthDataSourceKind.GOOGLE_HEALTH_API
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    when {
                        apiConnected && apiIsPrimary -> Icons.Filled.CheckCircle
                        apiConnected                -> Icons.Filled.CheckCircle
                        else                        -> Icons.Filled.RadioButtonUnchecked
                    },
                    contentDescription = null,
                    tint = when {
                        apiConnected && apiIsPrimary -> Color(0xFF00C853)
                        apiConnected                -> MaterialTheme.colorScheme.secondary
                        else                        -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(
                        when {
                            apiConnected && apiIsPrimary ->
                                "Connected (${container.authManager.currentAccountEmail() ?: "Cloud"}) — Primary source"
                            apiConnected ->
                                "Connected (${container.authManager.currentAccountEmail() ?: "Cloud"}) — Supplement (HC is primary)"
                            else -> "Not connected"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (apiConnected && !apiIsPrimary) {
                        Text(
                            "API data fills gaps where Health Connect returns nothing",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (apiConnected && !apiIsPrimary) {
                Button(
                    onClick = {
                        scope.launch {
                            container.settingsRepository.setDataSourceKind(HealthDataSourceKind.GOOGLE_HEALTH_API)
                            onDataSourceChanged()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Set as primary") }
            }

            if (apiConnected) {
                OutlinedButton(
                    onClick = { scope.launch { container.authManager.signOut(settings.oauthConfig); onSignedOut() } },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Sign out of Google Health API") }
            }

            var showAdvanced by remember { mutableStateOf(false) }
            var authError by remember { mutableStateOf<String?>(null) }

            TextButton(onClick = { showAdvanced = !showAdvanced; authError = null }) {
                Text(if (showAdvanced) "Hide OAuth Client Setup" else "Configure OAuth Client ID / Secret")
            }

            // Show auth error if token exchange failed
            authError?.let { err ->
                androidx.compose.material3.Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            "Authentication failed",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        if (err.contains("invalid_client", ignoreCase = true) ||
                            err.contains("client secret", ignoreCase = true)) {
                            Text(
                                "→ Web application clients require a Client Secret. " +
                                "Enter it in the field below, or switch to an Android-type client (no secret needed).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                        if (err.contains("redirect_uri_mismatch", ignoreCase = true)) {
                            Text(
                                "→ The redirect URI com.openfit.mobile:/oauth/callback must be " +
                                "added as an Authorised Redirect URI in your Google Cloud Console " +
                                "OAuth client settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            if (showAdvanced) {
                OAuthEditor(
                    config = settings.oauthConfig,
                    onSave = { updated ->
                        scope.launch {
                            authError = null
                            container.settingsRepository.updateOAuthConfig(updated)
                            if (updated.isConfigured) {
                                val intent = runCatching {
                                    container.authManager.createAuthorizationIntent(updated)
                                }.getOrElse { e ->
                                    authError = e.message ?: "Failed to build auth request"
                                    return@launch
                                }
                                launchAuthIntent(intent) { resultIntent ->
                                    scope.launch {
                                        if (resultIntent == null) {
                                            authError = "Sign-in was cancelled or the browser returned no result."
                                            return@launch
                                        }
                                        val result = runCatching {
                                            container.authManager.handleAuthorizationResponse(resultIntent, updated)
                                        }
                                        if (result.isSuccess) {
                                            // Token saved — now switch data source
                                            container.settingsRepository.setDataSourceKind(
                                                HealthDataSourceKind.GOOGLE_HEALTH_API
                                            )
                                            onDataSourceChanged()
                                            authError = null
                                        } else {
                                            authError = result.exceptionOrNull()?.message
                                                ?: "Token exchange failed — check your client ID and secret."
                                        }
                                    }
                                }
                            }
                        }
                    },
                )
            }
        }
    }

    HorizontalDivider()

    // AI Coach Connection
    SectionHeader("AI Coach", Icons.Filled.Forum)
    Text(
        "Configures the Coach tab and AI summaries. Works with Claude, GPT, Gemini, or any self-hosted assistant you point it at - nothing here is tied to one specific agent.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    AiProviderPicker(
        selected = settings.selectedAiProvider,
        onSelect = { scope.launch { container.settingsRepository.setSelectedAiProvider(it) } },
    )

    settings.selectedAiProvider?.let { kind ->
        if (kind == AiProviderKind.CUSTOM) {
            CustomEndpointsManager(
                container = container,
                settings = settings,
            )
        } else {
            AiProviderConfigForm(
                kind = kind,
                config = settings.aiProviders[kind] ?: AiProviderConfig(kind = kind),
                onSave = { config -> scope.launch { container.settingsRepository.updateAiProviderConfig(kind, config) } },
            )
        }
    }

    HorizontalDivider()
    BackupRestoreSection(container = container)
}

// ============================================================================
// 4. REMINDERS SECTION
// ============================================================================
@Composable
private fun RemindersSettingsSection(
    container: AppContainer,
    reminders: AppReminderSettings,
    onChange: (AppReminderSettings) -> Unit,
) {
    SectionHeader("Daily AI Briefings", Icons.Filled.WbSunny)
    Text(
        "Receive scheduled briefings from your AI Coach summarising sleep recovery and activity goals.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SummaryScheduleRow(
                label = "Morning Sleep Summary",
                schedule = reminders.morningSleepSummary,
                description = "Delivers an AI briefing of your sleep efficiency, HRV recovery, and vitals right after waking up.",
                onChange = { updated -> onChange(reminders.copy(morningSleepSummary = updated)) },
            )

            HorizontalDivider()

            SummaryScheduleRow(
                label = "Evening Activity Summary",
                schedule = reminders.eveningActivitySummary,
                description = "Summarises your daily step goals, active minutes, and calorie burn.",
                onChange = { updated -> onChange(reminders.copy(eveningActivitySummary = updated)) },
            )
        }
    }

    HorizontalDivider()

    SectionHeader("Habit & Health Nudges", Icons.Filled.NotificationsActive)
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Hydration Reminders", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Periodic prompts throughout the day to help you meet your water goal.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = reminders.hydrationReminder,
                    onCheckedChange = { onChange(reminders.copy(hydrationReminder = it)) },
                )
            }

            if (reminders.hydrationReminder) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf(1 to "Every 1 hr", 2 to "Every 2 hrs", 3 to "Every 3 hrs").forEach { (hrs, label) ->
                        FilterChip(
                            selected = reminders.hydrationIntervalHours == hrs,
                            onClick = { onChange(reminders.copy(hydrationIntervalHours = hrs)) },
                            label = { Text(label) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            HorizontalDivider()

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Move & Activity Nudge", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Hourly reminder if you have been sedentary for more than 50 minutes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = reminders.moveReminder,
                    onCheckedChange = { onChange(reminders.copy(moveReminder = it)) },
                )
            }
        }
    }
}

// ============================================================================
// 5. GOALS SECTION
// ============================================================================
@Composable
private fun HealthGoalsSection(
    goals: UserHealthGoals,
    onUpdateGoals: (UserHealthGoals) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    SectionHeader("Daily Health Targets", Icons.Filled.TrackChanges)
    Text(
        "Customise your daily activity, recovery, and physiological targets. These calibrate progress rings and trend charts across the app.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        com.openfit.mobile.ui.common.GoalStepperRow(
            label = "Daily Step Goal",
            valueText = "%,d steps".format(goals.stepGoal),
            onDecrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(stepGoal = (goals.stepGoal - 500).coerceAtLeast(1_000)))
            },
            onIncrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(stepGoal = (goals.stepGoal + 500).coerceAtMost(50_000)))
            },
        )
        val hours = goals.sleepMinutesGoal / 60
        val mins = goals.sleepMinutesGoal % 60
        com.openfit.mobile.ui.common.GoalStepperRow(
            label = "Daily Sleep Goal",
            valueText = "${hours}h ${if (mins > 0) "${mins}m" else "00m"}",
            onDecrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(sleepMinutesGoal = (goals.sleepMinutesGoal - 15).coerceAtLeast(240)))
            },
            onIncrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(sleepMinutesGoal = (goals.sleepMinutesGoal + 15).coerceAtMost(720)))
            },
        )
        com.openfit.mobile.ui.common.GoalStepperRow(
            label = "Daily Hydration Target",
            valueText = "%.2f Liters".format(goals.waterLitersGoal),
            onDecrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(waterLitersGoal = ((goals.waterLitersGoal - 0.25).coerceAtLeast(0.5) * 100).toInt() / 100.0))
            },
            onIncrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(waterLitersGoal = ((goals.waterLitersGoal + 0.25).coerceAtMost(10.0) * 100).toInt() / 100.0))
            },
        )
        com.openfit.mobile.ui.common.GoalStepperRow(
            label = "Daily Calorie Burn Goal",
            valueText = "%,d kcal".format(goals.caloriesGoal),
            onDecrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(caloriesGoal = (goals.caloriesGoal - 100).coerceAtLeast(500)))
            },
            onIncrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(caloriesGoal = (goals.caloriesGoal + 100).coerceAtMost(10_000)))
            },
        )
        com.openfit.mobile.ui.common.GoalStepperRow(
            label = "Daily Active Minutes",
            valueText = "${goals.activeMinutesGoal} min",
            onDecrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(activeMinutesGoal = (goals.activeMinutesGoal - 5).coerceAtLeast(5)))
            },
            onIncrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(activeMinutesGoal = (goals.activeMinutesGoal + 5).coerceAtMost(300)))
            },
        )
        com.openfit.mobile.ui.common.GoalStepperRow(
            label = "Daily Distance Target",
            valueText = "%.1f km".format(goals.distanceKmGoal),
            onDecrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(distanceKmGoal = (goals.distanceKmGoal - 0.5).coerceAtLeast(1.0)))
            },
            onIncrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(distanceKmGoal = (goals.distanceKmGoal + 0.5).coerceAtMost(50.0)))
            },
        )
        com.openfit.mobile.ui.common.GoalStepperRow(
            label = "Floors Climbed Goal",
            valueText = "${goals.floorsGoal} floors",
            onDecrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(floorsGoal = (goals.floorsGoal - 1).coerceAtLeast(1)))
            },
            onIncrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(floorsGoal = (goals.floorsGoal + 1).coerceAtMost(100)))
            },
        )
        val currentWeightTarget = goals.targetWeightKg ?: 70.0
        com.openfit.mobile.ui.common.GoalStepperRow(
            label = "Target Body Weight",
            valueText = "%.1f kg".format(currentWeightTarget),
            onDecrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(targetWeightKg = (currentWeightTarget - 0.5).coerceAtLeast(30.0)))
            },
            onIncrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(targetWeightKg = (currentWeightTarget + 0.5).coerceAtMost(250.0)))
            },
        )
        val currentMaxHr = goals.maxHeartRateBpm ?: 190
        com.openfit.mobile.ui.common.GoalStepperRow(
            label = "Max Heart Rate (for HR zones)",
            valueText = if (goals.maxHeartRateBpm == null) "Not set" else "$currentMaxHr bpm",
            onDecrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(maxHeartRateBpm = (currentMaxHr - 1).coerceAtLeast(100)))
            },
            onIncrement = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onUpdateGoals(goals.copy(maxHeartRateBpm = (currentMaxHr + 1).coerceAtMost(230)))
            },
        )
    }
}

// ============================================================================
// HELPER COMPONENTS
// ============================================================================
@Composable
private fun SectionHeader(text: String, icon: ImageVector? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AiProviderPicker(selected: AiProviderKind?, onSelect: (AiProviderKind) -> Unit) {
    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(vertical = 4.dp)) {
            AiProviderKind.entries.forEach { kind ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelect(kind) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    RadioButton(selected = selected == kind, onClick = { onSelect(kind) })
                    Spacer(Modifier.width(10.dp))
                    Text(kind.displayName(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun AiProviderKind.displayName(): String = when (this) {
    AiProviderKind.CLAUDE -> "Claude (Anthropic)"
    AiProviderKind.OPENAI -> "GPT (OpenAI)"
    AiProviderKind.GEMINI -> "Gemini (Google)"
    AiProviderKind.CUSTOM -> "Custom / self-hosted endpoint"
}

@Composable
private fun AiProviderConfigForm(kind: AiProviderKind, config: AiProviderConfig, onSave: (AiProviderConfig) -> Unit) {
    var apiKey by remember(kind) { mutableStateOf(config.apiKey) }
    var model by remember(kind) { mutableStateOf(config.model) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = apiKey, onValueChange = { apiKey = it },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = model, onValueChange = { model = it },
            label = { Text("Model (leave blank for default)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(AiProviderConfig(kind, apiKey.trim(), model.trim())) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save AI configuration") }
    }
}

// ── Backup & Restore ─────────────────────────────────────────────────────────
@Composable
private fun BackupRestoreSection(container: AppContainer) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    // ── Google Drive ───────────────────────────────────────────────────────
    val driveAccount by container.driveAccount.collectAsState()
    val syncing by container.syncing.collectAsState()
    val syncStatus by container.syncStatus.collectAsState()
    val lastSyncRun by container.driveLastSyncRun.collectAsState()
    var authMessage by remember { mutableStateOf<String?>(null) }

    val consentLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        container.driveAuth.onConsentResult(result.data)
            .onSuccess { token ->
                container.syncWithDrive(token)
                container.rememberDriveConnection()
            }
            .onFailure { e ->
                authMessage = e.message ?: "Google sign-in failed"
            }
    }

    fun connectDrive() {
        authMessage = null
        scope.launch {
            when (val step = container.driveAuth.begin()) {
                is DriveAuth.Step.Token -> {
                    container.syncWithDrive(step.value)
                    container.rememberDriveConnection()
                }
                is DriveAuth.Step.NeedsConsent -> {
                    consentLauncher.launch(
                        IntentSenderRequest.Builder(step.intentSender).build()
                    )
                }
                is DriveAuth.Step.Failed -> authMessage = step.message
            }
        }
    }

    SectionHeader("Backup & Restore", Icons.Filled.Backup)

    // Drive card
    Text(
        "Back up all settings to your Google Drive (private app folder). " +
            "Restores automatically on first launch after a reinstall or on a new device.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (driveAccount != null) {
                // Connected
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF00C853),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "Connected: $driveAccount",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        lastSyncRun?.let { raw ->
                            val formatted = runCatching {
                                val instant = java.time.Instant.parse(raw)
                                val local = java.time.LocalDateTime.ofInstant(
                                    instant, java.time.ZoneId.systemDefault()
                                )
                                java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
                                    .format(local)
                            }.getOrElse { raw.take(16).replace('T', ' ') }
                            Text(
                                "Last sync: $formatted",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { container.driveAuth.token?.let { container.syncWithDrive(it) } },
                        enabled = !syncing,
                        modifier = Modifier.weight(1f),
                    ) {
                        if (syncing) {
                            CircularProgressIndicator(
                                Modifier.size(16.dp), strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("Syncing…")
                        } else {
                            Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sync Now")
                        }
                    }
                    OutlinedButton(
                        onClick = { container.forgetDriveConnection() },
                        modifier = Modifier.weight(1f),
                    ) { Text("Sign Out") }
                }

                syncStatus?.let { (msg, ok) ->
                    Text(
                        msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (ok) Color(0xFF00C853) else MaterialTheme.colorScheme.error,
                    )
                }
            } else {
                // Not connected
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Not connected",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = { connectDrive() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Sign in with Google Drive")
                }

                authMessage?.let { msg ->
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

    // ── Manual file export / import ────────────────────────────────────────
    var fileStatus by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    var fileBusy by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            fileBusy = true
            fileStatus = runCatching {
                val json = com.openfit.mobile.data.backup.SettingsBackup.export(container.settingsRepository)
                context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                "Settings exported successfully" to true
            }.getOrElse { "Export failed: ${it.message}" to false }
            fileBusy = false
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            fileBusy = true
            fileStatus = runCatching {
                val json = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText()
                    ?: error("Could not read file")
                com.openfit.mobile.data.backup.SettingsBackup
                    .import(json, container.settingsRepository)
                    .getOrThrow()
                "Settings restored successfully" to true
            }.getOrElse { "Import failed: ${it.message}" to false }
            fileBusy = false
        }
    }

    Text(
        "Manual export/import — save a JSON file anywhere, share between devices without any account.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { exportLauncher.launch("openfit-settings-backup.json") },
                    enabled = !fileBusy, modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json")) },
                    enabled = !fileBusy, modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Import")
                }
            }
            fileStatus?.let { (msg, ok) ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) Color(0xFF00C853) else MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
/** Manages any number of named, self-hosted/custom AI endpoint profiles
 * (e.g. several Odysseus instances, or any other OpenAI-compatible
 * self-hosted assistant) - add, edit, delete, pick the active one, and test
 * connectivity before relying on it. This is the "easy setup" path for
 * self-hosted agents: since there's no universal login API across them,
 * fast, specific pass/fail feedback on the URL/path/session/auth you typed
 * is the practical alternative to a real login flow. */
@Composable
private fun CustomEndpointsManager(container: AppContainer, settings: AppSettings) {
    val scope = rememberCoroutineScope()
    var editingProfile by remember { mutableStateOf<com.openfit.mobile.model.CustomEndpointProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (settings.customEndpoints.isEmpty()) {
            Text(
                "No custom endpoints saved yet. Add one below - works with any self-hosted assistant that accepts a JSON POST of {message, model?, session?} and replies with text in a response/message/text/reply/content/answer field (this is what Odysseus and similar lightweight chat APIs use). Endpoints expecting the OpenAI /v1/chat/completions format need a small proxy in front.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        settings.customEndpoints.forEach { profile ->
            Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { scope.launch { container.settingsRepository.setSelectedCustomEndpoint(profile.id) } }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = settings.selectedCustomEndpointId == profile.id || (settings.selectedCustomEndpointId == null && settings.customEndpoints.first() == profile),
                        onClick = { scope.launch { container.settingsRepository.setSelectedCustomEndpoint(profile.id) } },
                    )
                    Column(Modifier.weight(1f).padding(start = 4.dp)) {
                        Text(profile.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        Text(profile.baseUrl, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { editingProfile = profile; showEditor = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.displayName}")
                    }
                    IconButton(onClick = { scope.launch { container.settingsRepository.deleteCustomEndpoint(profile.id) } }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Delete ${profile.displayName}")
                    }
                }
            }
        }
        OutlinedButton(
            onClick = { editingProfile = null; showEditor = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Add endpoint")
        }
    }

    if (showEditor) {
        CustomEndpointEditorDialog(
            initial = editingProfile,
            onDismiss = { showEditor = false },
            onSave = { profile ->
                scope.launch { container.settingsRepository.saveCustomEndpoint(profile) }
                showEditor = false
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomEndpointEditorDialog(
    initial: com.openfit.mobile.model.CustomEndpointProfile?,
    onDismiss: () -> Unit,
    onSave: (com.openfit.mobile.model.CustomEndpointProfile) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var name     by remember { mutableStateOf(initial?.name     ?: "") }
    var baseUrl  by remember { mutableStateOf(initial?.baseUrl  ?: "") }
    var wireFormat by remember { mutableStateOf(initial?.wireFormat ?: com.openfit.mobile.model.CustomWireFormat.SIMPLE_MESSAGE) }
    var chatPath by remember { mutableStateOf(initial?.chatPath ?: "/api/chat") }
    var sessionId by remember { mutableStateOf(initial?.sessionId ?: "") }
    var apiKey   by remember { mutableStateOf(initial?.apiKey   ?: "") }
    var model    by remember { mutableStateOf(initial?.model    ?: "") }
    var testState by remember { mutableStateOf<TestConnectionState>(TestConnectionState.Idle) }
    var apiKeyVisible by remember { mutableStateOf(false) }

    fun currentProfile() = com.openfit.mobile.model.CustomEndpointProfile(
        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
        name = name.trim(), baseUrl = baseUrl.trim(),
        chatPath = chatPath.trim().ifBlank { "/api/chat" },
        sessionId = sessionId.trim(), apiKey = apiKey.trim(),
        model = model.trim(), wireFormat = wireFormat,
    )

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header ────────────────────────────────────────────────────
            Text(
                if (initial == null) "Add custom endpoint" else "Edit endpoint",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )

            // ── Identity ──────────────────────────────────────────────────
            SectionLabel("Identity")
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("Name") },
                placeholder = { Text("e.g. Odysseus – home") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = baseUrl, onValueChange = { baseUrl = it },
                label = { Text("Base URL") },
                placeholder = { Text("https://my-assistant.example.com") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            // ── Format ────────────────────────────────────────────────────
            SectionLabel("Request format")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = wireFormat == com.openfit.mobile.model.CustomWireFormat.SIMPLE_MESSAGE,
                    onClick = {
                        if (chatPath.isBlank() || chatPath == "/v1/chat/completions") chatPath = "/api/chat"
                        wireFormat = com.openfit.mobile.model.CustomWireFormat.SIMPLE_MESSAGE
                    },
                    label = { Text("Simple") },
                )
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = wireFormat == com.openfit.mobile.model.CustomWireFormat.OPENAI_CHAT,
                    onClick = {
                        if (chatPath.isBlank() || chatPath == "/api/chat") chatPath = "/v1/chat/completions"
                        wireFormat = com.openfit.mobile.model.CustomWireFormat.OPENAI_CHAT
                    },
                    label = { Text("OpenAI-compatible") },
                )
            }
            Text(
                if (wireFormat == com.openfit.mobile.model.CustomWireFormat.OPENAI_CHAT)
                    "Standard /v1/chat/completions — Ollama, LM Studio, vLLM, text-generation-webui, etc."
                else
                    "Lightweight {message → text} — Odysseus and similar self-hosted chat APIs.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = chatPath, onValueChange = { chatPath = it },
                label = { Text("Chat path") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
            )

            // ── Authentication ────────────────────────────────────────────
            SectionLabel("Authentication")
            OutlinedTextField(
                value = apiKey, onValueChange = { apiKey = it },
                label = { Text("API key / token (optional)") },
                singleLine = true,
                visualTransformation = if (apiKeyVisible)
                    androidx.compose.ui.text.input.VisualTransformation.None
                else
                    androidx.compose.ui.text.input.PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            if (apiKeyVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                            contentDescription = if (apiKeyVisible) "Hide" else "Show",
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            // ── Model / Session ───────────────────────────────────────────
            SectionLabel("Optional fields")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = model, onValueChange = { model = it },
                    label = { Text("Model") },
                    placeholder = { Text("e.g. llama3") },
                    singleLine = true, modifier = Modifier.weight(1f),
                )
                if (wireFormat == com.openfit.mobile.model.CustomWireFormat.SIMPLE_MESSAGE) {
                    OutlinedTextField(
                        value = sessionId, onValueChange = { sessionId = it },
                        label = { Text("Session ID") },
                        singleLine = true, modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Test + Save ───────────────────────────────────────────────
            HorizontalDivider()
            when (val st = testState) {
                is TestConnectionState.Success -> Text(
                    "✓ Connected — \"${st.reply}\"",
                    style = MaterialTheme.typography.bodySmall, color = Color(0xFF00C853),
                )
                is TestConnectionState.Failure -> Text(
                    "✗ ${st.error}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                else -> {}
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        testState = TestConnectionState.Testing
                        scope.launch {
                            testState = try {
                                val result = com.openfit.mobile.data.ai.CustomProvider().complete(
                                    config = currentProfile().toProviderConfig(),
                                    systemPrompt = "You are a connectivity test.",
                                    userPrompt = "Reply with exactly one word: OK",
                                )
                                TestConnectionState.Success(result.text.take(120))
                            } catch (e: Exception) {
                                TestConnectionState.Failure(e.message ?: "Unknown error")
                            }
                        }
                    },
                    enabled = baseUrl.isNotBlank() && testState != TestConnectionState.Testing,
                    modifier = Modifier.weight(1f),
                ) {
                    if (testState == TestConnectionState.Testing) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text("Test")
                }
                Button(
                    onClick = { onSave(currentProfile()) },
                    enabled = baseUrl.isNotBlank(),
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Save")
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
    )
}

private sealed class TestConnectionState {
    data object Idle : TestConnectionState()
    data object Testing : TestConnectionState()
    data class Success(val reply: String) : TestConnectionState()
    data class Failure(val error: String) : TestConnectionState()
}

/** Optional, user-authored personalisation (their own name + persona
 * instructions) applied to every AI summary/Coach reply, for every provider
 * kind. Nothing about any specific agent's identity is assumed here - both
 * fields are blank by default and purely reflect what the user types. */
@Composable
private fun PersonalisationSettingsSection(
    personalisation: AiPersonalisationSettings,
    onSave: (AiPersonalisationSettings) -> Unit,
) {
    var name by remember(personalisation) { mutableStateOf(personalisation.userDisplayName) }
    var persona by remember(personalisation) { mutableStateOf(personalisation.personaInstructions) }

    SectionHeader("Personalisation", Icons.Filled.Person)
    Text(
        "Applies to AI summaries and Coach chat, whichever provider you use above.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your name") },
            supportingText = { Text("Optional - lets AI summaries and Coach chat address you by name.") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = persona,
            onValueChange = { persona = it },
            label = { Text("Custom persona / instructions") },
            supportingText = { Text("Optional. If your assistant has its own personality, describe it here so the app never overrides it. Leave blank for a neutral default coach voice.") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = { onSave(AiPersonalisationSettings(name.trim(), persona.trim())) },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Save personalisation") }
    }
}

@Composable
private fun SummaryScheduleRow(
    label: String,
    schedule: SummarySchedule,
    description: String? = null,
    onChange: (SummarySchedule) -> Unit,
) {
    var hourText by remember(schedule) { mutableStateOf(schedule.hour.toString()) }
    var minuteText by remember(schedule) { mutableStateOf(schedule.minute.toString().padStart(2, '0')) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                description?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Switch(
                checked = schedule.enabled,
                onCheckedChange = { onChange(schedule.copy(enabled = it)) },
            )
        }
        if (schedule.enabled) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = hourText,
                    onValueChange = { v -> hourText = v.filter { it.isDigit() }.take(2) },
                    label = { Text("Hour (0-23)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(110.dp),
                )
                Text(":", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = minuteText,
                    onValueChange = { v -> minuteText = v.filter { it.isDigit() }.take(2) },
                    label = { Text("Min") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.width(90.dp),
                )
                Button(onClick = {
                    val hour = hourText.toIntOrNull()?.coerceIn(0, 23) ?: schedule.hour
                    val minute = minuteText.toIntOrNull()?.coerceIn(0, 59) ?: schedule.minute
                    onChange(schedule.copy(hour = hour, minute = minute))
                }) { Text("Set") }
            }
        }
    }
}

@Composable
private fun OAuthEditor(config: OAuthConfig, onSave: (OAuthConfig) -> Unit) {
    var clientId     by remember { mutableStateOf(config.clientId) }
    var clientSecret by remember { mutableStateOf(config.clientSecret) }

    // Derive the redirect URI the user must register in Cloud Console
    val redirectUri = config.copy(
        clientId = clientId.trim(), clientSecret = clientSecret.trim()
    ).redirectUri

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // ── Setup instructions ───────────────────────────────────────────
        androidx.compose.material3.Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "Google Cloud Console setup",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                )
                Text(
                    "1. Create a project and enable the Google Health API.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "2. APIs & Services → Credentials → Create OAuth 2.0 Client ID.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "3. Choose application type:\n" +
                    "   • Android — package com.openfit.mobile + your SHA-1. No secret needed.\n" +
                    "     Also add the redirect URI below under \"Authorised redirect URIs\".\n" +
                    "   • Web application — enter client ID AND secret below. Register the redirect URI.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Text(
                    "Redirect URI to register:",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                )
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(
                        "com.openfit.mobile:/oauth/callback",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        // ── Fields ────────────────────────────────────────────────────────
        OutlinedTextField(
            value = clientId,
            onValueChange = { clientId = it },
            label = { Text("Client ID") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = clientSecret,
            onValueChange = { clientSecret = it },
            label = { Text("Client Secret (Web app clients only — leave blank for Android client)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = {
                onSave(OAuthConfig(clientId = clientId.trim(), clientSecret = clientSecret.trim()))
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = clientId.isNotBlank(),
        ) {
            Text("Save & connect")
        }
    }
}

// ============================================================================
// 6. ABOUT SECTION
// ============================================================================
@Composable
private fun AboutSettingsSection() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "dev"
        } catch (_: Exception) { "dev" }
    }

    SectionHeader("OpenFit Android", Icons.Filled.Info)
    Text(
        "Open-source health & fitness platform integrating Google Health Connect, Google Health OAuth API, and intelligent local/remote AI coaching.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            InfoRow("Version", versionName)
            HorizontalDivider()
            InfoRow("Architecture", "Jetpack Compose + Material 3")
            HorizontalDivider()
            InfoRow("Data Privacy", "100% On-Device Local Storage")
            HorizontalDivider()
            InfoRow("AI Coach", "Multi-provider, fully user-configurable")
            HorizontalDivider()
            InfoRow("Licence", "Apache 2.0 / Open Source")
        }
    }

    SectionHeader("Supported Wearables", Icons.Filled.Watch)
    Text(
        "Seamlessly pairs with Google Pixel Watch (all generations), Fitbit Air, trackers & bands, Oura Ring, WHOOP bands, Samsung Galaxy Watch, and smart scales via Health Connect.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

