package com.openfit.mobile.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openfit.mobile.OpenFitApplication
import kotlinx.coroutines.flow.first
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Fires a "move / stand up" reminder once per hour during the active window,
 * but only when the user has been sedentary for ≥ 50 minutes today.
 *
 * Uses the same window-clamping strategy as [HydrationReminderWorker].
 */
class MoveReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val container = (applicationContext as OpenFitApplication).container
        val settings  = container.settingsRepository.settingsFlow.first()

        if (!settings.reminders.moveReminder) return Result.success()

        val now         = ZonedDateTime.now()
        val windowStart = LocalTime.of(settings.reminders.morningSleepSummary.hour,
                                        settings.reminders.morningSleepSummary.minute)
        val windowEnd   = LocalTime.of(settings.reminders.eveningActivitySummary.hour,
                                        settings.reminders.eveningActivitySummary.minute)
        val nowTime     = now.toLocalTime()
        val inWindow    = nowTime >= windowStart && nowTime < windowEnd

        if (inWindow) {
            // Check sedentary time: only nudge if recent sedentary data suggests inactivity.
            // We use the cached bundle snapshot for a quick check without a full HC sync.
            val bundle = com.openfit.mobile.data.health.BundleCache
                .load(container.appContext, java.time.LocalDate.now().toString())

            val sedentarySeconds = bundle?.today?.sedentarySeconds ?: 0
            val stepsLastHour = bundle?.today?.stepsHourly
                ?.takeLast(2)?.sum() ?: 0

            // Fire if at least 50 min sedentary today OR fewer than 200 steps in the last 2 hours
            val shouldNudge = sedentarySeconds >= 50 * 60 || stepsLastHour < 200
            if (shouldNudge) {
                container.notificationHelper.showMoveReminder()
            }
        }

        // Schedule next in ~1 hour, clamped to window
        val delayMs = HydrationReminderWorker.nextDelayMillis(
            now, nowTime, 1L, windowStart, windowEnd
        )
        WorkScheduler.enqueueMoveReminder(applicationContext, delayMs)

        Result.success()
    } catch (_: Exception) {
        Result.failure()
    }
}
