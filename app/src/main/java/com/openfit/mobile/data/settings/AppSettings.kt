package com.openfit.mobile.data.settings

import com.openfit.mobile.data.oauth.OAuthConfig
import com.openfit.mobile.model.AiProviderConfig
import com.openfit.mobile.model.AiProviderKind
import com.openfit.mobile.model.CustomEndpointProfile
import kotlinx.serialization.Serializable

@Serializable
data class SummarySchedule(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 30,
)

@Serializable
enum class HealthDataSourceKind { HEALTH_CONNECT, GOOGLE_HEALTH_API }

@Serializable
enum class AppThemeMode { SYSTEM, LIGHT, DARK }

@Serializable
enum class AccentColorTheme {
    TEAL, PIXEL_BLUE, CORAL, EMERALD, VIOLET, CUSTOM
}

@Serializable
data class AppDisplaySettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val useDynamicColor: Boolean = true,
    val accentColor: AccentColorTheme = AccentColorTheme.TEAL,
    /** Hex colour (e.g. "#6750A4") used when accentColor == CUSTOM. */
    val customAccentColorHex: String? = null,
    /** Whether the Body tab shows the reproductive health event list
     * (menstruation, cervical mucus, ovulation tests, etc). On by default
     * since it's only populated when you actually have that data in
     * Health Connect; off hides the section entirely regardless of data. */
    val showReproductiveHealth: Boolean = true,
)

@Serializable
enum class UnitSystem { METRIC, IMPERIAL }

@Serializable
enum class WeightUnit { KG, LBS, STONE }

@Serializable
enum class DistanceUnit { KILOMETERS, MILES }

@Serializable
enum class WaterUnit { LITERS, MILLILITERS, FLUID_OUNCES }

@Serializable
enum class EnergyUnit { KCAL, KILOJOULES }

@Serializable
enum class TemperatureUnit { CELSIUS, FAHRENHEIT }

@Serializable
data class AppUnitSettings(
    val system: UnitSystem = UnitSystem.METRIC,
    val weightUnit: WeightUnit = WeightUnit.KG,
    val distanceUnit: DistanceUnit = DistanceUnit.KILOMETERS,
    val waterUnit: WaterUnit = WaterUnit.LITERS,
    val energyUnit: EnergyUnit = EnergyUnit.KCAL,
    val temperatureUnit: TemperatureUnit = TemperatureUnit.CELSIUS,
)

@Serializable
data class AppReminderSettings(
    val morningSleepSummary: SummarySchedule = SummarySchedule(enabled = false, hour = 7, minute = 30),
    val eveningActivitySummary: SummarySchedule = SummarySchedule(enabled = false, hour = 21, minute = 0),
    val hydrationReminder: Boolean = false,
    val hydrationIntervalHours: Int = 2,
    val moveReminder: Boolean = false,
)

@Serializable
data class UserHealthGoals(
    val stepGoal: Int = 10_000,
    val sleepMinutesGoal: Int = 480,
    val waterLitersGoal: Double = 2.5,
    val caloriesGoal: Int = 2_000,
    val activeMinutesGoal: Int = 30,
    val distanceKmGoal: Double = 5.0,
    val floorsGoal: Int = 10,
    val targetWeightKg: Double? = null,
    /** Optional - used only to compute heart-rate zone breakdown on the
     * Health tab. Never estimated/guessed from age; blank until the user
     * sets it themselves. */
    val maxHeartRateBpm: Int? = null,
)

/** Optional, entirely user-authored personalisation applied to every AI
 * request (summaries and Coach chat alike), for every provider kind - not
 * just self-hosted ones. Both fields are blank by default and nothing about
 * any specific agent/persona is assumed; the app only ever repeats back
 * what the user typed here. */
@Serializable
data class AiPersonalisationSettings(
    val userDisplayName: String = "",
    val personaInstructions: String = "",
)

@Serializable
data class AppSettings(
    val dataSourceKind: HealthDataSourceKind = HealthDataSourceKind.HEALTH_CONNECT,
    val oauthConfig: OAuthConfig = OAuthConfig(),
    val aiProviders: Map<AiProviderKind, AiProviderConfig> = emptyMap(),
    val selectedAiProvider: AiProviderKind? = null,
    /** Saved CUSTOM/self-hosted endpoint profiles, fully user-named and
     * user-edited - none of this is hard-coded to any specific agent. */
    val customEndpoints: List<CustomEndpointProfile> = emptyList(),
    val selectedCustomEndpointId: String? = null,
    val personalisation: AiPersonalisationSettings = AiPersonalisationSettings(),
    val morningSleepSummary: SummarySchedule = SummarySchedule(enabled = false, hour = 7, minute = 30),
    val eveningActivitySummary: SummarySchedule = SummarySchedule(enabled = false, hour = 21, minute = 0),
    val display: AppDisplaySettings = AppDisplaySettings(),
    val units: AppUnitSettings = AppUnitSettings(),
    val reminders: AppReminderSettings = AppReminderSettings(),
    val goals: UserHealthGoals = UserHealthGoals(),
    /** Last AI-generated summary texts — persisted so the Coach tab can
     * surface them even when the notification permission isn't granted. */
    val lastMorningSummary: String? = null,
    val lastEveningSummary: String? = null,
) {
    val activeCustomEndpoint: CustomEndpointProfile?
        get() = customEndpoints.find { it.id == selectedCustomEndpointId } ?: customEndpoints.firstOrNull()

    val activeAiConfig: AiProviderConfig?
        get() = when (selectedAiProvider) {
            null -> null
            AiProviderKind.CUSTOM -> activeCustomEndpoint?.toProviderConfig()
            else -> aiProviders[selectedAiProvider]
        }

    val isCoachConfigured: Boolean
        get() = activeAiConfig?.isConfigured == true
}
