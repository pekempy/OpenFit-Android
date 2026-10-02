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
 * Fires a hydration reminder notification and chains the next occurrence.
 *
 * Only fires when the current time falls within the user's active window
 * (between their morning summary time and evening summary time).  The next
 * occurrence is computed as `now + intervalHours`, then clamped back into
 * the window: if it would fall after the evening cutoff it is deferred to
 * the following morning; if before the morning start it is advanced to that
 * morning.
 */
class HydrationReminderWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = try {
        val container = (applicationContext as OpenFitApplication).container
        val settings  = container.settingsRepository.settingsFlow.first()
        val reminders = settings.reminders

        if (!reminders.hydrationReminder) return Result.success() // disabled; stop chain

        val now         = ZonedDateTime.now()
        val windowStart = LocalTime.of(settings.reminders.morningSleepSummary.hour,
                                        settings.reminders.morningSleepSummary.minute)
        val windowEnd   = LocalTime.of(settings.reminders.eveningActivitySummary.hour,
                                        settings.reminders.eveningActivitySummary.minute)
        val nowTime     = now.toLocalTime()

        val inWindow = nowTime >= windowStart && nowTime < windowEnd

        // Post notification only while inside the active window
        if (inWindow) {
            container.notificationHelper.showHydrationReminder()
        }

        // Schedule next occurrence
        val delayMs = nextDelayMillis(now, nowTime, reminders.hydrationIntervalHours.toLong(),
                                      windowStart, windowEnd)
        WorkScheduler.enqueueHydrationReminder(applicationContext, delayMs)

        Result.success()
    } catch (_: Exception) {
        Result.failure()
    }

    companion object {
        /**
         * Computes the delay in milliseconds until the next hydration reminder.
         *
         * `now + intervalHours` is the ideal next time.  If that falls outside
         * the window, it is bumped to the nearest valid boundary:
         *  - after eveningEnd  → tomorrow at windowStart
         *  - before windowStart → today at windowStart (or tomorrow if already past)
         */
        fun nextDelayMillis(
            now: ZonedDateTime,
            nowTime: LocalTime,
            intervalHours: Long,
            windowStart: LocalTime,
            windowEnd: LocalTime,
        ): Long {
            val ideal         = now.plusHours(intervalHours).truncatedTo(java.time.temporal.ChronoUnit.MINUTES)
            val idealTime     = ideal.toLocalTime()

            val nextFire: ZonedDateTime = when {
                idealTime >= windowEnd -> {
                    // Crosses into the night → wait until tomorrow morning
                    val tomorrowMorning = now.toLocalDate().plusDays(1)
                        .atTime(windowStart).atZone(now.zone)
                    tomorrowMorning
                }
                idealTime < windowStart -> {
                    // Lands before today's window → use today's window start (or tomorrow)
                    val todayMorning = now.toLocalDate().atTime(windowStart).atZone(now.zone)
                    if (todayMorning.isAfter(now)) todayMorning
                    else todayMorning.plusDays(1)
                }
                else -> ideal
            }

            return ChronoUnit.MILLIS.between(now, nextFire).coerceAtLeast(60_000L)
        }
    }
}
