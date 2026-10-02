package com.openfit.mobile.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.openfit.mobile.OpenFitApplication
import com.openfit.mobile.data.settings.SummarySchedule
import com.openfit.mobile.data.health.BundleCache
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/** Shared logic for the two daily summary workers: sync today's Google
 * Health data, ask the selected AI provider for a short recap, push a
 * notification, then reschedule tomorrow's run regardless of outcome - a
 * transient failure (network blip, AI provider hiccup) shouldn't silently
 * end the daily habit. */
abstract class BaseSummaryWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    protected abstract val workName: String
    protected abstract fun scheduleFor(schedule: com.openfit.mobile.data.settings.AppSettings): SummarySchedule
    protected abstract suspend fun buildSummary(
        summaryService: com.openfit.mobile.data.ai.SummaryService,
        bundle: com.openfit.mobile.model.HealthSnapshotBundle,
        settings: com.openfit.mobile.data.settings.AppSettings,
    ): String
    protected abstract fun notificationTitle(): String
    protected abstract fun notify(container: com.openfit.mobile.AppContainer, title: String, body: String)

    override suspend fun doWork(): Result {
        val container = (applicationContext as OpenFitApplication).container
        return try {
            val settings = container.settingsRepository.settingsFlow.first()
            val schedule = scheduleFor(settings)
            if (schedule.enabled) {
                // On Android 14+, Health Connect requires READ_HEALTH_DATA_IN_BACKGROUND
                // for WorkManager tasks. Without it every readRecords() call throws
                // SecurityException (swallowed to emptyList), producing "no data" summaries.
                // Detect this early and skip rather than sending a wrong notification.
                val needsBgPermission = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                    settings.dataSourceKind == com.openfit.mobile.data.settings.HealthDataSourceKind.HEALTH_CONNECT
                val hasBgPermission = !needsBgPermission ||
                    androidx.core.content.ContextCompat.checkSelfPermission(
                        applicationContext,
                        com.openfit.mobile.data.healthconnect.HealthConnectManager.BACKGROUND_READ_PERMISSION,
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                if (!hasBgPermission) {
                    // Open the app and re-trigger the permission grant from there.
                    notify(container, notificationTitle(),
                        "OpenFit needs background Health Connect access to generate your summary. Tap to grant it in the app.")
                    return Result.failure()
                }
                // Use whichever data source the user configured (Health Connect
                // or Google Health API) — not the raw cloud HealthRepository.
                val dataSource = container.activeHealthDataSource(settings.dataSourceKind)
                val bundle = dataSource.sync(LocalDate.now().toString())
                // Cache so the app shows data immediately when opened from the notification.
                BundleCache.save(applicationContext, bundle)
                val summaryText = buildSummary(container.summaryService, bundle, settings)
                // Persist so the Coach tab can surface it even when the
                // notification permission isn't granted or the notification is missed.
                saveSummary(container, summaryText)
                notify(container, notificationTitle(), summaryText)
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        } finally {
            // Reschedule tomorrow regardless of success/failure/disabled-at-run-time,
            // so re-enabling later in Settings doesn't require a manual re-trigger.
            val settings = runCatching { container.settingsRepository.settingsFlow.first() }.getOrNull()
            val schedule = settings?.let(::scheduleFor)
            if (schedule != null && schedule.enabled) {
                WorkScheduler.rescheduleNext(applicationContext, workName, schedule.hour, schedule.minute, this::class.java)
            }
        }
    }

    /** Persist the generated summary text so it can be surfaced in-app. */
    protected abstract suspend fun saveSummary(container: com.openfit.mobile.AppContainer, text: String)
}

