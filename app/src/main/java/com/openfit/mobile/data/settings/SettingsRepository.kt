package com.openfit.mobile.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.openfit.mobile.data.oauth.OAuthConfig
import com.openfit.mobile.model.AiProviderConfig
import com.openfit.mobile.model.AiProviderKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "openfit_settings")

/** All user-configurable app state: units (metric/imperial, kg/st/lbs),
 * appearance, connections (Health Connect, OAuth, AI coach), reminders, and health goals. */
class SettingsRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val settingsFlow: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        val rawReminders = prefs[KEY_REMINDERS]?.let { runCatching { json.decodeFromString<AppReminderSettings>(it) }.getOrNull() }
        val rawMorning = prefs[KEY_MORNING_SUMMARY]?.let { runCatching { json.decodeFromString<SummarySchedule>(it) }.getOrNull() }
        val rawEvening = prefs[KEY_EVENING_SUMMARY]?.let { runCatching { json.decodeFromString<SummarySchedule>(it) }.getOrNull() }
        val resolvedReminders = rawReminders ?: AppReminderSettings(
            morningSleepSummary = rawMorning ?: SummarySchedule(enabled = false, hour = 7, minute = 30),
            eveningActivitySummary = rawEvening ?: SummarySchedule(enabled = false, hour = 21, minute = 0),
        )

        val migratedCustomEndpoints = prefs[KEY_CUSTOM_ENDPOINTS]?.let {
            runCatching { json.decodeFromString<List<com.openfit.mobile.model.CustomEndpointProfile>>(it) }.getOrNull()
        } ?: run {
            // One-off migration: earlier builds stored a single CUSTOM slot
            // inside aiProviders. Wrap it into the new named-profile list so
            // nobody loses a working endpoint on upgrade.
            val legacyCustom = prefs[KEY_AI_PROVIDERS]?.let {
                runCatching { json.decodeFromString<Map<AiProviderKind, AiProviderConfig>>(it) }.getOrNull()
            }?.get(AiProviderKind.CUSTOM)
            if (legacyCustom != null && legacyCustom.baseUrl.isNotBlank()) {
                listOf(
                    com.openfit.mobile.model.CustomEndpointProfile(
                        name = "Default",
                        apiKey = legacyCustom.apiKey,
                        model = legacyCustom.model,
                        baseUrl = legacyCustom.baseUrl,
                        chatPath = legacyCustom.chatPath,
                        sessionId = legacyCustom.sessionId,
                    )
                )
            } else {
                emptyList()
            }
        }

        AppSettings(
            dataSourceKind = prefs[KEY_DATA_SOURCE]?.let { runCatching { HealthDataSourceKind.valueOf(it) }.getOrNull() }
                ?: HealthDataSourceKind.HEALTH_CONNECT,
            oauthConfig = prefs[KEY_OAUTH]?.let { runCatching { json.decodeFromString<OAuthConfig>(it) }.getOrNull() } ?: OAuthConfig(),
            aiProviders = prefs[KEY_AI_PROVIDERS]?.let {
                runCatching { json.decodeFromString<Map<AiProviderKind, AiProviderConfig>>(it) }.getOrNull()
            } ?: emptyMap(),
            selectedAiProvider = prefs[KEY_SELECTED_AI]?.let { runCatching { AiProviderKind.valueOf(it) }.getOrNull() },
            customEndpoints = migratedCustomEndpoints,
            selectedCustomEndpointId = prefs[KEY_SELECTED_CUSTOM_ENDPOINT] ?: migratedCustomEndpoints.firstOrNull()?.id,
            personalisation = prefs[KEY_PERSONALISATION]?.let {
                runCatching { json.decodeFromString<AiPersonalisationSettings>(it) }.getOrNull()
            } ?: AiPersonalisationSettings(),
            morningSleepSummary = resolvedReminders.morningSleepSummary,
            eveningActivitySummary = resolvedReminders.eveningActivitySummary,
            display = prefs[KEY_DISPLAY]?.let { runCatching { json.decodeFromString<AppDisplaySettings>(it) }.getOrNull() } ?: AppDisplaySettings(),
            units = prefs[KEY_UNITS]?.let { runCatching { json.decodeFromString<AppUnitSettings>(it) }.getOrNull() } ?: AppUnitSettings(),
            reminders = resolvedReminders,
            goals = prefs[KEY_GOALS]?.let { runCatching { json.decodeFromString<UserHealthGoals>(it) }.getOrNull() } ?: UserHealthGoals(),
        )
    }

    suspend fun updateGoals(goals: UserHealthGoals) {
        context.settingsDataStore.edit { it[KEY_GOALS] = json.encodeToString(goals) }
    }

    suspend fun updatePersonalisation(personalisation: AiPersonalisationSettings) {
        context.settingsDataStore.edit { it[KEY_PERSONALISATION] = json.encodeToString(personalisation) }
    }

    suspend fun updateUnits(units: AppUnitSettings) {
        context.settingsDataStore.edit { it[KEY_UNITS] = json.encodeToString(units) }
    }

    suspend fun updateReminders(reminders: AppReminderSettings) {
        context.settingsDataStore.edit {
            it[KEY_REMINDERS] = json.encodeToString(reminders)
            it[KEY_MORNING_SUMMARY] = json.encodeToString(reminders.morningSleepSummary)
            it[KEY_EVENING_SUMMARY] = json.encodeToString(reminders.eveningActivitySummary)
        }
    }

    suspend fun setDataSourceKind(kind: HealthDataSourceKind) {
        context.settingsDataStore.edit { it[KEY_DATA_SOURCE] = kind.name }
    }

    suspend fun updateDisplaySettings(display: AppDisplaySettings) {
        context.settingsDataStore.edit { it[KEY_DISPLAY] = json.encodeToString(display) }
    }

    suspend fun updateOAuthConfig(config: OAuthConfig) {
        context.settingsDataStore.edit { it[KEY_OAUTH] = json.encodeToString(config) }
    }

    suspend fun updateAiProviderConfig(kind: AiProviderKind, config: AiProviderConfig) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_AI_PROVIDERS]?.let {
                runCatching { json.decodeFromString<Map<AiProviderKind, AiProviderConfig>>(it) }.getOrNull()
            } ?: emptyMap()
            prefs[KEY_AI_PROVIDERS] = json.encodeToString(current + (kind to config))
        }
    }

    suspend fun setSelectedAiProvider(kind: AiProviderKind?) {
        context.settingsDataStore.edit { prefs ->
            if (kind == null) prefs.remove(KEY_SELECTED_AI) else prefs[KEY_SELECTED_AI] = kind.name
        }
    }

    /** Saves (inserts or updates by id) one named custom/self-hosted AI
     * endpoint profile. Fully user-defined - no assumptions about which
     * agent/service is behind it. */
    suspend fun saveCustomEndpoint(profile: com.openfit.mobile.model.CustomEndpointProfile) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_ENDPOINTS]?.let {
                runCatching { json.decodeFromString<List<com.openfit.mobile.model.CustomEndpointProfile>>(it) }.getOrNull()
            } ?: emptyList()
            val updated = if (current.any { it.id == profile.id }) {
                current.map { if (it.id == profile.id) profile else it }
            } else {
                current + profile
            }
            prefs[KEY_CUSTOM_ENDPOINTS] = json.encodeToString(updated)
            if (prefs[KEY_SELECTED_CUSTOM_ENDPOINT] == null) {
                prefs[KEY_SELECTED_CUSTOM_ENDPOINT] = profile.id
            }
        }
    }

    suspend fun deleteCustomEndpoint(id: String) {
        context.settingsDataStore.edit { prefs ->
            val current = prefs[KEY_CUSTOM_ENDPOINTS]?.let {
                runCatching { json.decodeFromString<List<com.openfit.mobile.model.CustomEndpointProfile>>(it) }.getOrNull()
            } ?: emptyList()
            val updated = current.filterNot { it.id == id }
            prefs[KEY_CUSTOM_ENDPOINTS] = json.encodeToString(updated)
            if (prefs[KEY_SELECTED_CUSTOM_ENDPOINT] == id) {
                val next = updated.firstOrNull()?.id
                if (next == null) prefs.remove(KEY_SELECTED_CUSTOM_ENDPOINT) else prefs[KEY_SELECTED_CUSTOM_ENDPOINT] = next
            }
        }
    }

    suspend fun setSelectedCustomEndpoint(id: String) {
        context.settingsDataStore.edit { prefs -> prefs[KEY_SELECTED_CUSTOM_ENDPOINT] = id }
    }

    suspend fun updateMorningSleepSummary(schedule: SummarySchedule) {
        context.settingsDataStore.edit { prefs ->
            itMorning(prefs, schedule)
        }
    }

    private fun itMorning(prefs: androidx.datastore.preferences.core.MutablePreferences, schedule: SummarySchedule) {
        prefs[KEY_MORNING_SUMMARY] = json.encodeToString(schedule)
        val currentReminders = prefs[KEY_REMINDERS]?.let { runCatching { json.decodeFromString<AppReminderSettings>(it) }.getOrNull() } ?: AppReminderSettings()
        prefs[KEY_REMINDERS] = json.encodeToString(currentReminders.copy(morningSleepSummary = schedule))
    }

    suspend fun updateEveningActivitySummary(schedule: SummarySchedule) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_EVENING_SUMMARY] = json.encodeToString(schedule)
            val currentReminders = prefs[KEY_REMINDERS]?.let { runCatching { json.decodeFromString<AppReminderSettings>(it) }.getOrNull() } ?: AppReminderSettings()
            prefs[KEY_REMINDERS] = json.encodeToString(currentReminders.copy(eveningActivitySummary = schedule))
        }
    }

    /** Restore a full [AppSettings] snapshot in one shot — used by backup import. */
    suspend fun applyFullSettings(s: AppSettings) {
        updateGoals(s.goals)
        updateUnits(s.units)
        updateDisplaySettings(s.display)
        updateReminders(s.reminders)
        updatePersonalisation(s.personalisation)
        setSelectedAiProvider(s.selectedAiProvider)
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_AI_PROVIDERS] = json.encodeToString(s.aiProviders)
            prefs[KEY_CUSTOM_ENDPOINTS] = json.encodeToString(s.customEndpoints)
            s.selectedCustomEndpointId?.let { prefs[KEY_SELECTED_CUSTOM_ENDPOINT] = it }
            prefs[KEY_DATA_SOURCE] = s.dataSourceKind.name
            prefs[KEY_OAUTH] = json.encodeToString(s.oauthConfig)
        }
    }

    private companion object {
        val KEY_OAUTH = stringPreferencesKey("oauth_config")
        val KEY_AI_PROVIDERS = stringPreferencesKey("ai_providers")
        val KEY_SELECTED_AI = stringPreferencesKey("selected_ai_provider")
        val KEY_CUSTOM_ENDPOINTS = stringPreferencesKey("custom_ai_endpoints")
        val KEY_SELECTED_CUSTOM_ENDPOINT = stringPreferencesKey("selected_custom_ai_endpoint")
        val KEY_PERSONALISATION = stringPreferencesKey("ai_personalisation")
        val KEY_MORNING_SUMMARY = stringPreferencesKey("morning_sleep_summary")
        val KEY_EVENING_SUMMARY = stringPreferencesKey("evening_activity_summary")
        val KEY_DATA_SOURCE = stringPreferencesKey("data_source_kind")
        val KEY_DISPLAY = stringPreferencesKey("display_settings")
        val KEY_UNITS = stringPreferencesKey("unit_settings")
        val KEY_REMINDERS = stringPreferencesKey("reminder_settings")
        val KEY_GOALS = stringPreferencesKey("health_goals")
    }
}
