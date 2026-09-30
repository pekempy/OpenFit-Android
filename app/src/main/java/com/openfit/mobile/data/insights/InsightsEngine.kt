package com.openfit.mobile.data.insights

import com.openfit.mobile.data.settings.UserHealthGoals
import com.openfit.mobile.model.DailySnapshot
import com.openfit.mobile.model.HealthSnapshotBundle
import kotlin.math.abs

enum class InsightKind { POSITIVE, ATTENTION, NEUTRAL }

data class Insight(val text: String, val kind: InsightKind)

/** Deterministic, rule-based pattern detection over the 14-day trend window
 * - goal streaks and deviation-from-average flags. This is NOT machine
 * learning; every insight is a plain, explainable statistical comparison
 * (consecutive-day counts, percent change vs the trailing average) so it's
 * always possible to see exactly why an insight fired. Runs entirely
 * on-device, no AI call involved. */
object InsightsEngine {

    fun generate(bundle: HealthSnapshotBundle, goals: UserHealthGoals): List<Insight> {
        val trend = bundle.trend
        val today = bundle.today
        val insights = mutableListOf<Insight>()

        fun streak(predicate: (DailySnapshot) -> Boolean): Int {
            var count = 0
            for (day in trend.asReversed()) {
                if (predicate(day)) count++ else break
            }
            return count
        }

        val stepStreak = streak { (it.steps ?: 0) >= goals.stepGoal }
        if (stepStreak >= 2) {
            insights += Insight("You've hit your step goal $stepStreak days running.", InsightKind.POSITIVE)
        }

        val sleepStreak = streak { (it.sleep?.totalMinutes ?: 0) >= goals.sleepMinutesGoal }
        if (sleepStreak >= 2) {
            insights += Insight("You've met your sleep goal $sleepStreak nights running.", InsightKind.POSITIVE)
        }

        val sleepShortfallStreak = streak {
            val minutes = it.sleep?.totalMinutes
            minutes != null && minutes < goals.sleepMinutesGoal - 60
        }
        if (sleepShortfallStreak >= 2) {
            insights += Insight(
                "You've slept over an hour under target for $sleepShortfallStreak nights running.",
                InsightKind.ATTENTION,
            )
        }

        fun deviationInsight(
            label: String,
            todayValue: Double?,
            history: List<Double?>,
            higherIsAttention: Boolean,
            thresholdPercent: Double = 12.0,
        ): Insight? {
            val past = history.dropLast(1).filterNotNull()
            if (todayValue == null || past.size < 3) return null
            val avg = past.average()
            if (avg == 0.0) return null
            val pctChange = ((todayValue - avg) / avg) * 100.0
            if (abs(pctChange) < thresholdPercent) return null
            val direction = if (pctChange > 0) "above" else "below"
            val kind = if ((pctChange > 0) == higherIsAttention) InsightKind.ATTENTION else InsightKind.POSITIVE
            return Insight(
                "$label is ${"%.0f".format(abs(pctChange))}% $direction your 2-week average today.",
                kind,
            )
        }

        deviationInsight(
            "Resting heart rate",
            today.restingHeartRateBpm?.toDouble(),
            trend.map { it.restingHeartRateBpm?.toDouble() },
            higherIsAttention = true,
        )?.let { insights += it }

        deviationInsight(
            "Heart rate variability",
            today.hrvMillis,
            trend.map { it.hrvMillis },
            higherIsAttention = false,
        )?.let { insights += it }

        deviationInsight(
            "Sleep duration",
            today.sleep?.totalMinutes?.toDouble(),
            trend.map { it.sleep?.totalMinutes?.toDouble() },
            higherIsAttention = false,
        )?.let { insights += it }

        return insights.take(5)
    }
}
