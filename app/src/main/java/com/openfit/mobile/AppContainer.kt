package com.openfit.mobile

import android.content.Context
import com.openfit.mobile.data.ai.SummaryService
import com.openfit.mobile.data.exercise.ExerciseLabelStore
import com.openfit.mobile.data.health.GoogleHealthDataSource
import com.openfit.mobile.data.health.HealthApiClient
import com.openfit.mobile.data.health.HealthDataSource
import com.openfit.mobile.data.health.HealthRepository
import com.openfit.mobile.data.healthconnect.HealthConnectRepository
import com.openfit.mobile.data.oauth.GoogleAuthManager
import com.openfit.mobile.data.oauth.TokenStore
import com.openfit.mobile.data.settings.HealthDataSourceKind
import com.openfit.mobile.data.settings.SettingsRepository
import com.openfit.mobile.notifications.NotificationHelper

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val settingsRepository = SettingsRepository(context)
    val exerciseLabelStore = ExerciseLabelStore(context)
    val tokenStore = TokenStore(context)
    val manualLogStore = com.openfit.mobile.data.logs.ManualLogStore(context)
    val authManager = GoogleAuthManager(context, tokenStore)
    val healthRepository = HealthRepository(HealthApiClient.create(), authManager)
    val googleHealthDataSource = GoogleHealthDataSource(healthRepository, authManager, settingsRepository)
    val healthConnectRepository = HealthConnectRepository(appContext, manualLogStore, settingsRepository)
    val summaryService = SummaryService()
    val notificationHelper = NotificationHelper(context)

    fun activeHealthDataSource(kind: HealthDataSourceKind): HealthDataSource = when (kind) {
        HealthDataSourceKind.HEALTH_CONNECT -> healthConnectRepository
        HealthDataSourceKind.GOOGLE_HEALTH_API -> googleHealthDataSource
    }
}
