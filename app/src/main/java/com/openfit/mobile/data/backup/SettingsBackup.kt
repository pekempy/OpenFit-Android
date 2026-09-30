package com.openfit.mobile.data.backup

import com.openfit.mobile.data.oauth.OAuthConfig
import com.openfit.mobile.data.settings.AppDisplaySettings
import com.openfit.mobile.data.settings.AppReminderSettings
import com.openfit.mobile.data.settings.AppUnitSettings
import com.openfit.mobile.data.settings.AiPersonalisationSettings
import com.openfit.mobile.data.settings.HealthDataSourceKind
import com.openfit.mobile.data.settings.UserHealthGoals
import com.openfit.mobile.model.AiProviderConfig
import com.openfit.mobile.model.AiProviderKind
import com.openfit.mobile.model.CustomEndpointProfile
import kotlinx.serialization.Serializable

/** Point-in-time snapshot of every user-configurable setting, written
 * to the Drive App Data folder as a JSON file.
 *
 * Drive OAuth tokens, Health Connect session state, and local DataStore
 * keys are intentionally excluded — those are device-specific and must
 * not roam across devices or accounts. */
@Serializable
data class SettingsBackup(
    val schemaVersion: Int = 1,
    val backedUpAt: String = "",
    val goals: UserHealthGoals = UserHealthGoals(),
    val units: AppUnitSettings = AppUnitSettings(),
    val display: AppDisplaySettings = AppDisplaySettings(),
    val reminders: AppReminderSettings = AppReminderSettings(),
    val personalisation: AiPersonalisationSettings = AiPersonalisationSettings(),
    val selectedAiProvider: AiProviderKind? = null,
    val aiProviders: Map<AiProviderKind, AiProviderConfig> = emptyMap(),
    val customEndpoints: List<CustomEndpointProfile> = emptyList(),
    val selectedCustomEndpointId: String? = null,
    val dataSourceKind: HealthDataSourceKind = HealthDataSourceKind.HEALTH_CONNECT,
    val oauthConfig: OAuthConfig = OAuthConfig(),
)
