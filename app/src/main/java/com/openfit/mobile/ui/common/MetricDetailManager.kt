package com.openfit.mobile.ui.common

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.openfit.mobile.data.settings.UserHealthGoals
import com.openfit.mobile.ui.charts.ChartColors
import kotlinx.coroutines.delay

enum class GoalType {
    STEPS,
    SLEEP,
    WATER,
    CALORIES,
    ACTIVE_MINUTES,
    DISTANCE,
    FLOORS,
    WEIGHT,
}

data class MetricDetail(
    val title: String,
    val currentValue: String? = null,
    val icon: ImageVector,
    val accentColor: Color,
    val summary: String,
    val measurement: String = "",
    val meaning: String = "",
    val goalType: GoalType? = null,
    val attribution: com.openfit.mobile.model.MetricAttribution? = null,
)

object MetricKnowledge {
    fun getDetail(
        label: String,
        currentValue: String? = null,
        fallbackIcon: ImageVector = Icons.Filled.Info,
        devices: List<com.openfit.mobile.model.PairedDevice> = emptyList(),
    ): MetricDetail {
        val key = label.lowercase().trim()
        val base = when {
            "hrv" in key -> MetricDetail(
                title = "Heart Rate Variability (HRV)",
                currentValue = currentValue,
                icon = Icons.Filled.MonitorHeart,
                accentColor = ChartColors.Hrv,
                summary = "RMSSD variance in milliseconds between consecutive heartbeats during deep sleep.",
                measurement = "Monitored using high-precision optical photoplethysmography (PPG) sensors that detect minute blood volume pulse changes between contractions.",
                meaning = "Higher HRV reflects parasympathetic nervous system dominance, indicating low systemic stress, strong cardiovascular recovery, and high physical readiness.",
                goalType = null,
            )
            "spo2" in key || "blood oxygen" in key || "oxygen" in key -> MetricDetail(
                title = "Blood Oxygen (SpO2)",
                currentValue = currentValue,
                icon = Icons.Filled.Bloodtype,
                accentColor = ChartColors.Spo2,
                summary = "Percentage of oxygen-saturated hemoglobin circulating throughout your blood.",
                measurement = "Measured during sleep using red (660nm) and infrared (940nm) optical sensors; oxygenated and deoxygenated blood absorb light distinctly.",
                meaning = "Healthy baseline is 95%–100%. Persistent nightly dips below 90% can indicate breathing disturbances, sleep apnea, or altitude adjustments.",
                goalType = null,
            )
            "resting" in key || "rhr" in key -> MetricDetail(
                title = "Resting Heart Rate (RHR)",
                currentValue = currentValue,
                icon = Icons.Filled.Favorite,
                accentColor = ChartColors.Heart,
                summary = "Your baseline heart rate in beats per minute while completely still or waking up.",
                measurement = "Continuously sampled throughout the day and during sleep using green optical PPG sensors on your wrist.",
                meaning = "A lower resting heart rate (typically 50–70 bpm for adults, 40–50 bpm for athletes) signifies efficient myocardial function and aerobic conditioning.",
                goalType = null,
            )
            "breath" in key || "respiratory" in key -> MetricDetail(
                title = "Breathing Rate",
                currentValue = currentValue,
                icon = Icons.Filled.Air,
                accentColor = ChartColors.Breathing,
                summary = "Average number of respiration cycles (breaths) per minute during sleep.",
                measurement = "Calculated from heart rate variability intervals via Respiratory Sinus Arrhythmia (RSA), where heart rate naturally speeds up on inhale and slows on exhale.",
                meaning = "A normal baseline is 12–20 rpm. A sudden jump above your typical trend often serves as an early sign of fever, respiratory illness, or systemic fatigue.",
                goalType = null,
            )
            "temperature" in key || "temp" in key -> MetricDetail(
                title = "Skin Temperature",
                currentValue = currentValue,
                icon = Icons.Filled.Thermostat,
                accentColor = ChartColors.Temperature,
                summary = "Variation in nightly wrist skin temperature compared to your personal baseline (°C).",
                measurement = "Sampled every minute during sleep by a dedicated thermal sensor resting directly against the skin.",
                meaning = "Deviations reflect circadian rhythms, bedroom thermal conditions, menstrual phases, or an active immune response fighting off an infection.",
                goalType = null,
            )
            "step" in key || "movement" in key -> MetricDetail(
                title = "Steps & Movement",
                currentValue = currentValue,
                icon = Icons.Filled.DirectionsWalk,
                accentColor = ChartColors.Movement,
                summary = "Cumulative count of footsteps taken throughout the day.",
                measurement = "Recorded by a 3-axis accelerometer detecting acceleration waveforms and cadence patterns unique to human walking.",
                meaning = "Promotes non-exercise activity thermogenesis (NEAT), enhances insulin sensitivity, and lowers cardiovascular morbidity. 10,000 steps is the standard benchmark.",
                goalType = GoalType.STEPS,
            )
            "sleep" in key -> MetricDetail(
                title = "Sleep Duration & Quality",
                currentValue = currentValue,
                icon = Icons.Filled.Bedtime,
                accentColor = ChartColors.Sleep,
                summary = "Total nightly sleep duration and sleep architecture stages (Deep, REM, Light, Awake).",
                measurement = "Determined by combining actigraphy (motion stillness) with optical heart rate deceleration and autonomic HRV cycles.",
                meaning = "Deep sleep rebuilds muscle tissue and releases growth hormone; REM sleep consolidates memory and cognitive resilience. 7–9 hours is recommended.",
                goalType = GoalType.SLEEP,
            )
            "calorie" in key -> MetricDetail(
                title = "Total Energy Burn",
                currentValue = currentValue,
                icon = Icons.Filled.LocalFireDepartment,
                accentColor = ChartColors.Movement,
                summary = "Total kilocalories expended throughout the 24-hour day.",
                measurement = "Combines your Basal Metabolic Rate (BMR, calculated from height, weight, sex, and age) with active burn tracked from motion and elevated heart rate.",
                meaning = "Crucial for regulating energy balance, weight loss, or muscle gain. Active burn helps you fuel appropriately for physical output.",
                goalType = GoalType.CALORIES,
            )
            "distance" in key -> MetricDetail(
                title = "Distance",
                currentValue = currentValue,
                icon = Icons.Filled.Terrain,
                accentColor = ChartColors.Movement,
                summary = "Total distance travelled walking, running, or hiking.",
                measurement = "Derived from device GPS during tracked workouts or computed from stride length calibrated to your height and cadence.",
                meaning = "Provides an objective measure of total ground covered, essential for tracking endurance progression and cardiovascular training.",
                goalType = GoalType.DISTANCE,
            )
            "floor" in key -> MetricDetail(
                title = "Floors Climbed",
                currentValue = currentValue,
                icon = Icons.Filled.Terrain,
                accentColor = ChartColors.Movement,
                summary = "Flights of stairs climbed, equivalent to ~3 meters (10 feet) of elevation gain per floor.",
                measurement = "Monitored using a barometric altimeter detecting slight drops in atmospheric pressure accompanied by continuous upward stepping.",
                meaning = "Stair climbing activates glutes, hamstrings, and calves against gravity, delivering higher aerobic and muscular stimulus than flat walking.",
                goalType = GoalType.FLOORS,
            )
            "active" in key || "zone" in key -> MetricDetail(
                title = "Active & Zone Minutes",
                currentValue = currentValue,
                icon = Icons.Filled.DirectionsRun,
                accentColor = ChartColors.Movement,
                summary = "Minutes spent in elevated heart rate zones (Fat Burn, Cardio, Peak).",
                measurement = "Accumulated when optical sensors detect heart rate elevated above 50% of your maximum heart rate (220 minus age) for sustained periods.",
                meaning = "The WHO recommends 150 minutes of moderate activity weekly to build cardiac longevity, metabolic resilience, and endurance.",
                goalType = GoalType.ACTIVE_MINUTES,
            )
            "weight" in key -> MetricDetail(
                title = "Weight",
                currentValue = currentValue,
                icon = Icons.Filled.MonitorWeight,
                accentColor = ChartColors.Body,
                summary = "Total body mass in kilograms.",
                measurement = "Captured from connected Bluetooth/Wi-Fi smart scales or manual entries synced with Google Health Connect.",
                meaning = "Weekly 7-day rolling averages smooth out daily water weight fluctuations, providing the true trajectory of your body composition.",
                goalType = GoalType.WEIGHT,
            )
            "body fat" in key || "fat" in key -> MetricDetail(
                title = "Body Fat Percentage",
                currentValue = currentValue,
                icon = Icons.Filled.PieChart,
                accentColor = ChartColors.Body,
                summary = "Percentage of your total body mass comprised of adipose (fat) tissue.",
                measurement = "Calculated via Bioelectrical Impedance Analysis (BIA), passing low-voltage safe electrical currents through the body to measure tissue resistance.",
                meaning = "A superior indicator of body composition and cardiovascular health compared to standard Body Mass Index (BMI).",
                goalType = null,
            )
            "hydration" in key || "water" in key -> MetricDetail(
                title = "Daily Hydration",
                currentValue = currentValue,
                icon = Icons.Filled.WaterDrop,
                accentColor = ChartColors.Hrv,
                summary = "Total fluid intake consumed in litres toward your daily hydration target.",
                measurement = "Logged via smart water bottles, third-party apps, or Health Connect entries.",
                meaning = "Maintains optimal blood volume, joint lubrication, cognitive focus, and body temperature. Dehydration of just 2% degrades athletic performance.",
                goalType = GoalType.WATER,
            )
            "cardio" in key || "vo2" in key -> MetricDetail(
                title = "Cardio Fitness (VO2 Max)",
                currentValue = currentValue,
                icon = Icons.Filled.DirectionsRun,
                accentColor = ChartColors.Cardio,
                summary = "Maximum volume of oxygen (in mL/kg/min) your body can consume during all-out exertion.",
                measurement = "Estimated using heart rate response, pace, age, and GPS elevation during brisk outdoor walking or running sessions.",
                meaning = "The premier clinical benchmark of cardiorespiratory fitness and one of the strongest predictors of all-cause longevity.",
                goalType = null,
            )
            else -> MetricDetail(
                title = label,
                currentValue = currentValue,
                icon = fallbackIcon,
                accentColor = Color(0xFF4A90E2),
                summary = "Health data monitored by your connected devices and synced with Google Health Connect.",
                measurement = "Sensed continuously by wearable optical and motion sensors.",
                meaning = "Contributes to your daily physiological wellness and activity profile.",
                goalType = null,
            )
        }
        return base.copy(
            attribution = com.openfit.mobile.model.WearableRegistry.getAttribution(base.title, devices)
        )
    }
}

/** Global inspector state to present non-truncated in-app toasts & goal sheets */
class MetricInspectorState {
    var activeToast by mutableStateOf<MetricDetail?>(null)
        private set
    var activeSheet by mutableStateOf<MetricDetail?>(null)
        private set

    fun showToast(detail: MetricDetail) {
        activeToast = detail
    }

    fun showSheet(detail: MetricDetail) {
        activeSheet = detail
        activeToast = null // Dismiss toast if opening full sheet
    }

    fun dismissToast() {
        activeToast = null
    }

    fun dismissSheet() {
        activeSheet = null
    }
}

val LocalMetricInspector = compositionLocalOf { MetricInspectorState() }

/** Elegant floating in-app toast banner with zero text truncation */
@Composable
fun CustomInAppToast(
    detail: MetricDetail?,
    onDismiss: () -> Unit,
    onOpenSheet: (MetricDetail) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Auto-dismiss after 4.5 seconds
    LaunchedEffect(detail) {
        if (detail != null) {
            delay(4500)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = detail != null,
        enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(300)) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(250)) + fadeOut(),
        modifier = modifier,
    ) {
        if (detail != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .clickable { onOpenSheet(detail) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(detail.accentColor.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = detail.icon,
                            contentDescription = null,
                            tint = detail.accentColor,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = detail.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            detail.currentValue?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = detail.accentColor,
                                )
                            }
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "${detail.summary} ${detail.meaning}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                        )

                        detail.attribution?.let { attr ->
                            if (attr.contributingDevices.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Sensors,
                                        contentDescription = null,
                                        tint = Color(0xFF00C853),
                                        modifier = Modifier.size(13.dp),
                                    )
                                    Text(
                                        text = "Source: ${attr.contributingDevices.joinToString(", ") { it.deviceName }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (detail.goalType != null) {
                                TextButton(
                                    onClick = { onOpenSheet(detail) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                ) {
                                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Set custom goal", style = MaterialTheme.typography.labelSmall)
                                }
                            } else {
                                TextButton(
                                    onClick = { onOpenSheet(detail) },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp),
                                ) {
                                    Icon(Icons.Filled.Info, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Learn more", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp).padding(start = 4.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

/** Full interactive detail & goal configuration sheet */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricDetailSheet(
    detail: MetricDetail?,
    goals: UserHealthGoals,
    onUpdateGoals: (UserHealthGoals) -> Unit,
    onDismiss: () -> Unit,
) {
    if (detail == null) return

    val haptics = LocalHapticFeedback.current
    var currentGoals by remember(goals) { mutableStateOf(goals) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(detail.accentColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(detail.icon, contentDescription = null, tint = detail.accentColor, modifier = Modifier.size(28.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(detail.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    detail.currentValue?.let {
                        Text("Current: $it", style = MaterialTheme.typography.titleMedium, color = detail.accentColor, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            HorizontalDivider()

            // 1. What it is
            SectionBlock(
                title = "What is it?",
                body = detail.summary,
                icon = Icons.Filled.Info,
            )

            // 2. How it's measured
            SectionBlock(
                title = "How is it measured?",
                body = detail.measurement,
                icon = Icons.Filled.Sensors,
            )

            // 3. Clinical & health meaning
            SectionBlock(
                title = "What does it mean for your health?",
                body = detail.meaning,
                icon = Icons.Filled.HealthAndSafety,
            )

            // 4. Contributing Devices & Sensors Attribution
            detail.attribution?.let { attr ->
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Filled.DevicesOther, contentDescription = null, tint = detail.accentColor, modifier = Modifier.size(20.dp))
                            Text(
                                "Contributing Devices & Sensors",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Color(0xFF00C853).copy(alpha = 0.15f),
                        ) {
                            Text(
                                "${attr.contributingDevices.size} Active",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF00C853),
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }

                    Text(
                        attr.summaryText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    attr.contributingDevices.forEach { dev ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (dev.imageRes != null) {
                                    androidx.compose.foundation.Image(
                                        painter = androidx.compose.ui.res.painterResource(dev.imageRes),
                                        contentDescription = dev.deviceName,
                                        modifier = Modifier.size(44.dp),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(dev.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                                    }
                                }

                                Column(Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Text(dev.deviceName, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        dev.batteryPercent?.let { bat ->
                                            Surface(
                                                shape = RoundedCornerShape(6.dp),
                                                color = MaterialTheme.colorScheme.surfaceVariant,
                                            ) {
                                                Text(
                                                    "$bat%",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                                                )
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        dev.sensorDescription,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }

                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = "Active contributor",
                                    tint = Color(0xFF00C853),
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }

            // 4. Custom Goal Section (if metric supports a goal)
            if (detail.goalType != null) {
                HorizontalDivider()
                Text("Custom Goal & Target", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                when (detail.goalType) {
                    GoalType.STEPS -> {
                        GoalStepperRow(
                            label = "Daily Step Goal",
                            valueText = "%,d steps".format(currentGoals.stepGoal),
                            onDecrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(stepGoal = (currentGoals.stepGoal - 500).coerceAtLeast(1_000))
                                onUpdateGoals(currentGoals)
                            },
                            onIncrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(stepGoal = (currentGoals.stepGoal + 500).coerceAtMost(50_000))
                                onUpdateGoals(currentGoals)
                            },
                        )
                    }
                    GoalType.SLEEP -> {
                        val hours = currentGoals.sleepMinutesGoal / 60
                        val mins = currentGoals.sleepMinutesGoal % 60
                        GoalStepperRow(
                            label = "Daily Sleep Goal",
                            valueText = "${hours}h ${if (mins > 0) "${mins}m" else "00m"}",
                            onDecrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(sleepMinutesGoal = (currentGoals.sleepMinutesGoal - 15).coerceAtLeast(240))
                                onUpdateGoals(currentGoals)
                            },
                            onIncrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(sleepMinutesGoal = (currentGoals.sleepMinutesGoal + 15).coerceAtMost(720))
                                onUpdateGoals(currentGoals)
                            },
                        )
                    }
                    GoalType.WATER -> {
                        GoalStepperRow(
                            label = "Daily Hydration Target",
                            valueText = "%.2f Liters".format(currentGoals.waterLitersGoal),
                            onDecrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(waterLitersGoal = ((currentGoals.waterLitersGoal - 0.25).coerceAtLeast(0.5) * 100).toInt() / 100.0)
                                onUpdateGoals(currentGoals)
                            },
                            onIncrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(waterLitersGoal = ((currentGoals.waterLitersGoal + 0.25).coerceAtMost(10.0) * 100).toInt() / 100.0)
                                onUpdateGoals(currentGoals)
                            },
                        )
                    }
                    GoalType.CALORIES -> {
                        GoalStepperRow(
                            label = "Daily Calorie Goal",
                            valueText = "%,d kcal".format(currentGoals.caloriesGoal),
                            onDecrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(caloriesGoal = (currentGoals.caloriesGoal - 100).coerceAtLeast(500))
                                onUpdateGoals(currentGoals)
                            },
                            onIncrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(caloriesGoal = (currentGoals.caloriesGoal + 100).coerceAtMost(10_000))
                                onUpdateGoals(currentGoals)
                            },
                        )
                    }
                    GoalType.ACTIVE_MINUTES -> {
                        GoalStepperRow(
                            label = "Daily Active Minutes",
                            valueText = "${currentGoals.activeMinutesGoal} min",
                            onDecrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(activeMinutesGoal = (currentGoals.activeMinutesGoal - 5).coerceAtLeast(5))
                                onUpdateGoals(currentGoals)
                            },
                            onIncrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(activeMinutesGoal = (currentGoals.activeMinutesGoal + 5).coerceAtMost(300))
                                onUpdateGoals(currentGoals)
                            },
                        )
                    }
                    GoalType.DISTANCE -> {
                        GoalStepperRow(
                            label = "Daily Distance Target",
                            valueText = "%.1f km".format(currentGoals.distanceKmGoal),
                            onDecrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(distanceKmGoal = (currentGoals.distanceKmGoal - 0.5).coerceAtLeast(1.0))
                                onUpdateGoals(currentGoals)
                            },
                            onIncrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(distanceKmGoal = (currentGoals.distanceKmGoal + 0.5).coerceAtMost(50.0))
                                onUpdateGoals(currentGoals)
                            },
                        )
                    }
                    GoalType.FLOORS -> {
                        GoalStepperRow(
                            label = "Floors Climbed Goal",
                            valueText = "${currentGoals.floorsGoal} floors",
                            onDecrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(floorsGoal = (currentGoals.floorsGoal - 1).coerceAtLeast(1))
                                onUpdateGoals(currentGoals)
                            },
                            onIncrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(floorsGoal = (currentGoals.floorsGoal + 1).coerceAtMost(100))
                                onUpdateGoals(currentGoals)
                            },
                        )
                    }
                    GoalType.WEIGHT -> {
                        val currentTarget = currentGoals.targetWeightKg ?: 70.0
                        GoalStepperRow(
                            label = "Target Body Weight",
                            valueText = "%.1f kg".format(currentTarget),
                            onDecrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(targetWeightKg = (currentTarget - 0.5).coerceAtLeast(30.0))
                                onUpdateGoals(currentGoals)
                            },
                            onIncrement = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                currentGoals = currentGoals.copy(targetWeightKg = (currentTarget + 0.5).coerceAtMost(250.0))
                                onUpdateGoals(currentGoals)
                            },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Done")
            }
        }
    }
}

@Composable
private fun SectionBlock(title: String, body: String, icon: ImageVector) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        }
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun GoalStepperRow(
    label: String,
    valueText: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(valueText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = onDecrement) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                FilledTonalIconButton(onClick = onIncrement) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }
        }
    }
}
