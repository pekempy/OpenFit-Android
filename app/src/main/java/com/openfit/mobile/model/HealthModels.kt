package com.openfit.mobile.model

import kotlinx.serialization.Serializable

/** A single calendar day of aggregated health metrics, mirroring what the
 * OpenFit desktop app surfaces on its Today view - steps, sleep, resting
 * heart rate, active/zone minutes, distance, sedentary time, plus the
 * "nightly signals" vitals (HRV, SpO2, breathing rate, skin temperature). */
@Serializable
data class DailySnapshot(
    val date: String, // ISO yyyy-MM-dd
    val steps: Int? = null,
    val stepsGoal: Int? = null,
    val calories: Double? = null,
    val caloriesConsumed: Double? = null,
    val distanceMeters: Double? = null,
    val floors: Int? = null,
    val activeMinutes: Int? = null,
    val zoneMinutes: Int? = null,
    val sedentarySeconds: Int? = null,
    val restingHeartRateBpm: Int? = null,
    val hrvMillis: Double? = null,
    val spo2Percent: Double? = null,
    val breathingRatePerMin: Double? = null,
    val skinTemperatureDeltaC: Double? = null,
    val vo2Max: Double? = null,
    val weightKg: Double? = null,
    val bodyFatPercent: Double? = null,
    val waterLiters: Double? = null,
    /** 24 entries, hour-indexed (0-23), for the "steps per hour" bar chart. */
    val stepsHourly: List<Int> = emptyList(),
    val sleep: SleepSession? = null,
    // Body composition
    val heightMeters: Double? = null,
    val bodyWaterMassKg: Double? = null,
    val basalMetabolicRateKcal: Double? = null,
    // Workout telemetry (daily aggregate)
    val elevationGainedMeters: Double? = null,
    // Continuous heart rate (distinct from restingHeartRateBpm)
    val heartRateAvgBpm: Int? = null,
    val heartRateMinBpm: Int? = null,
    val heartRateMaxBpm: Int? = null,
    /** Minutes spent in each HR zone today, keyed by zone label (e.g.
     * "Zone 3 (Moderate)"). Empty unless the user has set a max heart rate
     * in Settings > Goals. */
    val heartRateZoneMinutes: Map<String, Int> = emptyMap(),
)

@Serializable
data class SleepStageMinutes(
    val stage: String, // "deep" | "light" | "rem" | "wake"
    val minutes: Int,
)

@Serializable
data class SleepStageSegment(
    val stage: String, // "deep" | "light" | "rem" | "wake"
    val startTimeIso: String,
    val endTimeIso: String,
)

@Serializable
data class SleepSession(
    val date: String,
    val startTimeIso: String? = null,
    val endTimeIso: String? = null,
    val totalMinutes: Int,
    val efficiencyPercent: Int? = null,
    val stages: List<SleepStageMinutes> = emptyList(),
    val segments: List<SleepStageSegment> = emptyList(),
)

@Serializable
data class ExerciseSession(
    val id: String,
    val date: String,
    val startTimeIso: String? = null,
    val endTimeIso: String? = null,
    val durationMinutes: Int,
    /** Raw activity type as reported by Google Health (e.g. "running", "walking"). */
    val originalType: String,
    val averageHeartRateBpm: Int? = null,
    val caloriesBurned: Double? = null,
    val distanceMeters: Double? = null,
    val elevationGainedMeters: Double? = null,
    val averagePowerWatts: Double? = null,
    val maxPowerWatts: Double? = null,
    val averageSpeedMetersPerSecond: Double? = null,
    val maxSpeedMetersPerSecond: Double? = null,
)

@Serializable
data class PairedDevice(
    val id: String,
    val deviceType: String? = null, // e.g. "PIXEL_WATCH", "FITBIT_CHARGE" - raw Google Health enum value
    val deviceVersion: String? = null,
    val batteryLevelPercent: Int? = null,
    val lastSyncTimeIso: String? = null,
    val signals: List<String> = emptyList(),
)

/** A single reproductive-health reading - menstruation flow/period,
 * cervical mucus, ovulation test, sexual activity, or intermenstrual
 * bleeding. Episodic rather than a daily numeric metric, so it's kept as a
 * flat dated list rather than folded into [DailySnapshot]. */
@Serializable
data class ReproductiveHealthEvent(
    val date: String,
    val type: String, // "menstruation_flow" | "menstruation_period" | "cervical_mucus" | "ovulation_test" | "sexual_activity" | "intermenstrual_bleeding"
    val detail: String, // human-readable reading, e.g. "Heavy flow", "Positive", "Protected"
)

/** Everything the UI needs for one selected date: the day itself plus a
 * trailing 14-day trend window, used for the sparkline-style "personal
 * trends" cards and for the AI daily-summary prompts. */
data class HealthSnapshotBundle(
    val selectedDate: String,
    val today: DailySnapshot,
    val trend: List<DailySnapshot>, // 14 days ending at selectedDate, ascending
    val exercises: List<ExerciseSession>,
    val devices: List<PairedDevice> = emptyList(),
    val reproductiveHealthEvents: List<ReproductiveHealthEvent> = emptyList(),
    val fetchedAtEpochMillis: Long,
    val partial: Boolean, // true if one or more sub-requests failed
    val errors: List<String> = emptyList(),
)
