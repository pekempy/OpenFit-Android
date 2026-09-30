package com.openfit.mobile.data.backup

import android.content.Context
import android.content.Intent
import com.openfit.mobile.data.settings.AppSettings
import com.openfit.mobile.data.settings.DriveBackupSettings
import com.openfit.mobile.data.settings.SettingsRepository
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.Instant

/** Coordinates Drive auth, serialisation, and backup/restore.
 * Drives the UI state reported by [DriveBackupState]. */
class DriveBackupManager(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {
    val authManager = DriveAuthManager(context, DriveTokenStore(context))
    private val client = DriveBackupClient()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun createAuthIntent(settings: DriveBackupSettings): Intent {
        val clientId = settings.clientId.ifBlank {
            // Fall back to Health API client ID if the user hasn't set a separate one.
            null
        } ?: error("No Drive client ID configured")
        return authManager.createAuthIntent(clientId, settings.clientSecret)
    }

    suspend fun handleAuthResult(intent: Intent, settings: DriveBackupSettings) {
        val clientId = settings.clientId.ifBlank { error("No Drive client ID configured") }
        authManager.handleAuthorizationResponse(intent, clientId, settings.clientSecret)
        val email = DriveTokenStore(context).load()?.accountEmail ?: ""
        settingsRepository.updateDriveBackupSettings(
            settings.copy(enabled = true, accountEmail = email)
        )
    }

    suspend fun backup(): BackupResult {
        val settings = settingsRepository.settingsFlow.firstOrNull()
            ?: return BackupResult.Failure("Settings unavailable")
        val driveSettings = settings.driveBackup
        if (!driveSettings.enabled) return BackupResult.Failure("Drive not connected")

        return try {
            val token = authManager.getValidToken(
                clientId = driveSettings.clientId,
                clientSecret = driveSettings.clientSecret,
            )
            val snapshot = buildSnapshot(settings)
            val jsonStr = json.encodeToString(snapshot)
            client.upload(token, jsonStr)

            val now = Instant.now().toString()
            settingsRepository.updateDriveBackupSettings(driveSettings.copy(lastBackupTimeIso = now))
            BackupResult.Success(now)
        } catch (e: Exception) {
            BackupResult.Failure(e.message ?: "Backup failed")
        }
    }

    suspend fun restore(): RestoreResult {
        val settings = settingsRepository.settingsFlow.firstOrNull()
            ?: return RestoreResult.Failure("Settings unavailable")
        val driveSettings = settings.driveBackup
        if (!driveSettings.enabled) return RestoreResult.Failure("Drive not connected")

        return try {
            val token = authManager.getValidToken(
                clientId = driveSettings.clientId,
                clientSecret = driveSettings.clientSecret,
            )
            val jsonStr = client.download(token) ?: return RestoreResult.Failure("No backup found in Drive")
            val snapshot = json.decodeFromString<SettingsBackup>(jsonStr)
            settingsRepository.applyBackup(snapshot)
            RestoreResult.Success(snapshot.backedUpAt)
        } catch (e: Exception) {
            RestoreResult.Failure(e.message ?: "Restore failed")
        }
    }

    suspend fun disconnect() {
        authManager.signOut()
        val settings = settingsRepository.settingsFlow.firstOrNull() ?: return
        settingsRepository.updateDriveBackupSettings(DriveBackupSettings())
    }

    fun isConnected(): Boolean = authManager.isConnected()

    private fun buildSnapshot(settings: AppSettings) = SettingsBackup(
        backedUpAt = Instant.now().toString(),
        goals = settings.goals,
        units = settings.units,
        display = settings.display,
        reminders = settings.reminders,
        personalisation = settings.personalisation,
        selectedAiProvider = settings.selectedAiProvider,
        aiProviders = settings.aiProviders,
        customEndpoints = settings.customEndpoints,
        selectedCustomEndpointId = settings.selectedCustomEndpointId,
        dataSourceKind = settings.dataSourceKind,
        oauthConfig = settings.oauthConfig,
    )
}

sealed interface BackupResult {
    data class Success(val timestampIso: String) : BackupResult
    data class Failure(val reason: String) : BackupResult
}

sealed interface RestoreResult {
    data class Success(val backedUpAtIso: String) : RestoreResult
    data class Failure(val reason: String) : RestoreResult
}
