package com.openfit.mobile.work

import android.content.Context
import androidx.work.WorkerParameters
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.ai.SummaryService
import com.openfit.mobile.data.settings.AppSettings
import com.openfit.mobile.data.settings.SummarySchedule
import com.openfit.mobile.model.HealthSnapshotBundle

class MorningSummaryWorker(context: Context, params: WorkerParameters) : BaseSummaryWorker(context, params) {
    override val workName = WorkScheduler.MORNING_WORK_NAME
    override fun scheduleFor(schedule: AppSettings): SummarySchedule = schedule.morningSleepSummary
    override suspend fun buildSummary(summaryService: SummaryService, bundle: HealthSnapshotBundle, settings: AppSettings): String =
        summaryService.morningSleepSummary(bundle, settings)
    override fun notificationTitle() = "Good morning \u2600\ufe0f"
    override fun notify(container: AppContainer, title: String, body: String) =
        container.notificationHelper.showMorningSummary(title, body)
}
