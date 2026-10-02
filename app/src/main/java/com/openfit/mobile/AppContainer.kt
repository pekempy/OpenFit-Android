package com.openfit.mobile

import android.content.Context
import com.openfit.mobile.data.ai.SummaryService
import com.openfit.mobile.data.backup.DriveAuth
import com.openfit.mobile.data.backup.DriveSync
import com.openfit.mobile.data.backup.SettingsEnvelope
import com.openfit.mobile.data.exercise.ExerciseLabelStore
import com.openfit.mobile.data.health.GoogleHealthDataSource
import com.openfit.mobile.data.health.HealthApiClient
import com.openfit.mobile.data.health.HealthDataSource
import com.openfit.mobile.data.health.HealthRepository
import com.openfit.mobile.data.health.FallbackHealthDataSource
import com.openfit.mobile.data.healthconnect.HealthConnectRepository
import com.openfit.mobile.data.oauth.GoogleAuthManager
import com.openfit.mobile.data.oauth.TokenStore
import com.openfit.mobile.data.settings.HealthDataSourceKind
import com.openfit.mobile.data.settings.SettingsRepository
import com.openfit.mobile.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    // ── Google Drive backup ────────────────────────────────────────────────

    /** Application-scoped coroutine scope for Drive operations. Lives as long as
     *  the app process; no cancellation needed since AppContainer is a singleton. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    val driveAuth = DriveAuth(appContext)

    val driveAccount: StateFlow<String?> = settingsRepository.driveAccountFlow
        .stateIn(scope, SharingStarted.Eagerly, null)

    val driveLastSyncRun: StateFlow<String?> = settingsRepository.driveLastSyncRunFlow
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    /** Last sync result: human-readable message + success flag. */
    private val _syncStatus = MutableStateFlow<Pair<String, Boolean>?>(null)
    val syncStatus: StateFlow<Pair<String, Boolean>?> = _syncStatus.asStateFlow()

    /**
     * Timestamp of the envelope we last agreed on with Drive, persisted across
     * restarts. Loaded from DataStore in [resumeDrive] and updated after every
     * successful sync so the next session can resolve newest-wins correctly.
     */
    private var lastSyncedAt: String? = null

    /**
     * Bring settings and Drive into agreement: newest wins.
     *
     * Downloads the remote envelope and compares its [SettingsEnvelope.updatedAt]
     * to [lastSyncedAt]. If the remote is newer, settings are restored from it.
     * Otherwise the current local settings are pushed up.
     */
    fun syncWithDrive(token: String) = scope.launch {
        _syncing.value = true
        _syncStatus.value = null
        try {
            val remote = DriveSync.download(token)
            if (remote != null &&
                DriveSync.parseIso(remote.updatedAt) > DriveSync.parseIso(lastSyncedAt)
            ) {
                // Remote is newer — restore it.
                settingsRepository.applyFullSettings(remote.settings)
                lastSyncedAt = remote.updatedAt
                settingsRepository.setDriveLastSyncedAt(remote.updatedAt)
                settingsRepository.setDriveLastSyncRun(java.time.Instant.now().toString())
                _syncStatus.value = "Settings restored from Drive" to true
            } else {
                // Local is current or Drive has no backup yet — push.
                val localSettings = settingsRepository.settingsFlow.first()
                val now = java.time.Instant.now().toString()
                val envelope = SettingsEnvelope(updatedAt = now, settings = localSettings)
                DriveSync.upload(token, envelope)
                lastSyncedAt = now
                settingsRepository.setDriveLastSyncedAt(now)
                settingsRepository.setDriveLastSyncRun(now)
                _syncStatus.value = "Settings backed up to Drive" to true
            }
        } catch (e: Exception) {
            _syncStatus.value = "Sync failed: ${e.message}" to false
        } finally {
            _syncing.value = false
        }
    }

    /** Persist the connection so the next launch can resume silently. */
    fun rememberDriveConnection() = scope.launch {
        settingsRepository.setDriveAccount(driveAuth.signedInAs?.email ?: "connected")
    }

    /** Revoke the in-memory token and clear the persisted connection. */
    fun forgetDriveConnection() = scope.launch {
        settingsRepository.setDriveAccount(null)
        settingsRepository.setDriveLastSyncedAt(null)
        lastSyncedAt = null
        driveAuth.signOut()
        _syncStatus.value = null
    }

    /**
     * Silently re-acquire a Drive token on startup.
     *
     * Only runs if a previous connection was saved. Play Services returns a token
     * without user interaction when consent is still in force; if it has lapsed
     * the Settings screen will offer to reconnect.
     */
    fun resumeDrive() = scope.launch {
        if (settingsRepository.driveAccountFlow.first() == null) return@launch
        lastSyncedAt = settingsRepository.driveLastSyncedAtFlow.first()
        try {
            when (val step = driveAuth.begin()) {
                is DriveAuth.Step.Token -> syncWithDrive(step.value)
                else -> Unit // Consent lapsed; Settings will offer reconnect.
            }
        } catch (_: Exception) {}
    }

    // ── Health data source ─────────────────────────────────────────────────
    //
    // The setting controls which source is PREFERRED (primary), not which is
    // the only one used.  When both Health Connect and the Google Health API
    // are connected, both are queried in parallel and their results merged
    // field-by-field: primary wins for every non-null value; the secondary
    // fills any gaps.  If only one source is connected it is used exclusively.

    fun activeHealthDataSource(kind: HealthDataSourceKind): HealthDataSource =
        when (kind) {
            HealthDataSourceKind.HEALTH_CONNECT ->
                FallbackHealthDataSource(
                    primary  = healthConnectRepository,
                    fallback = googleHealthDataSource,
                )
            HealthDataSourceKind.GOOGLE_HEALTH_API ->
                FallbackHealthDataSource(
                    primary  = googleHealthDataSource,
                    fallback = healthConnectRepository,
                )
        }
}
