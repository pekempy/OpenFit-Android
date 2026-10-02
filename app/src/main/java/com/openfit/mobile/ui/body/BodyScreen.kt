package com.openfit.mobile.ui.body

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.openfit.mobile.data.settings.UnitSystem
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.ui.charts.BulletChart
import com.openfit.mobile.ui.charts.ChartColors
import com.openfit.mobile.ui.charts.DesktopLineChart
import com.openfit.mobile.ui.common.EmptyStateMessage
import com.openfit.mobile.ui.common.LoadingBlock
import com.openfit.mobile.ui.common.MetricCard
import com.openfit.mobile.ui.common.getMetricExplanation
import com.openfit.mobile.ui.today.TodayUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun BodyScreen(container: AppContainer, state: TodayUiState, onRefresh: () -> Unit) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
        when (state) {
            is TodayUiState.Loading -> LoadingBlock(Modifier.fillMaxSize())
            is TodayUiState.NotConnected -> EmptyStateMessage(
                "Connect Google Health in Settings to see your body metrics.",
                Modifier.fillMaxSize(),
            )
            is TodayUiState.Error -> EmptyStateMessage(state.message, Modifier.fillMaxSize())
            is TodayUiState.Success -> {
                val settings by container.settingsRepository.settingsFlow.collectAsState(initial = null)
                val goals = settings?.goals ?: com.openfit.mobile.data.settings.UserHealthGoals()
                val units = settings?.units ?: com.openfit.mobile.data.settings.AppUnitSettings()
                val inspector = com.openfit.mobile.ui.common.LocalMetricInspector.current
                val today = state.bundle.today
                val trend = state.bundle.trend
                val latestWeight = today.weightKg ?: trend.lastOrNull { it.weightKg != null }?.weightKg
                val latestFat = today.bodyFatPercent ?: trend.lastOrNull { it.bodyFatPercent != null }?.bodyFatPercent
                val scope = rememberCoroutineScope()
                var showHeightDialog by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Today's measurements", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        MetricCard(
                            label = "Weight", icon = Icons.Filled.MonitorWeight,
                            value = com.openfit.mobile.ui.common.UnitFormatter.formatWeight(latestWeight, units),
                            modifier = Modifier.weight(1f),
                            accentColor = ChartColors.Body,
                        )
                        MetricCard(
                            label = "Body fat", icon = Icons.Filled.PieChart,
                            value = latestFat?.let { "%.1f%%".format(it) } ?: "—",
                            modifier = Modifier.weight(1f),
                            accentColor = ChartColors.Body,
                        )
                    }

                    val latestHeight = today.heightMeters ?: trend.lastOrNull { it.heightMeters != null }?.heightMeters
                    val bmi = if (latestHeight != null && latestHeight > 0 && latestWeight != null) {
                        latestWeight / (latestHeight * latestHeight)
                    } else null
                    val bmiCategory = bmi?.let {
                        when {
                            it < 18.5 -> "Underweight"
                            it < 25.0 -> "Normal"
                            it < 30.0 -> "Overweight"
                            else -> "Obese"
                        }
                    }

                    val hasBodyComposition = latestHeight != null || today.bodyWaterMassKg != null || today.basalMetabolicRateKcal != null
                    if (hasBodyComposition || bmi != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                label = "Height", icon = Icons.Filled.MonitorWeight,
                                value = latestHeight?.let { "%.0f cm".format(it * 100) } ?: "Tap to set",
                                subLabel = if (latestHeight == null) null else null,
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Body,
                                onClick = { showHeightDialog = true },
                            )
                            MetricCard(
                                label = "BMI", icon = Icons.Filled.PieChart,
                                value = bmi?.let { "%.1f".format(it) } ?: "—",
                                subLabel = bmiCategory,
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Body,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MetricCard(
                                label = "BMR", icon = Icons.Filled.PieChart,
                                value = today.basalMetabolicRateKcal?.let { "%.0f".format(it) } ?: "—",
                                subLabel = "kcal/day",
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Body,
                            )
                            MetricCard(
                                label = "Body water", icon = Icons.Filled.WaterDrop,
                                value = com.openfit.mobile.ui.common.UnitFormatter.formatWeight(today.bodyWaterMassKg, units),
                                modifier = Modifier.weight(1f),
                                accentColor = ChartColors.Hrv,
                            )
                        }
                    }

                    val waterDetail = remember(today.waterLiters) {
                        com.openfit.mobile.ui.common.MetricKnowledge.getDetail(
                            "Hydration",
                            currentValue = com.openfit.mobile.ui.common.UnitFormatter.formatWater(today.waterLiters ?: 0.0, units),
                            fallbackIcon = Icons.Filled.WaterDrop,
                        )
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(CardDefaults.shape)
                            .combinedClickable(
                                onClick = { inspector.showToast(waterDetail) },
                                onLongClick = {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    inspector.showSheet(waterDetail)
                                },
                            ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = ChartColors.Hrv, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = com.openfit.mobile.ui.common.UnitFormatter.formatWater(today.waterLiters ?: 0.0, units),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                            BulletChart(
                                value = today.waterLiters ?: 0.0,
                                animationKey = state.bundle.fetchedAtEpochMillis,
                                target = goals.waterLitersGoal,
                                max = (goals.waterLitersGoal * 1.25).coerceAtLeast(3.0),
                                label = "Daily hydration target (%.2f L)".format(goals.waterLitersGoal),
                                valueLabel = "${today.waterLiters?.let { "%.1f".format(it) } ?: "0.0"} / %.2f L".format(goals.waterLitersGoal),
                                color = ChartColors.Hrv,
                            )
                        }
                    }

                    // 14-day Weight Trend Chart
                    val weightList = trend.map { it.weightKg }
                    if (weightList.filterNotNull().size > 1) {
                        Text("Body trends (14 days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.MonitorWeight, contentDescription = null, tint = ChartColors.Body, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Weight trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                                val weightAvg = weightList.filterNotNull().average()
                                DesktopLineChart(
                                    values = weightList,
                                    animationKey = state.bundle.fetchedAtEpochMillis,
                                    dates = trend.map { it.date },
                                    color = ChartColors.Body,
                                    target = weightAvg,
                                    targetLabel = "Average",
                                    unit = "kg",
                                    height = 130.dp,
                                )
                            }
                        }
                    }

                    val waterMassList = trend.map { it.bodyWaterMassKg }
                    if (waterMassList.filterNotNull().size > 1) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.WaterDrop, contentDescription = null, tint = ChartColors.Hrv, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Body water trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                                val waterMassAvg = waterMassList.filterNotNull().average()
                                DesktopLineChart(
                                    values = waterMassList,
                                    animationKey = state.bundle.fetchedAtEpochMillis,
                                    dates = trend.map { it.date },
                                    color = ChartColors.Hrv,
                                    target = waterMassAvg,
                                    targetLabel = "Average",
                                    unit = "kg",
                                    height = 130.dp,
                                )
                            }
                        }
                    }

                    val bmrList = trend.map { it.basalMetabolicRateKcal }
                    if (bmrList.filterNotNull().size > 1) {
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PieChart, contentDescription = null, tint = ChartColors.Body, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("BMR trend", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                }
                                val bmrAvg = bmrList.filterNotNull().average()
                                DesktopLineChart(
                                    values = bmrList,
                                    animationKey = state.bundle.fetchedAtEpochMillis,
                                    dates = trend.map { it.date },
                                    color = ChartColors.Body,
                                    target = bmrAvg,
                                    targetLabel = "Average",
                                    unit = "kcal",
                                    height = 130.dp,
                                )
                            }
                        }
                    }

                    val reproEvents = state.bundle.reproductiveHealthEvents
                    val showReproductiveHealth = settings?.display?.showReproductiveHealth ?: true
                    if (reproEvents.isNotEmpty() && showReproductiveHealth) {
                        Text("Reproductive health (14 days)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                reproEvents.take(20).forEach { event ->
                                    val typeLabel = when (event.type) {
                                        "menstruation_flow" -> "Menstruation"
                                        "menstruation_period" -> "Period"
                                        "cervical_mucus" -> "Cervical mucus"
                                        "ovulation_test" -> "Ovulation test"
                                        "sexual_activity" -> "Sexual activity"
                                        "intermenstrual_bleeding" -> "Intermenstrual bleeding"
                                        else -> event.type
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column {
                                            Text(typeLabel, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                            Text(event.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Text(event.date, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    if (latestWeight == null && latestFat == null) {
                        EmptyStateMessage("No weight or body-fat entries found in the last 14 days.")
                    }
                }

                if (showHeightDialog) {
                    HeightInputDialog(
                        isImperial = units.system == UnitSystem.IMPERIAL,
                        onDismiss = { showHeightDialog = false },
                        onSave = { meters ->
                            showHeightDialog = false
                            scope.launch {
                                val ok = container.healthConnectRepository.writeHeight(meters)
                                val msg = if (ok) "Height saved to Health Connect" else "Failed to save height"
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                onRefresh()
                            }
                        },
                    )
                }
            }
        }
}

@Composable
private fun HeightInputDialog(
    isImperial: Boolean,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    var cmText by remember { mutableStateOf("") }
    var feetText by remember { mutableStateOf("") }
    var inchesText by remember { mutableStateOf("") }

    val metersValue: Double? = if (isImperial) {
        val ft = feetText.toDoubleOrNull() ?: 0.0
        val inch = inchesText.toDoubleOrNull() ?: 0.0
        val totalInches = ft * 12.0 + inch
        if (totalInches > 0) totalInches * 0.0254 else null
    } else {
        cmText.toDoubleOrNull()?.let { if (it > 0) it / 100.0 else null }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set height") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Saved to Health Connect and used for BMI calculation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isImperial) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = feetText,
                            onValueChange = { feetText = it.filter { c -> c.isDigit() }.take(1) },
                            label = { Text("Feet") },
                            placeholder = { Text("5") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = inchesText,
                            onValueChange = { inchesText = it.filter { c -> c.isDigit() || c == '.' }.take(4) },
                            label = { Text("Inches") },
                            placeholder = { Text("10") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                } else {
                    OutlinedTextField(
                        value = cmText,
                        onValueChange = { cmText = it.filter { c -> c.isDigit() || c == '.' }.take(6) },
                        label = { Text("Height (cm)") },
                        placeholder = { Text("175") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = { metersValue?.let(onSave) }, enabled = metersValue != null) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
