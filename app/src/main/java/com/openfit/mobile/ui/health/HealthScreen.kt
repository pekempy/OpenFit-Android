package com.openfit.mobile.ui.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Bloodtype
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.ui.charts.ChartColors
import com.openfit.mobile.ui.charts.DesktopLineChart
import com.openfit.mobile.ui.common.EmptyStateMessage
import com.openfit.mobile.ui.common.LoadingBlock
import com.openfit.mobile.ui.common.MetricCard
import com.openfit.mobile.ui.today.TodayUiState

/** Vitals dashboard - resting heart rate, HRV, SpO2, breathing rate, skin
 * temperature, and cardio fitness (VO2max), mirroring OpenFit desktop's
 * "Nightly signals" plus cardio card set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthScreen(container: AppContainer, state: TodayUiState, onRefresh: () -> Unit) {
        when (state) {
            is TodayUiState.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is TodayUiState.NotConnected -> EmptyStateMessage(
                "Connect Google Health in Settings to see your vitals.",
                Modifier.fillMaxSize(),
            )
            is TodayUiState.Error -> EmptyStateMessage(state.message, Modifier.fillMaxSize())
            is TodayUiState.Success -> {
                val bundle = state.bundle
                val today = bundle.today
                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Today's vitals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(
                            label = "Resting heart rate", icon = Icons.Filled.Favorite,
                            value = today.restingHeartRateBpm?.let { "$it bpm" } ?: "—",
                            modifier = Modifier.weight(1f),
                            accentColor = ChartColors.Heart,
                        )
                        MetricCard(
                            label = "HRV", icon = Icons.Filled.MonitorHeart,
                            value = today.hrvMillis?.let { "${it.toInt()} ms" } ?: "—",
                            modifier = Modifier.weight(1f),
                            accentColor = ChartColors.Hrv,
                        )
                    }

                    if (today.heartRateAvgBpm != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                label = "Average heart rate", icon = Icons.Filled.MonitorHeart,
                                value = "${today.heartRateAvgBpm} bpm",
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Heart,
                            )
                            MetricCard(
                                label = "Min / max", icon = Icons.Filled.Favorite,
                                value = "${today.heartRateMinBpm ?: "—"} / ${today.heartRateMaxBpm ?: "—"}",
                                subLabel = "bpm",
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Heart,
                            )
                        }
                    }

                    if (today.heartRateZoneMinutes.isNotEmpty()) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("Heart rate zones", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                val zoneOrder = listOf(
                                    "Zone 1 (Very Light)" to ChartColors.Hrv,
                                    "Zone 2 (Light)" to ChartColors.Spo2,
                                    "Zone 3 (Moderate)" to ChartColors.Breathing,
                                    "Zone 4 (Hard)" to ChartColors.Temperature,
                                    "Zone 5 (Maximum)" to ChartColors.Heart,
                                )
                                val segments = zoneOrder.mapNotNull { (zone, color) ->
                                    val minutes = today.heartRateZoneMinutes[zone] ?: return@mapNotNull null
                                    minutes.toFloat() to color
                                }
                                com.openfit.mobile.ui.charts.SegmentedBar(segments = segments, modifier = Modifier.fillMaxWidth())
                                zoneOrder.forEach { (zone, color) ->
                                    val minutes = today.heartRateZoneMinutes[zone] ?: 0
                                    if (minutes > 0) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(modifier = Modifier.size(10.dp).background(color, shape = androidx.compose.foundation.shape.CircleShape))
                                            Spacer(Modifier.width(8.dp))
                                            Text("$zone: ${minutes}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (today.heartRateAvgBpm != null) {
                        Text(
                            "Set your max heart rate in Settings \u2192 Health Goals to see a zone breakdown.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(
                            label = "Breathing rate", icon = Icons.Filled.Air,
                            value = today.breathingRatePerMin?.let { "%.1f rpm".format(it) } ?: "—",
                            modifier = Modifier.weight(1f),
                            accentColor = ChartColors.Breathing,
                        )
                        MetricCard(
                            label = "Blood oxygen", icon = Icons.Filled.Bloodtype,
                            value = today.spo2Percent?.let { "%.0f%%".format(it) } ?: "—",
                            modifier = Modifier.weight(1f),
                            accentColor = ChartColors.Spo2,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(
                            label = "Skin temperature", icon = Icons.Filled.Thermostat,
                            value = today.skinTemperatureDeltaC?.let { "%+.1f°C".format(it) } ?: "—",
                            subLabel = "vs. baseline",
                            modifier = Modifier.weight(1f),
                            accentColor = ChartColors.Temperature,
                        )
                        MetricCard(
                            label = "Cardio fitness", icon = Icons.Filled.DirectionsRun,
                            value = today.vo2Max?.let { "%.1f".format(it) } ?: "—",
                            subLabel = "VO2 max",
                            modifier = Modifier.weight(1f),
                            accentColor = ChartColors.Cardio,
                        )
                    }

                    // 14-day Physiological Trends
                    val rhrList = bundle.trend.map { it.restingHeartRateBpm?.toDouble() }
                    val hrvList = bundle.trend.map { it.hrvMillis }
                    val avgHrList = bundle.trend.map { it.heartRateAvgBpm?.toDouble() }
                    val hasTrends = listOf(rhrList, hrvList, avgHrList).any { it.filterNotNull().size > 1 }

                    if (hasTrends) {
                        Text("Physiological trends (14 days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

                        if (rhrList.filterNotNull().size > 1) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Favorite, contentDescription = null, tint = ChartColors.Heart, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Resting Heart Rate trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    val baseline = rhrList.filterNotNull().average()
                                    DesktopLineChart(
                                        values = rhrList,
                                        dates = bundle.trend.map { it.date },
                                        color = ChartColors.Heart,
                                        target = baseline,
                                        targetLabel = "Baseline",
                                        unit = "bpm",
                                        height = 130.dp,
                                    )
                                }
                            }
                        }

                        if (hrvList.filterNotNull().size > 1) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = ChartColors.Hrv, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Heart Rate Variability trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    val hrvAvg = hrvList.filterNotNull().average()
                                    DesktopLineChart(
                                        values = hrvList,
                                        dates = bundle.trend.map { it.date },
                                        color = ChartColors.Hrv,
                                        target = hrvAvg,
                                        targetLabel = "Baseline",
                                        unit = "ms",
                                        height = 130.dp,
                                    )
                                }
                            }
                        }

                        if (avgHrList.filterNotNull().size > 1) {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.MonitorHeart, contentDescription = null, tint = ChartColors.Heart, modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text("Average Heart Rate trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                    }
                                    val avgHrBaseline = avgHrList.filterNotNull().average()
                                    DesktopLineChart(
                                        values = avgHrList,
                                        dates = bundle.trend.map { it.date },
                                        color = ChartColors.Heart,
                                        target = avgHrBaseline,
                                        targetLabel = "Baseline",
                                        unit = "bpm",
                                        height = 130.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
}
