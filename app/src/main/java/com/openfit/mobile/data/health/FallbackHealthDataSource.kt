package com.openfit.mobile.data.health

import com.openfit.mobile.model.DailySnapshot
import com.openfit.mobile.model.HealthSnapshotBundle
import com.openfit.mobile.model.PairedDevice
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
            devices   = mergeDeviceLists(p.devices, f.devices),
            reproductiveHealthEvents = (p.reproductiveHealthEvents + f.reproductiveHealthEvents)
                .distinctBy { it.date + it.type },
            fetchedAtEpochMillis = maxOf(p.fetchedAtEpochMillis, f.fetchedAtEpochMillis),
            partial = p.partial || f.partial,
            errors  = p.errors + f.errors,
        )
    }

    /**
     * Merges two PairedDevice lists from different data sources (HC and Google
     * Health API) into a single deduplicated list.
     *
     * Problem: HC ids look like `"1_google_pixel_watch_2_com.google.android.apps.healthdata"`;
     * Google-API ids are bare numeric resource suffixes like `"8675309"`.  They
     * can never match by id even for the same physical device.
     *
     * Strategy: for each primary (HC) device, find the best-matching fallback
     * (API) device by name-token overlap (≥1 shared meaningful token of ≥3 chars).
     * When matched, field-level merge:
     *   - signals come from HC (HC inspects every record; API never populates signals)
     *   - batteryLevelPercent comes from whichever has it (API REST is more reliable)
     *   - lastSyncTimeIso takes the more recent value
     * Fallback devices without a matching primary entry are appended as-is.
     * A final distinctBy { id } catches any residual id-level duplicates.
     */
    private fun mergeDeviceLists(
        primary: List<PairedDevice>,
        fallback: List<PairedDevice>,
    ): List<PairedDevice> {
        // Tokens: lowercase alpha-numeric words of ≥3 chars, minus noise words.
        val stopWords = setOf("the", "for", "and", "with", "air", "gen", "pro", "max", "fit")
        fun tokens(name: String?): Set<String> = (name ?: "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), " ")
            .split(" ")
            .filter { it.length >= 3 && it !in stopWords }
            .toSet()

        val usedFallbackIds = mutableSetOf<String>()

        val merged = primary.map { pDev ->
            val pTok = tokens(pDev.deviceType)
            val match = fallback.firstOrNull { fDev ->
                fDev.id !in usedFallbackIds &&
                    tokens(fDev.deviceType).any { it in pTok }
            }
            if (match != null) {
                usedFallbackIds += match.id
                // Field-level merge: HC wins for signals, best-of for battery/sync
                pDev.copy(
                    batteryLevelPercent = pDev.batteryLevelPercent ?: match.batteryLevelPercent,
                    lastSyncTimeIso     = listOfNotNull(pDev.lastSyncTimeIso, match.lastSyncTimeIso)
                        .maxOrNull(),
                    signals = pDev.signals.ifEmpty { match.signals },
                )
            } else {
                pDev
            }
        }

        // Append fallback devices that had no primary match (device known to API but not HC yet)
        val extras = fallback.filter { it.id !in usedFallbackIds }
        return (merged + extras).distinctBy { it.id }
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
