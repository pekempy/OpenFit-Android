package com.openfit.mobile.data.health

import com.openfit.mobile.model.DailySnapshot
import com.openfit.mobile.model.ExerciseSession
import com.openfit.mobile.model.HealthSnapshotBundle
import com.openfit.mobile.model.PairedDevice
import com.openfit.mobile.model.SleepSession
import com.openfit.mobile.model.SleepStageMinutes
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/** Raw responses keyed exactly like the parallel job list in
 * HealthRepository.syncSelectedDate(), then folded into the app's own
 * [DailySnapshot]/[SleepSession]/[ExerciseSession] models. Field paths below
 * are the real Google Health API v4 response shapes (ported from OpenFit
 * desktop's electron/google-health-service.cjs translateGoogleHealth()). */
object HealthTranslator {

    fun translate(raw: Map<String, JsonElement>, selectedDate: String, trendDates: List<String>): HealthSnapshotBundle {
        val steps = dailyRollupMap(raw["stepsDaily"]) { it.obj("steps").num("countSum") }
        val calories = dailyRollupMap(raw["caloriesDaily"]) { it.obj("totalCalories").num("kcalSum") }
        val distanceMm = dailyRollupMap(raw["distanceDaily"]) { it.obj("distance").num("millimetersSum") }
        val floors = dailyRollupMap(raw["floorsDaily"]) { it.obj("floors").num("countSum") }
        val activeMinutesByLevel = dailyRollupMap(raw["activeMinutesDaily"]) { point ->
            val levels = point.obj("activeMinutes").arr("activeMinutesRollupByActivityLevel") ?: return@dailyRollupMap null
            levels.filterIsInstance<JsonObject>().associate { lvl ->
                (lvl.str("activityLevel") ?: "") to (lvl.num("activeMinutesSum") ?: 0.0)
            }
        }
        val zoneMinutes = dailyRollupMap(raw["zoneMinutesDaily"]) { point ->
            point.obj("activeZoneMinutes")?.values?.sumOf { (it as? kotlinx.serialization.json.JsonPrimitive)?.let { p -> p.content.toDoubleOrNull() } ?: 0.0 }
        }
        val sedentarySeconds = dailyRollupMap(raw["sedentaryDaily"]) { point ->
            val duration = point.obj("sedentaryPeriod")?.str("durationSum") ?: return@dailyRollupMap null
            durationSeconds(duration)
        }
        val weightKg = dailyRollupMap(raw["weightDaily"]) { point -> point.obj("weight").num("weightGramsAvg")?.div(1000.0) }
        val bodyFat = dailyRollupMap(raw["fatDaily"]) { it.obj("bodyFat").num("bodyFatPercentageAvg") }
        val waterMl = dailyRollupMap(raw["waterDaily"]) { it.obj("hydrationLog").obj("amountConsumed").num("millilitersSum") }

        val restingHeart = dailyRecordMap(raw["restingHeartRaw"], "dailyRestingHeartRate") { it.num("beatsPerMinute") }
        val hrv = dailyRecordMap(raw["hrvRaw"], "dailyHeartRateVariability") { record ->
            record.num("averageHeartRateVariabilityMilliseconds")
                ?: record.num("deepSleepRootMeanSquareOfSuccessiveDifferencesMilliseconds")
        }
        val spo2 = dailyRecordMap(raw["spo2Raw"], "dailyOxygenSaturation") { it.num("averagePercentage") }
        val breathing = dailyRecordMap(raw["breathingRaw"], "dailyRespiratoryRate") { it.num("breathsPerMinute") }
        val skinTemp = dailyRecordMap(raw["skinTemperatureRaw"], "dailySleepTemperatureDerivations") { record ->
            val nightly = record.num("nightlyTemperatureCelsius")
            val baseline = record.num("baselineTemperatureCelsius")
            if (nightly == null || baseline == null) null else nightly - baseline
        }
        val vo2Max = dailyRecordMap(raw["cardioRaw"], "dailyVo2Max") { it.num("vo2Max") }

        val sleepRecords = raw["sleepRaw"].dataPoints().mapNotNull(::parseSleepPoint)
        // Keep ALL sessions per date sorted by start time; callers split overnight vs naps.
        val sleepByDate = sleepRecords
            .groupBy { it.date }
            .mapValues { (_, sessions) -> sessions.sortedBy { it.startTimeIso ?: "" } }

        val exercises = raw["activitiesRaw"].dataPoints().mapNotNull(::parseExercisePoint)

        fun snapshotFor(date: String): DailySnapshot {
            val allSleep = sleepByDate[date] ?: emptyList()
            return DailySnapshot(
                date = date,
                steps = steps[date]?.toInt(),
                calories = calories[date],
                distanceMeters = distanceMm[date]?.div(1000.0),
                floors = floors[date]?.toInt(),
                activeMinutes = activeMinutesByLevel[date]?.let { levels ->
                    ((levels["MODERATE"] ?: 0.0) + (levels["VIGOROUS"] ?: 0.0)).toInt()
                },
                zoneMinutes = zoneMinutes[date]?.toInt(),
                sedentarySeconds = sedentarySeconds[date]?.toInt(),
                restingHeartRateBpm = restingHeart[date]?.toInt(),
                hrvMillis = hrv[date],
                spo2Percent = spo2[date],
                breathingRatePerMin = breathing[date],
                skinTemperatureDeltaC = skinTemp[date],
                vo2Max = vo2Max[date],
                weightKg = weightKg[date],
                bodyFatPercent = bodyFat[date],
                waterLiters = waterMl[date]?.div(1000.0),
                sleep = allSleep.firstOrNull { !it.isNap },
                naps = allSleep.filter { it.isNap },
            )
        }

        val stepsHourly = parseHourlySteps(raw["stepsIntradayRaw"])

        val trend = trendDates.map(::snapshotFor)
        val today = (trend.lastOrNull { it.date == selectedDate } ?: snapshotFor(selectedDate))
            .copy(stepsHourly = stepsHourly)

        return HealthSnapshotBundle(
            selectedDate = selectedDate,
            today = today,
            trend = trend,
            exercises = exercises.filter { it.date == selectedDate },
            devices = parseDevices(raw["devicesRaw"]),
            fetchedAtEpochMillis = System.currentTimeMillis(),
            partial = false,
        )
    }

    private fun parseDevices(payload: JsonElement?): List<PairedDevice> {
        val list = payload.arr("pairedDevices")?.filterIsInstance<JsonObject>() ?: return emptyList()
        return list.map { device ->
            val name = device.str("name") ?: ""
            PairedDevice(
                id = name.substringAfterLast('/').ifBlank { name },
                deviceType = device.str("deviceType"),
                deviceVersion = device.str("deviceVersion"),
                batteryLevelPercent = device.numInt("batteryLevel"),
                lastSyncTimeIso = device.str("lastSyncTime"),
            )
        }
    }

    private fun parseSleepPoint(point: JsonObject): SleepSession? {
        val sleep = point.obj("sleep") ?: return null
        val interval = sleep.obj("interval")
        val summary = sleep.obj("summary")
        val endCivil = interval?.get("civilEndTime")
        val date = dateFromCivil(endCivil) ?: localDateFromUtc(interval?.str("endTime")) ?: return null

        val hasDetailedStages = (summary.arr("stagesSummary") ?: emptyList<JsonElement>())
            .filterIsInstance<JsonObject>()
            .any { it.str("type")?.uppercase() in setOf("LIGHT", "DEEP", "REM") }

        val stageMinutes = LinkedHashMap<String, Int>()
        for (stage in summary.arr("stagesSummary")?.filterIsInstance<JsonObject>() ?: emptyList()) {
            val rawType = stage.str("type")?.lowercase() ?: continue
            if (rawType == "asleep" && hasDetailedStages) continue // ASLEEP is the LIGHT+DEEP+REM aggregate; skip when we have the breakdown
            val key = sleepStageKey(rawType) ?: continue
            val minutes = stage.num("minutes")?.toInt() ?: 0
            stageMinutes[key] = (stageMinutes[key] ?: 0) + minutes
        }

        val asleepMinutes = (summary.num("minutesAsleep") ?: 0.0).toInt()
        val periodMinutes = summary.num("minutesInSleepPeriod")?.toInt()
        val efficiency = if (periodMinutes != null && periodMinutes > 0) (asleepMinutes * 100 / periodMinutes) else null

        val startHour = interval?.str("startTime")?.let {
            runCatching { java.time.Instant.parse(it).atZone(java.time.ZoneId.systemDefault()).hour }.getOrNull()
        }
        val isNap = startHour != null && startHour in 10..20
        return SleepSession(
            date = date,
            startTimeIso = interval?.str("startTime"),
            endTimeIso = interval?.str("endTime"),
            totalMinutes = asleepMinutes,
            efficiencyPercent = efficiency,
            stages = stageMinutes.map { (stage, minutes) -> SleepStageMinutes(stage, minutes) },
            isNap = isNap,
        )
    }

    private fun sleepStageKey(type: String): String? = when (type) {
        "awake", "restless" -> "wake"
        "asleep" -> "light"
        "deep", "light", "rem", "wake" -> type
        else -> null
    }

    /** Converts a UTC ISO-8601 timestamp to a local YYYY-MM-DD date using
     * the device's default timezone. Falls back to slicing the UTC string if
     * parsing fails so we never return null on a valid-looking timestamp. */
    private fun localDateFromUtc(utcString: String?): String? {
        utcString ?: return null
        return runCatching {
            java.time.Instant.parse(utcString)
                .atZone(java.time.ZoneId.systemDefault())
                .toLocalDate()
                .format(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE)
        }.getOrElse { utcString.take(10) }
    }


    private fun parseExercisePoint(point: JsonObject): ExerciseSession? {
        val exercise = point.obj("exercise") ?: return null
        val interval = exercise.obj("interval")
        val start = interval?.str("startTime") ?: return null
        val summary = exercise.obj("metricsSummary")
        val durationSecondsValue = durationSeconds(exercise.str("activeDuration"))
        val date = start.take(10)
        return ExerciseSession(
            id = point.str("dataPointName") ?: point.str("name") ?: "$start-${exercise.str("exerciseType")}",
            date = date,
            startTimeIso = start,
            endTimeIso = interval.str("endTime"),
            durationMinutes = (durationSecondsValue / 60.0).toInt(),
            originalType = exercise.str("displayName")
                ?: exercise.str("exerciseType")?.replace('_', ' ')?.lowercase()
                ?: "activity",
            averageHeartRateBpm = summary.num("averageHeartRateBeatsPerMinute")?.toInt(),
            caloriesBurned = summary.num("caloriesKcal"),
            distanceMeters = summary.num("distanceMillimeters")?.div(1000.0),
        )
    }

    /** Buckets intraday step data points into 24 hourly totals for the
     * "steps per hour" bar chart. */
    private fun parseHourlySteps(payload: JsonElement?): List<Int> {
        val buckets = IntArray(24)
        for (point in payload.dataPoints()) {
            val record = point.obj("steps") ?: continue
            val interval = record.obj("interval")
            val timeString = timeFromCivil(interval?.get("civilStartTime")) ?: interval?.str("startTime")?.drop(11)?.take(5)
            val hour = timeString?.take(2)?.toIntOrNull() ?: continue
            if (hour !in 0..23) continue
            buckets[hour] += (record.num("count") ?: 0.0).toInt()
        }
        return buckets.toList()
    }
}
