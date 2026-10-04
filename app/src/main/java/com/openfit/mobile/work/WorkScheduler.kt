package com.openfit.mobile.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.settings.SummarySchedule
import java.time.Duration
import java.time.LocalTime
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

/** Schedules the daily morning/evening summary workers at a fixed wall-clock
 * time. WorkManager's PeriodicWorkRequest can't pin an exact time-of-day, so
 * each run is a OneTimeWorkRequest that reschedules its own next occurrence
 * on completion (see BaseSummaryWorker.rescheduleNext()). */
object WorkScheduler {
    const val MORNING_WORK_NAME   = "morning_sleep_summary"
    const val EVENING_WORK_NAME   = "evening_activity_summary"
    const val HYDRATION_WORK_NAME = "hydration_reminder"
    const val MOVE_WORK_NAME      = "move_reminder"

    fun scheduleMorningSummary(container: AppContainer, schedule: SummarySchedule) =
        schedule(container.appContext, MORNING_WORK_NAME, schedule, MorningSummaryWorker::class.java)

    fun scheduleEveningSummary(container: AppContainer, schedule: SummarySchedule) =
        schedule(container.appContext, EVENING_WORK_NAME, schedule, EveningSummaryWorker::class.java)

    /** Called by BootRescheduleReceiver and by each worker after it finishes,
     * to queue the following day's run (or the very first run, after boot). */
    fun rescheduleNext(context: Context, name: String, hour: Int, minute: Int, workerClass: Class<out ListenableWorker>) {
        enqueue(context, name, workerClass, computeInitialDelayMillis(hour, minute, forceNextOccurrence = true))
    }

    private fun schedule(context: Context, name: String, schedule: SummarySchedule, workerClass: Class<out ListenableWorker>) {
        val workManager = WorkManager.getInstance(context)
        if (!schedule.enabled) {
            workManager.cancelUniqueWork(name)
            return
        }
        enqueue(context, name, workerClass, computeInitialDelayMillis(schedule.hour, schedule.minute))
    }

    private fun enqueue(context: Context, name: String, workerClass: Class<out ListenableWorker>, delayMillis: Long) {
        val request = OneTimeWorkRequest.Builder(workerClass)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(name)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
    }

    private fun computeInitialDelayMillis(hour: Int, minute: Int, forceNextOccurrence: Boolean = false): Long {
        val now = ZonedDateTime.now()
        var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (forceNextOccurrence || !target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMillis().coerceAtLeast(0L)
    }

    // ── Hydration reminder ────────────────────────────────────────────────

    /**
     * Schedules the first hydration reminder at the next window-start time
     * (morning summary hour) when [enabled], or cancels the chain when false.
     * Subsequent occurrences are self-rescheduled by [HydrationReminderWorker].
     */
    fun scheduleHydrationReminder(
        context: Context,
        enabled: Boolean,
        morningHour: Int,
        morningMinute: Int,
    ) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(HYDRATION_WORK_NAME); return }
        // If we're already inside the active window (past morning start), fire in
        // 1 minute so the user gets reminders today instead of waiting until
        // tomorrow's window start.
        val nowTime = ZonedDateTime.now().toLocalTime()
        val windowStart = LocalTime.of(morningHour, morningMinute)
        val delayMs = if (nowTime >= windowStart) 60_000L
                      else computeInitialDelayMillis(morningHour, morningMinute)
        enqueue(context, HYDRATION_WORK_NAME, HydrationReminderWorker::class.java, delayMs)
    }


    /** Called by [HydrationReminderWorker] to chain the next occurrence. */
    fun enqueueHydrationReminder(context: Context, delayMs: Long) =
        enqueue(context, HYDRATION_WORK_NAME, HydrationReminderWorker::class.java, delayMs)

    // ── Move / stand-up reminder ──────────────────────────────────────────

    fun scheduleMoveReminder(
        context: Context,
        enabled: Boolean,
        morningHour: Int,
        morningMinute: Int,
    ) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(MOVE_WORK_NAME); return }
        val delayMs = computeInitialDelayMillis(morningHour, morningMinute)
        enqueue(context, MOVE_WORK_NAME, MoveReminderWorker::class.java, delayMs)
    }

    /** Called by [MoveReminderWorker] to chain the next occurrence. */
    fun enqueueMoveReminder(context: Context, delayMs: Long) =
        enqueue(context, MOVE_WORK_NAME, MoveReminderWorker::class.java, delayMs)
}
