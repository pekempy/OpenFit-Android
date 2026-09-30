package com.openfit.mobile.data.logs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

@Serializable
data class LoggedNutrition(
    val id: String = UUID.randomUUID().toString(),
    val date: String, // yyyy-MM-dd
    val timestampIso: String,
    val calories: Double,
    val mealType: String, // Breakfast, Lunch, Dinner, Snack
    val name: String,
)

@Serializable
data class LoggedExercise(
    val id: String = UUID.randomUUID().toString(),
    val date: String, // yyyy-MM-dd
    val startTimeIso: String,
    val exerciseType: String,
    val durationMinutes: Int,
    val caloriesBurned: Double? = null,
    val distanceMeters: Double? = null,
)

@Serializable
data class LoggedWeight(
    val id: String = UUID.randomUUID().toString(),
    val date: String, // yyyy-MM-dd
    val timestampIso: String,
    val weightKg: Double,
    val bodyFatPercent: Double? = null,
)

@Serializable
data class LoggedWater(
    val id: String = UUID.randomUUID().toString(),
    val date: String, // yyyy-MM-dd
    val timestampIso: String,
    val amountLiters: Double,
)

@Serializable
data class ManualHealthLogs(
    val nutrition: List<LoggedNutrition> = emptyList(),
    val exercises: List<LoggedExercise> = emptyList(),
    val weights: List<LoggedWeight> = emptyList(),
    val water: List<LoggedWater> = emptyList(),
)

private val Context.logsDataStore: DataStore<Preferences> by preferencesDataStore(name = "openfit_manual_logs")

class ManualLogStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val KEY_LOGS = stringPreferencesKey("manual_health_logs")

    val logsFlow: Flow<ManualHealthLogs> = context.logsDataStore.data.map { prefs ->
        prefs[KEY_LOGS]?.let { runCatching { json.decodeFromString<ManualHealthLogs>(it) }.getOrNull() }
            ?: ManualHealthLogs()
    }

    suspend fun logNutrition(calories: Double, mealType: String, name: String, time: Instant = Instant.now()): LoggedNutrition {
        val date = time.atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val entry = LoggedNutrition(
            date = date,
            timestampIso = time.toString(),
            calories = calories,
            mealType = mealType,
            name = name,
        )
        context.logsDataStore.edit { prefs ->
            val current = prefs[KEY_LOGS]?.let { runCatching { json.decodeFromString<ManualHealthLogs>(it) }.getOrNull() } ?: ManualHealthLogs()
            prefs[KEY_LOGS] = json.encodeToString(current.copy(nutrition = current.nutrition + entry))
        }
        return entry
    }

    suspend fun logExercise(
        exerciseType: String,
        durationMinutes: Int,
        caloriesBurned: Double? = null,
        distanceMeters: Double? = null,
        time: Instant = Instant.now(),
    ): LoggedExercise {
        val date = time.atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val entry = LoggedExercise(
            date = date,
            startTimeIso = time.toString(),
            exerciseType = exerciseType,
            durationMinutes = durationMinutes,
            caloriesBurned = caloriesBurned,
            distanceMeters = distanceMeters,
        )
        context.logsDataStore.edit { prefs ->
            val current = prefs[KEY_LOGS]?.let { runCatching { json.decodeFromString<ManualHealthLogs>(it) }.getOrNull() } ?: ManualHealthLogs()
            prefs[KEY_LOGS] = json.encodeToString(current.copy(exercises = current.exercises + entry))
        }
        return entry
    }

    suspend fun logWeight(weightKg: Double, bodyFatPercent: Double? = null, time: Instant = Instant.now()): LoggedWeight {
        val date = time.atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val entry = LoggedWeight(
            date = date,
            timestampIso = time.toString(),
            weightKg = weightKg,
            bodyFatPercent = bodyFatPercent,
        )
        context.logsDataStore.edit { prefs ->
            val current = prefs[KEY_LOGS]?.let { runCatching { json.decodeFromString<ManualHealthLogs>(it) }.getOrNull() } ?: ManualHealthLogs()
            prefs[KEY_LOGS] = json.encodeToString(current.copy(weights = current.weights + entry))
        }
        return entry
    }

    suspend fun logWater(amountLiters: Double, time: Instant = Instant.now()): LoggedWater {
        val date = time.atZone(ZoneId.systemDefault()).toLocalDate().toString()
        val entry = LoggedWater(
            date = date,
            timestampIso = time.toString(),
            amountLiters = amountLiters,
        )
        context.logsDataStore.edit { prefs ->
            val current = prefs[KEY_LOGS]?.let { runCatching { json.decodeFromString<ManualHealthLogs>(it) }.getOrNull() } ?: ManualHealthLogs()
            prefs[KEY_LOGS] = json.encodeToString(current.copy(water = current.water + entry))
        }
        return entry
    }
}
