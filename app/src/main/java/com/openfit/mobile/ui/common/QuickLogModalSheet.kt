package com.openfit.mobile.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.exercise.HC_EXERCISE_MET
import com.openfit.mobile.data.exercise.exerciseTypeIdForLabel
import com.openfit.mobile.data.settings.AppUnitSettings
import com.openfit.mobile.data.settings.UserHealthGoals
import com.openfit.mobile.data.settings.WeightUnit
import com.openfit.mobile.ui.charts.ChartColors
import kotlinx.coroutines.launch
import java.time.Instant
import kotlin.math.roundToInt

enum class QuickLogCategory(val title: String, val icon: ImageVector, val color: Color) {
    WATER("Water", Icons.Filled.WaterDrop, Color(0xFF29B6F6)),
    FOOD("Food", Icons.Filled.Restaurant, Color(0xFFFFA726)),
    WORKOUT("Workout", Icons.Filled.DirectionsRun, Color(0xFF66BB6A)),
    WEIGHT("Weight", Icons.Filled.MonitorWeight, Color(0xFFAB47BC)),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogModalSheet(
    container: AppContainer,
    initialCategory: QuickLogCategory = QuickLogCategory.WATER,
    goals: UserHealthGoals,
    units: AppUnitSettings,
    onDismiss: () -> Unit,
    onLogged: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header with Category Selector Tabs
            Text(
                "Quick Log",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QuickLogCategory.entries.forEach { cat ->
                    val isSelected = cat == selectedCategory
                    FilterChip(
                        selected = isSelected,
                        onClick = { selectedCategory = cat },
                        label = { Text(cat.title, maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                cat.icon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else cat.color,
                                modifier = Modifier.size(18.dp),
                            )
                        },
                    )
                }
            }

            HorizontalDivider()

            when (selectedCategory) {
                QuickLogCategory.WATER -> LogWaterForm(
                    container = container,
                    goals = goals,
                    units = units,
                    onSuccess = { msg ->
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onLogged(msg)
                        }
                    },
                )
                QuickLogCategory.FOOD -> LogFoodForm(
                    container = container,
                    goals = goals,
                    onSuccess = { msg ->
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onLogged(msg)
                        }
                    },
                )
                QuickLogCategory.WORKOUT -> LogWorkoutForm(
                    container = container,
                    goals = goals,
                    units = units,
                    onSuccess = { msg ->
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onLogged(msg)
                        }
                    },
                )
                QuickLogCategory.WEIGHT -> LogWeightForm(
                    container = container,
                    goals = goals,
                    units = units,
                    onSuccess = { msg ->
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onLogged(msg)
                        }
                    },
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun LogWaterForm(
    container: AppContainer,
    goals: UserHealthGoals,
    units: AppUnitSettings,
    onSuccess: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var amountMl by remember { mutableStateOf(250) }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Log Water Intake", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        // Quick add presets
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(250 to "+250 ml\n(Glass)", 500 to "+500 ml\n(Bottle)", 750 to "+750 ml\n(Large)", 1000 to "+1.0 L\n(Flask)").forEach { (ml, label) ->
                OutlinedButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        amountMl = ml
                    },
                    modifier = Modifier.weight(1f),
                    colors = if (amountMl == ml) ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer) else ButtonDefaults.outlinedButtonColors(),
                ) {
                    Text(label, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                }
            }
        }

        // Stepper for custom amount
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Amount to log", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        amountMl = (amountMl - 50).coerceAtLeast(50)
                    },
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }

                Text(
                    "%,d ml (%.2f L)".format(amountMl, amountMl / 1000.0),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        amountMl = (amountMl + 50).coerceAtMost(3000)
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }
        }

        Button(
            onClick = {
                val liters = amountMl / 1000.0
                scope.launch {
                    container.manualLogStore.logWater(liters)
                    container.healthConnectRepository.writeWater(liters)
                    onSuccess("✓ Logged %,d ml (%.2f L) water".format(amountMl, liters))
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.WaterDrop, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log %,d ml Water".format(amountMl))
        }
    }
}

@Composable
private fun LogFoodForm(
    container: AppContainer,
    goals: UserHealthGoals,
    onSuccess: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var caloriesText by remember { mutableStateOf("400") }
    var mealType by remember { mutableStateOf("Lunch") }
    var foodName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Log Calorie / Food Intake", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { type ->
                FilterChip(
                    selected = mealType == type,
                    onClick = { mealType = type },
                    label = { Text(type) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        OutlinedTextField(
            value = caloriesText,
            onValueChange = { caloriesText = it.filter { ch -> ch.isDigit() }.take(5) },
            label = { Text("Calories (kcal)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = ChartColors.Calories) },
        )

        OutlinedTextField(
            value = foodName,
            onValueChange = { foodName = it },
            label = { Text("Food / Description (optional)") },
            placeholder = { Text("e.g. Oatmeal with fruit, Salad, Chicken bowl") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.Fastfood, contentDescription = null) },
        )

        val cal = caloriesText.toDoubleOrNull() ?: 0.0
        Button(
            onClick = {
                if (cal > 0) {
                    scope.launch {
                        container.manualLogStore.logNutrition(cal, mealType, foodName)
                        container.healthConnectRepository.writeNutrition(cal, mealType, foodName)
                        onSuccess("✓ Logged %,d kcal for $mealType".format(cal.roundToInt()))
                    }
                }
            },
            enabled = cal > 0,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log %,d kcal $mealType".format(cal.roundToInt()))
        }
    }
}

@Composable
private fun LogWorkoutForm(
    container: AppContainer,
    goals: UserHealthGoals,
    units: AppUnitSettings,
    onSuccess: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    var exerciseType by remember { mutableStateOf("Running") }
    var durationMinutes by remember { mutableStateOf(30) }
    var distanceKmText by remember { mutableStateOf("") }

    val estimatedCalories = remember(exerciseType, durationMinutes) {
        val typeId = exerciseTypeIdForLabel(exerciseType)
        val met = HC_EXERCISE_MET.getValue(typeId)
        (met * 70.0 * (durationMinutes / 60.0)).roundToInt()
    }
    var customCaloriesText by remember(estimatedCalories) { mutableStateOf(estimatedCalories.toString()) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Log Workout / Exercise", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        ExerciseTypeDropdown(
            selected = exerciseType,
            onSelect = { exerciseType = it },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Duration", style = MaterialTheme.typography.bodyLarge)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        durationMinutes = (durationMinutes - 5).coerceAtLeast(5)
                    },
                ) {
                    Icon(Icons.Filled.Remove, contentDescription = "Decrease")
                }
                Text(
                    "$durationMinutes min",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                FilledTonalIconButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        durationMinutes = (durationMinutes + 5).coerceAtMost(360)
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Increase")
                }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(15, 30, 45, 60).forEach { mins ->
                OutlinedButton(
                    onClick = { durationMinutes = mins },
                    modifier = Modifier.weight(1f),
                    colors = if (durationMinutes == mins)
                        ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    else ButtonDefaults.outlinedButtonColors(),
                ) { Text("${mins}m") }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = customCaloriesText,
                onValueChange = { customCaloriesText = it.filter { ch -> ch.isDigit() }.take(5) },
                label = { Text("Calories (kcal)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            OutlinedTextField(
                value = distanceKmText,
                onValueChange = { distanceKmText = it.filter { ch -> ch.isDigit() || ch == '.' }.take(6) },
                label = { Text("Distance (km, opt)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
        }

        val cal = customCaloriesText.toDoubleOrNull() ?: estimatedCalories.toDouble()
        val distMeters = distanceKmText.toDoubleOrNull()?.let { it * 1000.0 }

        Button(
            onClick = {
                scope.launch {
                    container.manualLogStore.logExercise(
                        exerciseType = exerciseType,
                        durationMinutes = durationMinutes,
                        caloriesBurned = cal,
                        distanceMeters = distMeters,
                    )
                    container.healthConnectRepository.writeExercise(
                        type = exerciseType,
                        durationMinutes = durationMinutes,
                        caloriesBurned = cal,
                        distanceMeters = distMeters,
                    )
                    onSuccess("✓ Logged $exerciseType (${durationMinutes}m, ${cal.toInt()} kcal)")
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.FitnessCenter, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log $exerciseType")
        }
    }
}

@Composable
private fun LogWeightForm(
    container: AppContainer,
    goals: UserHealthGoals,
    units: AppUnitSettings,
    onSuccess: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    var selectedUnit by remember(units.weightUnit) { mutableStateOf(units.weightUnit) }

    // Text field state - initialise according to current unit
    var weightKgText by remember { mutableStateOf("72.0") }
    var weightLbsText by remember { mutableStateOf("158.7") }
    var stoneText by remember { mutableStateOf("11") }
    var stoneLbsText by remember { mutableStateOf("5") }

    var bodyFatText by remember { mutableStateOf("") }

    // Compute effective weight in kg from the currently selected unit's inputs
    val effectiveKg: Double? = when (selectedUnit) {
        WeightUnit.KG -> weightKgText.toDoubleOrNull()
        WeightUnit.LBS -> weightLbsText.toDoubleOrNull()?.let { it / 2.20462262 }
        WeightUnit.STONE -> {
            val st = stoneText.toDoubleOrNull() ?: 0.0
            val lbs = stoneLbsText.toDoubleOrNull() ?: 0.0
            if (st > 0.0 || lbs > 0.0) ((st * 14.0) + lbs) / 2.20462262 else null
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Log Body Weight", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WeightUnit.entries.forEach { unit ->
                val label = when (unit) {
                    WeightUnit.KG -> "kg"
                    WeightUnit.LBS -> "lbs"
                    WeightUnit.STONE -> "stone (st)"
                }
                FilterChip(
                    selected = selectedUnit == unit,
                    onClick = {
                        if (selectedUnit != unit) {
                            // Populate target unit's text field from current effective kg smoothly
                            effectiveKg?.let { currentKg ->
                                when (unit) {
                                    WeightUnit.KG -> weightKgText = "%.1f".format(currentKg)
                                    WeightUnit.LBS -> weightLbsText = "%.1f".format(currentKg * 2.20462262)
                                    WeightUnit.STONE -> {
                                        val totalLbs = currentKg * 2.20462262
                                        stoneText = (totalLbs / 14).toInt().toString()
                                        stoneLbsText = "%.1f".format(totalLbs % 14).removeSuffix(".0")
                                    }
                                }
                            }
                            selectedUnit = unit
                        }
                    },
                    label = { Text(label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (selectedUnit) {
            WeightUnit.KG -> {
                OutlinedTextField(
                    value = weightKgText,
                    onValueChange = { input ->
                        // Allow typing numbers, decimal points, and deleting cleanly
                        if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                            weightKgText = input.take(6)
                        }
                    },
                    label = { Text("Weight (kg)") },
                    placeholder = { Text("70.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.MonitorWeight, contentDescription = null, tint = ChartColors.Body) },
                )
            }
            WeightUnit.LBS -> {
                OutlinedTextField(
                    value = weightLbsText,
                    onValueChange = { input ->
                        if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                            weightLbsText = input.take(6)
                        }
                    },
                    label = { Text("Weight (lbs)") },
                    placeholder = { Text("154.0") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Filled.MonitorWeight, contentDescription = null, tint = ChartColors.Body) },
                )
            }
            WeightUnit.STONE -> {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = stoneText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.all { it.isDigit() }) {
                                stoneText = input.take(3)
                            }
                        },
                        label = { Text("Stone (st)") },
                        placeholder = { Text("11") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = stoneLbsText,
                        onValueChange = { input ->
                            if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                                stoneLbsText = input.take(4)
                            }
                        },
                        label = { Text("Pounds (lbs)") },
                        placeholder = { Text("4.0") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        OutlinedTextField(
            value = bodyFatText,
            onValueChange = { input ->
                if (input.isEmpty() || input.matches(Regex("""^\d*\.?\d*$"""))) {
                    bodyFatText = input.take(4)
                }
            },
            label = { Text("Body Fat % (optional)") },
            placeholder = { Text("18.5") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = { Icon(Icons.Filled.PieChart, contentDescription = null) },
        )

        val fat = bodyFatText.toDoubleOrNull()
        val isValidWeight = effectiveKg != null && effectiveKg > 15.0

        Button(
            onClick = {
                val kgToLog = effectiveKg ?: return@Button
                scope.launch {
                    container.manualLogStore.logWeight(kgToLog, fat)
                    container.healthConnectRepository.writeWeight(kgToLog)
                    val formatted = when (selectedUnit) {
                        WeightUnit.KG -> "%.1f kg".format(kgToLog)
                        WeightUnit.LBS -> "%.1f lbs".format(kgToLog * 2.20462262)
                        WeightUnit.STONE -> "$stoneText st $stoneLbsText lbs"
                    }
                    onSuccess("✓ Logged weight: $formatted")
                }
            },
            enabled = isValidWeight,
            modifier = Modifier.fillMaxWidth().height(48.dp),
        ) {
            Icon(Icons.Filled.MonitorWeight, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Log Weight")
        }
    }
}
