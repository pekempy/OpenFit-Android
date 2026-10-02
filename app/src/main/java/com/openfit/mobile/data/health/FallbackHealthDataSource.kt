package com.openfit.mobile.data.health

import com.openfit.mobile.model.DailySnapshot
import com.openfit.mobile.model.HealthSnapshotBundle
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Wraps two [HealthDataSource] implementations and produces a single merged
 * [HealthSnapshotBundle] that draws on whichever sources are connected.
 *
 * Strategy:
 *  - If only [primary] is connected  → behaves exactly like [primary].
 *  - If only [fallback] is connected → behaves exactly like [fallback].
 *  - If both are connected           → fetches both in parallel and merges,
 *    preferring [primary] values; [fallback] fills fields that [primary]
 *    returned null for.
 *
 * Callers never need to know how many sources are active — the bundle shape
 * is identical regardless.
 */
class FallbackHealthDataSource(
    private val primary: HealthDataSource,
    private val fallback: HealthDataSource,
) : HealthDataSource {

    override suspend fun isConnected(): Boolean =
        primary.isConnected() || fallback.isConnected()

    override suspend fun sync(selectedDate: String): HealthSnapshotBundle = coroutineScope {
        val primaryConnected  = primary.isConnected()
        val fallbackConnected = fallback.isConnected()

        when {
            primaryConnected && fallbackConnected -> {
                // Fetch both concurrently; either can fail independently.
                val primaryJob  = async { runCatching { primary.sync(selectedDate) } }
                val fallbackJob = async { runCatching { fallback.sync(selectedDate) } }

                val primaryResult  = primaryJob.await()
                val fallbackResult = fallbackJob.await()

                when {
                    primaryResult.isSuccess && fallbackResult.isSuccess ->
                        merge(primaryResult.getOrThrow(), fallbackResult.getOrThrow())
                    primaryResult.isSuccess  -> primaryResult.getOrThrow()
                    fallbackResult.isSuccess -> fallbackResult.getOrThrow()
                    else -> throw primaryResult.exceptionOrNull()
                        ?: Exception("Both health data sources failed")
                }
            }
            primaryConnected  -> primary.sync(selectedDate)
            fallbackConnected -> fallback.sync(selectedDate)
            else -> throw Exception("Not connected to any health data source")
        }
    }

    // ── Merge helpers ─────────────────────────────────────────────────────────

    private fun merge(p: HealthSnapshotBundle, f: HealthSnapshotBundle): HealthSnapshotBundle {
        // Build a lookup of fallback trend days keyed by date so merging is O(n).
        val fallbackTrendByDate = (f.trend + f.today).associateBy { it.date }

        val mergedTrend = buildList {
            val allDates = (p.trend.map { it.date } + f.trend.map { it.date }).distinct().sorted()
            for (date in allDates) {
                val pDay = p.trend.find { it.date == date }
                val fDay = fallbackTrendByDate[date]
                when {
                    pDay != null && fDay != null -> add(mergeSnapshot(pDay, fDay))
                    pDay != null -> add(pDay)
                    fDay != null -> add(fDay)
                }
            }
        }

        val fToday = fallbackTrendByDate[p.today.date]

        return p.copy(
            today     = if (fToday != null) mergeSnapshot(p.today, fToday) else p.today,
            trend     = mergedTrend,
            exercises = (p.exercises + f.exercises).distinctBy { it.id },
            devices   = (p.devices + f.devices).distinctBy { it.id },
            reproductiveHealthEvents = (p.reproductiveHealthEvents + f.reproductiveHealthEvents)
                .distinctBy { it.date + it.type },
            fetchedAtEpochMillis = maxOf(p.fetchedAtEpochMillis, f.fetchedAtEpochMillis),
            partial = p.partial || f.partial,
            errors  = p.errors + f.errors,
        )
    }

    /**
     * Merges two snapshots for the same date. [primary] wins for every field
     * that is non-null; [fallback] fills in any gaps.
     */
    private fun mergeSnapshot(primary: DailySnapshot, fallback: DailySnapshot): DailySnapshot =
        primary.copy(
            steps                  = primary.steps                  ?: fallback.steps,
            stepsGoal              = primary.stepsGoal              ?: fallback.stepsGoal,
            calories               = primary.calories               ?: fallback.calories,
            caloriesConsumed       = primary.caloriesConsumed       ?: fallback.caloriesConsumed,
            distanceMeters         = primary.distanceMeters         ?: fallback.distanceMeters,
            floors                 = primary.floors                 ?: fallback.floors,
            activeMinutes          = primary.activeMinutes          ?: fallback.activeMinutes,
            zoneMinutes            = primary.zoneMinutes            ?: fallback.zoneMinutes,
            sedentarySeconds       = primary.sedentarySeconds       ?: fallback.sedentarySeconds,
            restingHeartRateBpm    = primary.restingHeartRateBpm    ?: fallback.restingHeartRateBpm,
            hrvMillis              = primary.hrvMillis              ?: fallback.hrvMillis,
            spo2Percent            = primary.spo2Percent            ?: fallback.spo2Percent,
            breathingRatePerMin    = primary.breathingRatePerMin    ?: fallback.breathingRatePerMin,
            skinTemperatureDeltaC  = primary.skinTemperatureDeltaC  ?: fallback.skinTemperatureDeltaC,
            vo2Max                 = primary.vo2Max                 ?: fallback.vo2Max,
            weightKg               = primary.weightKg               ?: fallback.weightKg,
            bodyFatPercent         = primary.bodyFatPercent         ?: fallback.bodyFatPercent,
            waterLiters            = primary.waterLiters            ?: fallback.waterLiters,
            stepsHourly            = primary.stepsHourly.ifEmpty    { fallback.stepsHourly },
            sleep                  = primary.sleep                  ?: fallback.sleep,
            naps                   = primary.naps.ifEmpty           { fallback.naps },
            heightMeters           = primary.heightMeters           ?: fallback.heightMeters,
            bodyWaterMassKg        = primary.bodyWaterMassKg        ?: fallback.bodyWaterMassKg,
            basalMetabolicRateKcal = primary.basalMetabolicRateKcal ?: fallback.basalMetabolicRateKcal,
            elevationGainedMeters  = primary.elevationGainedMeters  ?: fallback.elevationGainedMeters,
            heartRateAvgBpm        = primary.heartRateAvgBpm        ?: fallback.heartRateAvgBpm,
            heartRateMinBpm        = primary.heartRateMinBpm        ?: fallback.heartRateMinBpm,
            heartRateMaxBpm        = primary.heartRateMaxBpm        ?: fallback.heartRateMaxBpm,
            heartRateZoneMinutes   = primary.heartRateZoneMinutes.ifEmpty { fallback.heartRateZoneMinutes },
        )
}
