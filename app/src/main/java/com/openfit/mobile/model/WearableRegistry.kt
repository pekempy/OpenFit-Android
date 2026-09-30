package com.openfit.mobile.model

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.openfit.mobile.R

enum class WearableCategory(val displayName: String) {
    SMART_WATCH("Smart Watch"),
    FITNESS_TRACKER("Fitness Tracker"),
    SMART_RING("Smart Ring"),
    SMART_SCALE("Smart Scale"),
    SMARTPHONE("Smartphone"),
    MANUAL_ENTRY("Manual Entry"),
    GENERIC_WEARABLE("Wearable Sensor"),
}

data class DeviceContribution(
    val deviceId: String,
    val deviceName: String,
    val category: WearableCategory,
    val sensorDescription: String,
    @DrawableRes val imageRes: Int? = null,
    val icon: ImageVector,
    val isContributing: Boolean,
    val batteryPercent: Int? = null,
)

data class MetricAttribution(
    val metricKey: String,
    val contributingDevices: List<DeviceContribution>,
    val summaryText: String,
)

object WearableRegistry {

    fun resolveCategory(name: String): WearableCategory {
        val lower = name.lowercase()
        return when {
            "ring" in lower || "oura" in lower || "ultrahuman" in lower -> WearableCategory.SMART_RING
            "watch" in lower || "wear os" in lower || "garmin" in lower -> WearableCategory.SMART_WATCH
            "air" in lower || "band" in lower || "tracker" in lower || "charge" in lower || "inspire" in lower || "whoop" in lower -> WearableCategory.FITNESS_TRACKER
            "scale" in lower || "aria" in lower || "withings body" in lower -> WearableCategory.SMART_SCALE
            "phone" in lower || "pixel" in lower || "galaxy s" in lower -> WearableCategory.SMARTPHONE
            "manual" in lower || "quick log" in lower -> WearableCategory.MANUAL_ENTRY
            else -> WearableCategory.GENERIC_WEARABLE
        }
    }

    @DrawableRes
    fun imageFor(name: String): Int? {
        val lower = name.lowercase()
        return when {
            "air" in lower || ("fitbit" in lower && "watch" !in lower) -> R.drawable.device_fitbit_air
            "pixel watch" in lower -> R.drawable.device_pixel_watch_4
            "pixel" in lower -> R.drawable.device_pixel_10_pro_xl
            else -> null
        }
    }

    fun iconFor(category: WearableCategory): ImageVector {
        return when (category) {
            WearableCategory.SMART_WATCH -> Icons.Filled.Watch
            WearableCategory.FITNESS_TRACKER -> Icons.Filled.DirectionsRun
            WearableCategory.SMART_RING -> Icons.Filled.RadioButtonChecked
            WearableCategory.SMART_SCALE -> Icons.Filled.MonitorWeight
            WearableCategory.SMARTPHONE -> Icons.Filled.Smartphone
            WearableCategory.MANUAL_ENTRY -> Icons.Filled.EditNote
            WearableCategory.GENERIC_WEARABLE -> Icons.Filled.Sensors
        }
    }

    /** Maps a [MetricDetail] title to the signal name(s) recorded against each
     * [PairedDevice] in [HealthConnectRepository] - pure vocabulary
     * translation between two independently-worded naming schemes, nothing
     * device- or brand-specific. */
    private val METRIC_TITLE_TO_SIGNALS: Map<String, List<String>> = mapOf(
        "heart rate variability (hrv)" to listOf("HRV"),
        "blood oxygen (spo2)" to listOf("SpO2"),
        "resting heart rate (rhr)" to listOf("Resting HR", "Heart Rate"),
        "breathing rate" to listOf("Breathing"),
        "skin temperature" to listOf("Skin Temp"),
        "steps & movement" to listOf("Steps"),
        "sleep duration & quality" to listOf("Sleep"),
        "total energy burn" to listOf("Calories"),
        "distance" to listOf("Distance"),
        "floors climbed" to listOf("Floors"),
        "active & zone minutes" to listOf("Calories", "Workouts"),
        "weight" to listOf("Weight"),
        "body fat percentage" to listOf("Body Fat"),
        "daily hydration" to listOf("Hydration"),
        "cardio fitness (vo2 max)" to listOf("VO2 Max"),
    )

    /** Which of the user's real paired devices actually contributed to a
     * given metric today - driven entirely by [PairedDevice.signals], which
     * [HealthConnectRepository] populates from each record's real Health
     * Connect metadata. No device name/brand is special-cased: any device,
     * however unfamiliar, shows up correctly here as long as it wrote a
     * real Health Connect record for that metric. */
    fun getAttribution(metricTitle: String, devices: List<PairedDevice>): MetricAttribution {
        val key = metricTitle.lowercase().trim()
        val candidateSignals = METRIC_TITLE_TO_SIGNALS[key] ?: listOf(metricTitle)

        val contributing = devices.filter { dev ->
            dev.signals.any { signal -> candidateSignals.any { candidate -> signal.equals(candidate, ignoreCase = true) } }
        }.map { dev ->
            val name = dev.deviceType ?: dev.deviceVersion ?: "Connected device"
            val category = resolveCategory(name)
            DeviceContribution(
                deviceId = dev.id,
                deviceName = name,
                category = category,
                sensorDescription = "${dev.deviceVersion ?: "Device"} synced via Health Connect",
                imageRes = imageFor(name),
                icon = iconFor(category),
                isContributing = true,
                batteryPercent = dev.batteryLevelPercent,
            )
        }

        val summary = when {
            contributing.size == 1 -> "${contributing.first().deviceName} is the exclusive data source."
            contributing.size > 1 ->
                "Contributed simultaneously by ${contributing.size} devices (${contributing.joinToString(", ") { it.deviceName }}) and deduplicated via Google Health Connect."
            else -> "Synchronised via Google Health Connect from verified wearable sensors."
        }

        return MetricAttribution(
            metricKey = key,
            contributingDevices = contributing,
            summaryText = summary,
        )
    }
}
