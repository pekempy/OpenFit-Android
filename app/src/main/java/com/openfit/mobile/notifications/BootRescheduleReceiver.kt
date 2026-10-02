package com.openfit.mobile.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.openfit.mobile.OpenFitApplication
import com.openfit.mobile.work.EveningSummaryWorker
import com.openfit.mobile.work.MorningSummaryWorker
import com.openfit.mobile.work.WorkScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-queues all WorkManager jobs after a device reboot so OEM battery
  * managers that clear WorkManager alarms don't silently kill daily reminders. */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as OpenFitApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            val settings  = container.settingsRepository.settingsFlow.first()
            val reminders = settings.reminders

            // Morning / evening AI summaries
            if (reminders.morningSleepSummary.enabled) {
                WorkScheduler.rescheduleNext(
                    context, WorkScheduler.MORNING_WORK_NAME,
                    reminders.morningSleepSummary.hour, reminders.morningSleepSummary.minute,
                    MorningSummaryWorker::class.java,
                )
            }
            if (reminders.eveningActivitySummary.enabled) {
                WorkScheduler.rescheduleNext(
                    context, WorkScheduler.EVENING_WORK_NAME,
                    reminders.eveningActivitySummary.hour, reminders.eveningActivitySummary.minute,
                    EveningSummaryWorker::class.java,
                )
            }

            // Hydration reminder
            WorkScheduler.scheduleHydrationReminder(
                context      = context,
                enabled      = reminders.hydrationReminder,
                morningHour  = reminders.morningSleepSummary.hour,
                morningMinute = reminders.morningSleepSummary.minute,
            )

            // Move / stand-up reminder
            WorkScheduler.scheduleMoveReminder(
                context      = context,
                enabled      = reminders.moveReminder,
                morningHour  = reminders.morningSleepSummary.hour,
                morningMinute = reminders.morningSleepSummary.minute,
            )
        }
    }
}
