package com.openfit.mobile.ui.charts

import androidx.compose.ui.graphics.Color

/** Per-metric accent colors, matching OpenFit desktop's convention: orange
 * for movement, blue for sleep, red/pink for heart, distinct cool tones for
 * the other vitals, and a Fitbit-style four-color sleep-stage palette. */
object ChartColors {
    val Movement = Color(0xFFFFA726) // amber - steps, distance, floors, active/zone minutes
    val Sleep = Color(0xFF29B6F6) // light blue - sleep duration/efficiency
    val Heart = Color(0xFFEF5350) // red/pink - resting heart rate
    val Hrv = Color(0xFF26C6DA) // teal
    val Spo2 = Color(0xFF5C6BC0) // indigo-blue
    val Breathing = Color(0xFF66BB6A) // green
    val Temperature = Color(0xFFFF7043) // deep orange - thermal
    val Cardio = Color(0xFFAB47BC) // purple - VO2 max / cardio fitness
    val Calories = Color(0xFFFF7043)
    val Body = Color(0xFF8D6E63) // warm neutral - weight/body fat

    // Sleep-stage palette
    val SleepDeep = Color(0xFF7E57C2) // purple
    val SleepLight = Color(0xFF29B6F6) // cyan/blue
    val SleepRem = Color(0xFFFFA726) // orange
    val SleepWake = Color(0xFF616161) // muted grey

    fun sleepStageColor(stage: String): Color = when (stage.lowercase()) {
        "deep" -> SleepDeep
        "light" -> SleepLight
        "rem" -> SleepRem
        else -> SleepWake
    }
}
