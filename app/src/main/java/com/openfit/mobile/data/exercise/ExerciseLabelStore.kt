package com.openfit.mobile.data.exercise

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.exerciseLabelsDataStore: DataStore<Preferences> by preferencesDataStore(name = "openfit_exercise_labels")

/** Local-only relabeling of Google Health exercise sessions - e.g. renaming
 * a generic "Walking" entry to "Dog walking" or "Gardening". Google Health's
 * OAuth scopes granted here are read-only, so this can't (and shouldn't)
 * write back upstream; it's purely a display-layer override keyed by each
 * exercise session's stable id, stored as a simple JSON map. */
class ExerciseLabelStore(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    val labelsFlow: Flow<Map<String, String>> = context.exerciseLabelsDataStore.data.map { prefs ->
        prefs[KEY_LABELS]?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()
    }

    suspend fun setLabel(sessionId: String, customLabel: String) {
        context.exerciseLabelsDataStore.edit { prefs ->
            val current = prefs[KEY_LABELS]?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() } ?: emptyMap()
            val updated = if (customLabel.isBlank()) current - sessionId else current + (sessionId to customLabel)
            prefs[KEY_LABELS] = json.encodeToString(updated)
        }
    }

    suspend fun clearLabel(sessionId: String) = setLabel(sessionId, "")

    private companion object {
        val KEY_LABELS = stringPreferencesKey("session_id_to_label")
        val KEY_CUSTOM_WORKOUT_TYPES = stringPreferencesKey("custom_workout_types")
    }

    /** User-added custom workout types (beyond the built-in suggestions),
     * shown in the Quick Log Workout FAB and persisted locally. Fully
     * user-extensible - nothing about available workout types is fixed. */
    val customWorkoutTypesFlow: Flow<List<String>> = context.exerciseLabelsDataStore.data.map { prefs ->
        prefs[KEY_CUSTOM_WORKOUT_TYPES]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()
    }

    suspend fun addCustomWorkoutType(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        context.exerciseLabelsDataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_WORKOUT_TYPES]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()
            if (current.none { it.equals(trimmed, ignoreCase = true) } && DEFAULT_WORKOUT_TYPES.none { it.equals(trimmed, ignoreCase = true) }) {
                prefs[KEY_CUSTOM_WORKOUT_TYPES] = json.encodeToString(current + trimmed)
            }
        }
    }

    suspend fun removeCustomWorkoutType(name: String) {
        context.exerciseLabelsDataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_WORKOUT_TYPES]?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() } ?: emptyList()
            prefs[KEY_CUSTOM_WORKOUT_TYPES] = json.encodeToString(current.filterNot { it.equals(name, ignoreCase = true) })
        }
    }
}

/** Common quick-pick relabels, matching the kind of everyday activity that
 * gets misclassified or organised by auto-detection (a walk that was
 * actually the dog, a "strength training" block that was really gardening,
 * etc). The text field in the UI still accepts anything custom too. */
val EXERCISE_LABEL_SUGGESTIONS = listOf(
    "Dog walking", "Gardening", "Jog", "Hike", "Housework", "Chasing kids",
    "Grocery run", "Bike commute", "Stretching", "Yoga", "Physical therapy",
)

/** Built-in workout types offered in the Quick Log Workout FAB, alongside
 * any user-added custom types from [ExerciseLabelStore.customWorkoutTypesFlow].
 * Deliberately broader than a handful of options - covers the most common
 * everyday exercise categories, with calorie estimation falling back to a
 * sensible default MET for anything not explicitly listed (including every
 * user-added custom type). */
val DEFAULT_WORKOUT_TYPES = listOf(
    "Running", "Walking", "Cycling", "Strength", "HIIT", "Yoga",
    "Swimming", "Hiking", "Rowing", "Pilates", "Dancing", "Boxing",
)

/** Approximate MET (metabolic equivalent) values used to estimate calories
 * burned for a workout type in the Quick Log FAB. Unlisted/custom types
 * fall back to a moderate-activity default. */
val WORKOUT_TYPE_MET = mapOf(
    "running" to 9.8, "walking" to 3.5, "cycling" to 7.5, "strength" to 5.0,
    "hiit" to 8.5, "yoga" to 3.0, "swimming" to 8.0, "hiking" to 6.0,
    "rowing" to 7.0, "pilates" to 3.5, "dancing" to 5.5, "boxing" to 9.0,
)
