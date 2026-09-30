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

/** Some OEMs (aggressive battery managers on certain Android skins) clear
 * WorkManager's persisted alarms across a reboot even though WorkManager is
 * supposed to survive it on its own; re-enqueuing both summary jobs here is
 * a cheap belt-and-braces fix. */
class BootRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as OpenFitApplication).container
        CoroutineScope(Dispatchers.IO).launch {
            val settings = container.settingsRepository.settingsFlow.first()
            if (settings.morningSleepSummary.enabled) {
                WorkScheduler.rescheduleNext(
                    context, WorkScheduler.MORNING_WORK_NAME,
                    settings.morningSleepSummary.hour, settings.morningSleepSummary.minute,
                    MorningSummaryWorker::class.java,
                )
            }
            if (settings.eveningActivitySummary.enabled) {
                WorkScheduler.rescheduleNext(
                    context, WorkScheduler.EVENING_WORK_NAME,
                    settings.eveningActivitySummary.hour, settings.eveningActivitySummary.minute,
                    EveningSummaryWorker::class.java,
                )
            }
        }
    }
}
