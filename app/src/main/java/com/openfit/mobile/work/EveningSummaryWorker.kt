package com.openfit.mobile.work

import android.content.Context
import androidx.work.WorkerParameters
import com.openfit.mobile.AppContainer
import com.openfit.mobile.data.ai.SummaryService
import com.openfit.mobile.data.settings.AppSettings
import com.openfit.mobile.data.settings.SummarySchedule
import com.openfit.mobile.model.HealthSnapshotBundle

class EveningSummaryWorker(context: Context, params: WorkerParameters) : BaseSummaryWorker(context, params) {
    override val workName = WorkScheduler.EVENING_WORK_NAME
    override fun scheduleFor(schedule: AppSettings): SummarySchedule = schedule.eveningActivitySummary
    override suspend fun buildSummary(summaryService: SummaryService, bundle: HealthSnapshotBundle, settings: AppSettings): String =
        summaryService.eveningActivitySummary(bundle, settings)
    override fun notificationTitle() = "Today's activity recap"
    override fun notify(container: AppContainer, title: String, body: String) =
        container.notificationHelper.showEveningSummary(title, body)
    override suspend fun saveSummary(container: AppContainer, text: String) =
        container.settingsRepository.saveLastEveningSummary(text)
}
