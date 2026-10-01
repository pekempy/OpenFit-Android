package com.openfit.mobile.ui.today

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.openfit.mobile.AppContainer
import com.openfit.mobile.model.DailySnapshot
import com.openfit.mobile.ui.charts.ChartColors
import com.openfit.mobile.ui.charts.DesktopLineChart
import com.openfit.mobile.ui.charts.HourlyBarChart
import com.openfit.mobile.ui.charts.RingProgress
import com.openfit.mobile.ui.charts.SegmentedBar
import com.openfit.mobile.ui.common.EmptyStateMessage
import com.openfit.mobile.ui.common.LoadingBlock
import com.openfit.mobile.ui.common.SimpleViewModelFactory
import com.openfit.mobile.ui.common.UnitFormatter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TodayScreen(
    container: AppContainer,
    state: TodayUiState,
    onRefresh: () -> Unit,
    onConnectRequested: () -> Unit,
) {
    val isRefreshing = (state as? TodayUiState.Success)?.isRefreshing ?: false
    Scaffold(topBar = { TopAppBar(title = { Text("Today") }) }) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (val s = state) {
                is TodayUiState.Loading -> LoadingBlock(Modifier.fillMaxSize())
                is TodayUiState.NotConnected -> Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    EmptyStateMessage("Connect your Google Health account in Settings to see your data here.")
                    Button(onClick = onConnectRequested, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) {
                        Text("Connect Google Health")
                    }
                }
                is TodayUiState.Error -> Column(modifier = Modifier.fillMaxSize()) {
                    EmptyStateMessage(s.message)
                    Button(onClick = onRefresh, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                        Text("Retry")
                    }
                }
                is TodayUiState.Success -> TodayContent(container, PaddingValues(0.dp), s)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TodayContent(container: AppContainer, padding: PaddingValues, state: TodayUiState.Success) {
    val haptics = LocalHapticFeedback.current
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = null)
    val goals = settings?.goals ?: com.openfit.mobile.data.settings.UserHealthGoals()
    val units = settings?.units ?: com.openfit.mobile.data.settings.AppUnitSettings()
    val bundle = state.bundle
    val today = bundle.today
    val trend = bundle.trend
    val zone = ZoneId.systemDefault()

    val dateLabel = runCatching {
        LocalDate.parse(bundle.selectedDate)
            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(Locale.getDefault()))
    }.getOrDefault(bundle.selectedDate)

    val insights = remember(bundle, goals) {
        com.openfit.mobile.data.insights.InsightsEngine.generate(bundle, goals)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {

        // ── Date header ──────────────────────────────────────────────────────
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                state.accountEmail?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(dateLabel, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }

        // ── Insights ─────────────────────────────────────────────────────────
        if (insights.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Insights", style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer)
                        insights.forEach { insight ->
                            val dotColor = when (insight.kind) {
                                com.openfit.mobile.data.insights.InsightKind.POSITIVE -> Color(0xFF00C853)
                                com.openfit.mobile.data.insights.InsightKind.ATTENTION -> MaterialTheme.colorScheme.error
                                com.openfit.mobile.data.insights.InsightKind.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Row(verticalAlignment = Alignment.Top) {
                                Box(Modifier.padding(top = 6.dp).size(6.dp)
                                    .clip(CircleShape).background(dotColor))
                                Spacer(Modifier.width(10.dp))
                                Text(insight.text, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }
                }
            }
        }

        // ── Activity hero card ───────────────────────────────────────────────
        item {
            val stepGoalFraction = ((today.steps ?: 0) / goals.stepGoal.toFloat()).coerceIn(0f, 1f)
            val hasHourlySteps = today.stepsHourly.any { it > 0 }
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.DirectionsWalk, contentDescription = null,
                                    tint = ChartColors.Movement, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Movement", style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                today.steps?.let { "%,d".format(it) } ?: "—",
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Bold,
                                color = ChartColors.Movement,
                            )
                            Text("steps · goal %,d".format(goals.stepGoal),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            // Sub-metrics row
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                today.distanceMeters?.let { m ->
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.Straighten, contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(UnitFormatter.formatDistance(m, units),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                today.calories?.let { cal ->
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(UnitFormatter.formatCalories(cal, units),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                today.floors?.let { f ->
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Icon(Icons.Filled.Stairs, contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("$f floors",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                        RingProgress(
                            progress = stepGoalFraction,
                            color = ChartColors.Movement,
                            size = 80.dp,
                            centerText = "${(stepGoalFraction * 100).toInt()}%",
                            centerSubText = "goal",
                        )
                    }
                    // Hourly step chart — shows when/how steps were distributed
                    if (hasHourlySteps) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                        HourlyBarChart(
                            values = today.stepsHourly,
                            color = ChartColors.Movement,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        // ── Sleep card ───────────────────────────────────────────────────────
        today.sleep?.let { sleep ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Bedtime, contentDescription = null,
                                tint = ChartColors.Sleep, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Sleep", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom) {
                            Column {
                                Text(formatDuration(sleep.totalMinutes),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold)
                                val sleepGoalMin = goals.sleepMinutesGoal
                                Text("goal ${formatDuration(sleepGoalMin)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            sleep.efficiencyPercent?.let { eff ->
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("$eff%", style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.SemiBold, color = ChartColors.Sleep)
                                    Text("efficiency", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        // Sleep stage segment bar
                        if (sleep.stages.isNotEmpty()) {
                            val stageColors = mapOf(
                                "deep" to ChartColors.Sleep,
                                "rem" to Color(0xFF7B61FF),
                                "light" to Color(0xFF60A5FA),
                                "wake" to MaterialTheme.colorScheme.outlineVariant,
                            )
                            val segments = sleep.stages.mapNotNull { stage ->
                                stageColors[stage.stage]?.let { color ->
                                    Pair(stage.minutes.toFloat(), color)
                                }
                            }
                            if (segments.isNotEmpty()) {
                                SegmentedBar(segments = segments, height = 12.dp, cornerRadius = 6.dp)
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    sleep.stages.forEach { stage ->
                                        val color = stageColors[stage.stage] ?: return@forEach
                                        val label = when (stage.stage) {
                                            "deep" -> "Deep"
                                            "rem" -> "REM"
                                            "light" -> "Light"
                                            "wake" -> "Awake"
                                            else -> stage.stage
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                                            Text("$label ${stage.minutes}m",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                        // Bedtime / wake time
                        val bedtime = runCatching {
                            Instant.parse(sleep.startTimeIso).atZone(zone)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                        }.getOrNull()
                        val wakeTime = runCatching {
                            Instant.parse(sleep.endTimeIso).atZone(zone)
                                .format(DateTimeFormatter.ofPattern("HH:mm"))
                        }.getOrNull()
                        if (bedtime != null || wakeTime != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                                bedtime?.let {
                                    Column {
                                        Text("Bedtime", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium)
                                    }
                                }
                                wakeTime?.let {
                                    Column {
                                        Text("Woke", style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(it, style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Heart + Vitals card ──────────────────────────────────────────────
        val hasVitals = today.restingHeartRateBpm != null || today.hrvMillis != null ||
            today.spo2Percent != null || today.breathingRatePerMin != null
        if (hasVitals) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Favorite, contentDescription = null,
                                tint = ChartColors.Heart, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Heart & Vitals", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        // 4-metric horizontal strip
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            today.restingHeartRateBpm?.let {
                                VitalChip("RHR", "$it", "bpm", ChartColors.Heart)
                            }
                            today.hrvMillis?.let {
                                VitalChip("HRV", "${it.toInt()}", "ms", ChartColors.Hrv)
                            }
                            today.spo2Percent?.let {
                                VitalChip("SpO₂", "${it.toInt()}", "%", ChartColors.Spo2)
                            }
                            today.breathingRatePerMin?.let {
                                VitalChip("Breathing", "%.0f".format(it), "rpm", ChartColors.Breathing)
                            }
                        }
                        // Mini RHR trend sparkline
                        val rhrTrend = trend.map { it.restingHeartRateBpm?.toDouble() }
                        if (rhrTrend.filterNotNull().size > 3) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Text("RHR — 14 days", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            DesktopLineChart(
                                values = rhrTrend,
                                dates = trend.map { it.date },
                                color = ChartColors.Heart,
                                target = rhrTrend.filterNotNull().average(),
                                targetLabel = "Avg",
                                unit = "bpm",
                                height = 80.dp,
                                showArea = false,
                            )
                        }
                        // Skin temp if available
                        today.skinTemperatureDeltaC?.let { delta ->
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Thermostat, contentDescription = null,
                                    tint = ChartColors.Temperature, modifier = Modifier.size(16.dp))
                                Text("Skin temperature", style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.weight(1f))
                                Text(UnitFormatter.formatTemperatureDelta(delta, units),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
            }
        }

        // ── Nutrition + Hydration card ────────────────────────────────────────
        val hasFood = (today.caloriesConsumed ?: 0.0) > 0
        val hasWater = (today.waterLiters ?: 0.0) > 0
        if (hasFood || hasWater) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Restaurant, contentDescription = null,
                                tint = Color(0xFFFFA726), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Nutrition", style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (hasFood) {
                            val calIn = today.caloriesConsumed ?: 0.0
                            val calGoal = goals.caloriesGoal.toDouble()
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(UnitFormatter.formatCalories(calIn, units),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text("/ %,d kcal".format(goals.caloriesGoal),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.align(Alignment.Bottom))
                            }
                            LinearProgressIndicator(
                                progress = { (calIn / calGoal).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
                                color = Color(0xFFFFA726),
                            )
                        }
                        if (hasWater) {
                            val water = today.waterLiters ?: 0.0
                            val waterGoal = goals.waterLitersGoal
                            if (hasFood) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Row(verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.WaterDrop, contentDescription = null,
                                        tint = Color(0xFF29B6F6), modifier = Modifier.size(16.dp))
                                    Text(UnitFormatter.formatWater(water, units),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold)
                                }
                                Text("/ %.1f L".format(waterGoal),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            LinearProgressIndicator(
                                progress = { (water / waterGoal).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small),
                                color = Color(0xFF29B6F6),
                            )
                        }
                    }
                }
            }
        }

        // ── 14-day steps trend ───────────────────────────────────────────────
        if (trend.size > 1 && trend.any { it.steps != null }) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.DirectionsWalk, contentDescription = null,
                                tint = ChartColors.Movement, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Steps — 14 days", style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold)
                        }
                        DesktopLineChart(
                            values = trend.map { it.steps?.toDouble() },
                            dates = trend.map { it.date },
                            color = ChartColors.Movement,
                            target = goals.stepGoal.toDouble(),
                            targetLabel = "Goal",
                            unit = "steps",
                            height = 120.dp,
                        )
                    }
                }
            }
        }
    }
}

/** Small vertically-stacked metric chip used inside the Heart & Vitals card. */
@Composable
private fun VitalChip(label: String, value: String, unit: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value, style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = color)
        Text(unit, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(label, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun formatDuration(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h}h ${m}m"
}

// Google Health API v4 doesn't expose a step-goal endpoint (confirmed empty
// in OpenFit desktop's own activityGoals response) - 10,000 matches the
// long-standing industry-default daily step target most trackers assume.
private const val DEFAULT_STEP_GOAL = 10_000
