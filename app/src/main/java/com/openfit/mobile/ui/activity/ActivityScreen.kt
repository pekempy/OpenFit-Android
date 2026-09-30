package com.openfit.mobile.ui.activity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.ui.common.ExerciseTypeDropdown
import com.openfit.mobile.model.ExerciseSession
import com.openfit.mobile.ui.charts.ChartColors
import com.openfit.mobile.ui.charts.DesktopColumnChart
import com.openfit.mobile.ui.charts.HourlyBarChart
import com.openfit.mobile.ui.common.EmptyStateMessage
import com.openfit.mobile.ui.common.LoadingBlock
import com.openfit.mobile.ui.common.MetricCard
import com.openfit.mobile.ui.today.TodayUiState
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityScreen(container: AppContainer, state: TodayUiState, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    val labels by container.exerciseLabelStore.labelsFlow.collectAsState(initial = emptyMap())
    val settings by container.settingsRepository.settingsFlow.collectAsState(initial = null)
    val goals = settings?.goals ?: com.openfit.mobile.data.settings.UserHealthGoals()
    val units = settings?.units ?: com.openfit.mobile.data.settings.AppUnitSettings()
    var relabelTarget by remember { mutableStateOf<ExerciseSession?>(null) }

        when (state) {
            is TodayUiState.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is TodayUiState.NotConnected -> EmptyStateMessage(
                "Connect Google Health in Settings to see your activity.",
                Modifier.fillMaxSize(),
            )
            is TodayUiState.Error -> EmptyStateMessage(state.message, Modifier.fillMaxSize())
            is TodayUiState.Success -> {
                val bundle = state.bundle
                val today = bundle.today
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item { Text("Today's movement", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                label = "Steps", icon = Icons.Filled.DirectionsWalk,
                                value = today.steps?.let { "%,d".format(it) } ?: "—",
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Movement,
                            )
                            MetricCard(
                                label = "Distance", icon = Icons.Filled.Terrain,
                                value = com.openfit.mobile.ui.common.UnitFormatter.formatDistance(today.distanceMeters, units),
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Movement,
                            )
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                label = "Active minutes", icon = Icons.Filled.DirectionsRun,
                                value = today.activeMinutes?.toString() ?: "—",
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Movement,
                            )
                            MetricCard(
                                label = "Zone minutes", icon = Icons.Filled.LocalFireDepartment,
                                value = today.zoneMinutes?.toString() ?: "—",
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Movement,
                            )
                        }
                    }
                    if (today.elevationGainedMeters != null && today.elevationGainedMeters > 0) {
                        item {
                            MetricCard(
                                label = "Elevation gained", icon = Icons.Filled.Terrain,
                                value = "${today.elevationGainedMeters.toInt()} m",
                                modifier = Modifier.fillMaxWidth(),
                                accentColor = ChartColors.Movement,
                            )
                        }
                    }
                    item { Text("Steps per hour (today)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    item {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp)) {
                                HourlyBarChart(values = today.stepsHourly, color = ChartColors.Movement)
                            }
                        }
                    }

                    item { Text("Exercise sessions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                    if (bundle.exercises.isEmpty()) {
                        item { EmptyStateMessage("No exercise sessions logged today.") }
                    } else {
                        items(bundle.exercises, key = { it.id }) { session ->
                            ExerciseRow(session = session, customLabel = labels[session.id], onRelabel = { relabelTarget = session })
                        }
                    }

                    if (bundle.trend.size > 1) {
                        item { Text("Movement trends (14 days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                        item {
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    DesktopColumnChart(
                                        values = bundle.trend.map { it.steps?.toDouble() },
                                        dates = bundle.trend.map { it.date },
                                        color = ChartColors.Movement,
                                        target = goals.stepGoal.toDouble(),
                                        targetLabel = "%,d goal".format(goals.stepGoal),
                                        unit = "steps",
                                        height = 140.dp,
                                    )
                                }
                            }
                        }

                        val elevationList = bundle.trend.map { it.elevationGainedMeters }
                        if (elevationList.filterNotNull().size > 1) {
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.Terrain, contentDescription = null, tint = ChartColors.Movement, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Elevation gained trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                        }
                                        val elevationAvg = elevationList.filterNotNull().average()
                                        DesktopColumnChart(
                                            values = elevationList,
                                            dates = bundle.trend.map { it.date },
                                            color = ChartColors.Movement,
                                            target = elevationAvg,
                                            targetLabel = "Average",
                                            unit = "m",
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

    relabelTarget?.let { session ->
        RelabelDialog(
            session = session,
            currentLabel = labels[session.id],
            onDismiss = { relabelTarget = null },
            onSave = { newLabel ->
                scope.launch { container.exerciseLabelStore.setLabel(session.id, newLabel) }
                relabelTarget = null
            },
            onClear = {
                scope.launch { container.exerciseLabelStore.clearLabel(session.id) }
                relabelTarget = null
            },
        )
    }
}

@Composable
private fun ExerciseRow(session: ExerciseSession, customLabel: String?, onRelabel: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = customLabel ?: session.originalType.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (customLabel != null) {
                    Text("Originally: ${session.originalType}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val details = buildList {
                    add("${session.durationMinutes} min")
                    session.caloriesBurned?.let { add("${it.toInt()} kcal") }
                    session.averageHeartRateBpm?.let { add("$it bpm avg") }
                    session.elevationGainedMeters?.takeIf { it > 0 }?.let { add("${it.toInt()} m elevation") }
                    session.averagePowerWatts?.let { add("${it.toInt()} W avg power") }
                    session.averageSpeedMetersPerSecond?.let { add("%.1f m/s avg".format(it)) }
                }.joinToString(" · ")
                Text(details, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onRelabel) {
                Icon(Icons.Filled.Edit, contentDescription = "Rename this activity")
            }
        }
    }
}

@Composable
private fun RelabelDialog(
    session: ExerciseSession,
    currentLabel: String?,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var text by remember { mutableStateOf(currentLabel ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename \"${session.originalType}\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This only changes how it's displayed in OpenFit — the Health Connect record is untouched.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ExerciseTypeDropdown(
                    selected = text,
                    onSelect = { text = it },
                    allowFreeform = true,
                    label = "Custom name",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text.trim()) }, enabled = text.isNotBlank()) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (currentLabel != null) TextButton(onClick = onClear) { Text("Clear") }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
