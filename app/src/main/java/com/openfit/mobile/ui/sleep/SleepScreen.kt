package com.openfit.mobile.ui.sleep

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.model.SleepSession
import com.openfit.mobile.ui.charts.BulletChart
import com.openfit.mobile.ui.charts.ChartColors
import com.openfit.mobile.ui.charts.DesktopColumnChart
import com.openfit.mobile.ui.charts.RingProgress
import com.openfit.mobile.ui.charts.SegmentedBar
import com.openfit.mobile.ui.charts.SleepHypnogramChart
import com.openfit.mobile.ui.common.EmptyStateMessage
import com.openfit.mobile.ui.common.LoadingBlock
import com.openfit.mobile.ui.common.MetricCard
import com.openfit.mobile.ui.common.getMetricExplanation
import com.openfit.mobile.ui.today.TodayUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SleepScreen(container: AppContainer, state: TodayUiState, onRefresh: () -> Unit, revealKey: Long = 0L) {
        when (state) {
            is TodayUiState.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is TodayUiState.NotConnected -> EmptyStateMessage(
                "Connect Google Health in Settings to see your sleep data.",
                Modifier.fillMaxSize(),
            )
            is TodayUiState.Error -> EmptyStateMessage(state.message, Modifier.fillMaxSize())
            is TodayUiState.Success -> {
                val settings by container.settingsRepository.settingsFlow.collectAsState(initial = null)
                val goals = settings?.goals ?: com.openfit.mobile.data.settings.UserHealthGoals()
                val animKey = if (revealKey > 0L) maxOf(state.bundle.fetchedAtEpochMillis, revealKey) else 0L
                val sleep = state.bundle.today.sleep
                val naps = state.bundle.today.naps
                val totalSleepMinutes = state.bundle.today.totalSleepMinutes
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Total sleep header card
                    if (totalSleepMinutes != null) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = formatDuration(totalSleepMinutes),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = ChartColors.Sleep,
                                )
                                // Show sub-labels if both overnight and naps exist
                                if (sleep != null && naps.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "Overnight: ${formatDuration(sleep.totalMinutes)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            val napsTotalMinutes = naps.sumOf { it.totalMinutes }
                                            Text(
                                                text = "Naps: ${formatDuration(napsTotalMinutes)}",
                                                style = MaterialTheme.typography.bodyMedium,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Overnight sleep section
                    if (sleep != null) {
                        Text("Last night's sleep", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        SleepSummary(sleep, goals, animKey)

                        if (sleep.stages.isNotEmpty()) {
                            Text("Stages", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            StageBreakdown(sleep, animKey)
                        }

                        if (sleep.segments.isNotEmpty()) {
                            Text("Night timeline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    SleepHypnogramChart(
                                        animationKey = animKey,
                                        segments = sleep.segments,
                                        height = 150.dp
                                    )
                                }
                            }
                        }
                    }

                    // Naps section
                    if (naps.isNotEmpty()) {
                        Text("Naps", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        naps.forEach { nap ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    // Nap start time
                                    val startTime = try {
                                        val instant = Instant.parse(nap.startTimeIso)
                                        val formatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).withZone(ZoneId.systemDefault())
                                        formatter.format(instant)
                                    } catch (e: Exception) {
                                        "N/A"
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Text(
                                            text = startTime,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                        Text(
                                            text = formatDuration(nap.totalMinutes),
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                    }

                                    // Nap stage breakdown
                                    if (nap.stages.isNotEmpty()) {
                                        StageBreakdown(nap, animKey)
                                    }
                                }
                            }
                        }
                    }

                    // No sleep empty state
                    if (sleep == null && naps.isEmpty()) {
                        EmptyStateMessage("No sleep recorded.")
                    }

                    // Trend chart
                    val trendSleep = state.bundle.trend.filter { it.totalSleepMinutes != null }
                    if (trendSleep.size > 1) {
                        Text("Sleep duration (14 days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                val sleepTargetMinutes = goals.sleepMinutesGoal.toDouble()
                                val sleepTargetHours = goals.sleepMinutesGoal / 60
                                val sleepTargetRemMins = goals.sleepMinutesGoal % 60
                                val goalStr = "${sleepTargetHours}h${if (sleepTargetRemMins > 0) " ${sleepTargetRemMins}m" else ""}"
                                DesktopColumnChart(
                                    values = state.bundle.trend.map { it.totalSleepMinutes?.toDouble() },
                                    animationKey = animKey,
                                    dates = state.bundle.trend.map { it.date },
                                    color = ChartColors.Sleep,
                                    target = sleepTargetMinutes,
                                    targetLabel = "$goalStr goal",
                                    height = 140.dp,
                                    formatter = { formatDuration(it.toInt()) },
                                )
                            }
                        }
                    }
                }
            }
        }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SleepSummary(sleep: SleepSession, goals: com.openfit.mobile.data.settings.UserHealthGoals, animationKey: Long = 0L) {
    val haptics = LocalHapticFeedback.current
    val inspector = com.openfit.mobile.ui.common.LocalMetricInspector.current
    val sleepDetail = remember(sleep.totalMinutes) {
        com.openfit.mobile.ui.common.MetricKnowledge.getDetail(
            "Sleep",
            currentValue = formatDuration(sleep.totalMinutes),
            fallbackIcon = Icons.Filled.Bedtime,
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardDefaults.shape)
            .combinedClickable(
                onClick = { inspector.showToast(sleepDetail) },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    inspector.showSheet(sleepDetail)
                },
            ),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Sleep time",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = formatDuration(sleep.totalMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = ChartColors.Sleep,
                    )
                }
                if (sleep.efficiencyPercent != null) {
                    RingProgress(
                        progress = (sleep.efficiencyPercent ?: 0) / 100f,
                        animationKey = animationKey,
                        color = ChartColors.Sleep,
                        size = 72.dp,
                        centerText = "${sleep.efficiencyPercent ?: 0}%",
                        centerSubText = "efficiency",
                    )
                }
            }

            val targetMin = goals.sleepMinutesGoal.toDouble()
            val targetH = goals.sleepMinutesGoal / 60
            val targetM = goals.sleepMinutesGoal % 60
            val goalFormatted = "${targetH}h ${if (targetM > 0) "${targetM}m" else "00m"}"

            BulletChart(
                value = sleep.totalMinutes.toDouble(),
                animationKey = animationKey,
                target = targetMin,
                max = (targetMin * 1.2).coerceAtLeast(540.0),
                label = "Duration compared with $goalFormatted goal",
                valueLabel = "${formatDuration(sleep.totalMinutes)} / $goalFormatted",
                color = ChartColors.Sleep,
            )
        }
    }
}

@Composable
private fun StageBreakdown(sleep: SleepSession, animationKey: Long = 0L) {
    val stageOrder = listOf("deep", "rem", "light", "wake")
    val byStage = sleep.stages.associateBy { it.stage }
    val totalMinutes = sleep.stages.sumOf { it.minutes }.coerceAtLeast(1)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (sleep.stages.isEmpty()) {
                Text("No stage breakdown available for this session.", style = MaterialTheme.typography.bodyMedium)
            } else {
                val segments = stageOrder.mapNotNull { stage ->
                    byStage[stage]?.minutes?.takeIf { it > 0 }?.let { (it.toFloat()) to ChartColors.sleepStageColor(stage) }
                }
                SegmentedBar(
                    animationKey = animationKey,
                    segments = segments,
                    modifier = Modifier.fillMaxWidth(),
                    height = 12.dp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    stageOrder.forEach { stage ->
                        val minutes = byStage[stage]?.minutes ?: 0
                        val percent = (minutes * 100) / totalMinutes
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(ChartColors.sleepStageColor(stage))
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    text = stage.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                text = "${percent}% · ${minutes}m",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(totalMinutes: Int): String {
    val h = totalMinutes / 60
    val m = totalMinutes % 60
    return "${h}h ${m}m"
}
