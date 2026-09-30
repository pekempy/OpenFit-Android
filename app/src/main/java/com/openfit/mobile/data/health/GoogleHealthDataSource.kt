package com.openfit.mobile.data.health

import com.openfit.mobile.data.oauth.GoogleAuthManager
import com.openfit.mobile.data.settings.SettingsRepository
import com.openfit.mobile.model.HealthSnapshotBundle
import kotlinx.coroutines.flow.first

/** Adapts the existing OAuth-based [HealthRepository] to the shared
 * [HealthDataSource] contract, resolving the current OAuth client config
 * from settings on each sync rather than requiring the caller to pass it. */
class GoogleHealthDataSource(
    private val repository: HealthRepository,
    private val authManager: GoogleAuthManager,
    private val settingsRepository: SettingsRepository,
) : HealthDataSource {
    override suspend fun sync(selectedDate: String): HealthSnapshotBundle {
        val config = settingsRepository.settingsFlow.first().oauthConfig
        return repository.sync(config, selectedDate)
    }

    override suspend fun isConnected(): Boolean = authManager.isConnected()
}
