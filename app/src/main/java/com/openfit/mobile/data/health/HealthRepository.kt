package com.openfit.mobile.data.health

import com.openfit.mobile.data.oauth.GoogleAuthManager
import com.openfit.mobile.data.oauth.OAuthConfig
import com.openfit.mobile.model.HealthSnapshotBundle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Fetches and translates a window of Google Health data for one selected
 * date, mirroring OpenFit desktop's syncGoogleHealthData(): a 14-day daily-
 * rollup trend for the aggregate metrics, plus intraday/session data
 * (sleep, exercise, resting-HR/HRV/SpO2/breathing/skin-temp/VO2max "daily
 * record" types) for the same window. */
class HealthRepository(
    private val api: GoogleHealthApi,
    private val authManager: GoogleAuthManager,
) {
    private val isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    suspend fun sync(oauthConfig: OAuthConfig, selectedDate: String): HealthSnapshotBundle = coroutineScope {
        val token = authManager.getValidAccessToken(oauthConfig)
        val bearer = "Bearer $token"
        val selected = LocalDate.parse(selectedDate, isoFormatter)
        val trendStart = selected.minusDays(13)
        val dayAfter = selected.plusDays(1)
        val trendDates = (0..13).map { trendStart.plusDays(it.toLong()).format(isoFormatter) }

        val jobs = linkedMapOf<String, kotlinx.coroutines.Deferred<JsonElement?>>(
            "stepsDaily" to async { runCatching { dailyRollUp(bearer, "steps", trendStart, dayAfter) }.getOrNull() },
            "caloriesDaily" to async { runCatching { dailyRollUp(bearer, "total-calories", trendStart, dayAfter) }.getOrNull() },
            "distanceDaily" to async { runCatching { dailyRollUp(bearer, "distance", trendStart, dayAfter) }.getOrNull() },
            "floorsDaily" to async { runCatching { dailyRollUp(bearer, "floors", trendStart, dayAfter) }.getOrNull() },
            "activeMinutesDaily" to async { runCatching { dailyRollUp(bearer, "active-minutes", trendStart, dayAfter) }.getOrNull() },
            "zoneMinutesDaily" to async { runCatching { dailyRollUp(bearer, "active-zone-minutes", trendStart, dayAfter) }.getOrNull() },
            "sedentaryDaily" to async { runCatching { dailyRollUp(bearer, "sedentary-period", trendStart, dayAfter) }.getOrNull() },
            "weightDaily" to async { runCatching { dailyRollUp(bearer, "weight", trendStart, dayAfter) }.getOrNull() },
            "fatDaily" to async { runCatching { dailyRollUp(bearer, "body-fat", trendStart, dayAfter) }.getOrNull() },
            "waterDaily" to async { runCatching { dailyRollUp(bearer, "hydration-log", trendStart, dayAfter) }.getOrNull() },
            "restingHeartRaw" to async {
                runCatching { listReconcile(bearer, "daily-resting-heart-rate", "daily", trendStart.format(isoFormatter), dayAfter.format(isoFormatter)) }.getOrNull()
            },
            "hrvRaw" to async {
                runCatching { listReconcile(bearer, "daily-heart-rate-variability", "daily", trendStart.format(isoFormatter), dayAfter.format(isoFormatter)) }.getOrNull()
            },
            "spo2Raw" to async {
                runCatching { listReconcile(bearer, "daily-oxygen-saturation", "daily", trendStart.format(isoFormatter), dayAfter.format(isoFormatter)) }.getOrNull()
            },
            "breathingRaw" to async {
                runCatching { listReconcile(bearer, "daily-respiratory-rate", "daily", trendStart.format(isoFormatter), dayAfter.format(isoFormatter)) }.getOrNull()
            },
            "skinTemperatureRaw" to async {
                runCatching { listReconcile(bearer, "daily-sleep-temperature-derivations", "daily", trendStart.format(isoFormatter), dayAfter.format(isoFormatter)) }.getOrNull()
            },
            "cardioRaw" to async {
                runCatching { listReconcile(bearer, "daily-vo2-max", "daily", trendStart.format(isoFormatter), dayAfter.format(isoFormatter)) }.getOrNull()
            },
            "stepsIntradayRaw" to async {
                runCatching {
                    listReconcile(bearer, "steps", "interval", selectedDate, dayAfter.format(isoFormatter), dataSourceFamily = "google-wearables")
                }.getOrNull()
            },
            "sleepRaw" to async {
                runCatching {
                    listReconcile(bearer, "sleep", "sleep", trendStart.format(isoFormatter), dayAfter.format(isoFormatter), pageSize = "25")
                }.getOrNull()
            },
            "activitiesRaw" to async {
                runCatching { listReconcile(bearer, "exercise", "session", trendStart.format(isoFormatter), dayAfter.format(isoFormatter), pageSize = "25") }.getOrNull()
            },
            "devicesRaw" to async {
                runCatching { HealthApiRateLimiter.withSlot { api.pairedDevices(bearer) } }.getOrNull()
            },
        )

        val results = jobs.mapValues { (_, deferred) -> deferred.await() }
        val errors = results.filterValues { it == null }.keys.toList()

        val bundle = HealthTranslator.translate(
            raw = results.filterValues { it != null }.mapValues { it.value!! },
            selectedDate = selectedDate,
            trendDates = trendDates,
        )
        if (errors.isEmpty()) bundle else bundle.copy(partial = true, errors = errors.map { "$it unavailable" })
    }

    private suspend fun dailyRollUp(bearer: String, type: String, start: LocalDate, endExclusive: LocalDate): JsonObject =
        HealthApiRateLimiter.withSlot {
            val body = buildJsonObject {
                put("range", buildJsonObject {
                    put("start", civilDateTime(start, endOfDay = false))
                    put("end", civilDateTime(endExclusive.minusDays(1), endOfDay = true))
                })
                put("windowSizeDays", 1)
            }
            api.dailyRollUp(bearer, type, body)
        }

    private suspend fun listReconcile(
        bearer: String,
        type: String,
        recordType: String,
        start: String,
        end: String,
        dataSourceFamily: String? = "all-sources",
        pageSize: String = "10000",
    ): JsonObject {
        val filter = dataFilter(type, recordType, start, end)
        val allPoints = mutableListOf<JsonElement>()
        var pageToken: String? = null
        var pageCount = 0
        do {
            val page = HealthApiRateLimiter.withSlot {
                api.listDataPointsReconcile(
                    bearer = bearer,
                    type = type,
                    filter = filter,
                    pageSize = pageSize,
                    dataSourceFamily = dataSourceFamily?.let { "users/me/dataSourceFamilies/$it" },
                    pageToken = pageToken,
                )
            }
            allPoints += page.dataPoints()
            pageToken = (page["nextPageToken"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
            pageCount += 1
        } while (pageToken != null && pageCount < 100)
        return buildJsonObject {
            put("dataPoints", kotlinx.serialization.json.JsonArray(allPoints))
        }
    }

    private fun dataFilter(type: String, recordType: String, start: String, end: String): String {
        val field = type.replace('-', '_')
        return when (recordType) {
            "daily" -> "$field.date >= \"$start\" AND $field.date < \"$end\""
            "sleep" -> "sleep.interval.civil_end_time >= \"$start\" AND sleep.interval.civil_end_time < \"$end\""
            "sample" -> "$field.sample_time.civil_time >= \"$start\" AND $field.sample_time.civil_time < \"$end\""
            else -> "$field.interval.civil_start_time >= \"$start\" AND $field.interval.civil_start_time < \"$end\""
        }
    }

    private fun civilDateTime(date: LocalDate, endOfDay: Boolean): JsonObject = buildJsonObject {
        put("date", buildJsonObject {
            put("year", date.year)
            put("month", date.monthValue)
            put("day", date.dayOfMonth)
        })
        put("time", buildJsonObject {
            if (endOfDay) {
                put("hours", 23); put("minutes", 59); put("seconds", 59); put("nanos", 0)
            } else {
                put("hours", 0); put("minutes", 0); put("seconds", 0); put("nanos", 0)
            }
        })
    }
}
