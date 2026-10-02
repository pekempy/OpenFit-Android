package com.openfit.mobile.model

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import com.openfit.mobile.R

enum class WearableCategory {
    SMART_WATCH, FITNESS_BAND, SMART_RING,
    CHEST_STRAP, SCALE, PHONE, GENERIC_WEARABLE, UNKNOWN
}

data class DeviceContribution(
    val deviceName: String,
    @DrawableRes val imageRes: Int? = null,
    val icon: ImageVector,
    val batteryPercent: Int? = null,
    val sensorDescription: String,
)

data class MetricAttribution(
    val contributingDevices: List<DeviceContribution>,
    val summaryText: String,
)

object WearableRegistry {

    // ── Category resolution ───────────────────────────────────────────────
    // Checks are ordered from most-specific to least-specific.
    // HC device type takes priority over name matching when available.

    fun resolveCategory(deviceType: String?, deviceVersion: String? = ""): WearableCategory {
        // deviceVersion is the typeLabel from HC ("Watch", "Phone", "Ring", etc.)
        val version = (deviceVersion ?: "").lowercase()
        val lower   = (deviceType ?: "").lowercase()

        // Authoritative type label from Health Connect
        return when {
            version == "ring"                                     -> WearableCategory.SMART_RING
            version == "fitness band"                             -> WearableCategory.FITNESS_BAND
            version == "chest strap"                              -> WearableCategory.CHEST_STRAP
            version == "scale"                                    -> WearableCategory.SCALE
            version == "phone"                                    -> WearableCategory.PHONE
            version == "watch" || version == "head-mounted"       -> WearableCategory.SMART_WATCH

            // Ring brands
            "oura" in lower || "galaxy ring" in lower
            || "ultrahuman" in lower || "circular" in lower
            || "evie" in lower || "motiv" in lower               -> WearableCategory.SMART_RING

            // Chest straps
            "chest" in lower || "hrm" in lower
            || "h7" in lower || "h9" in lower || "h10" in lower  -> WearableCategory.CHEST_STRAP

            // Scales / body composition
            "scale" in lower || "body+" in lower
            || "body comp" in lower || "withings" in lower
            || "qardio" in lower || "eufy" in lower              -> WearableCategory.SCALE

            // Bands (must come before generic watch check)
            "band" in lower || "charge" in lower
            || "inspire" in lower || "ace" in lower
            || "mi band" in lower || "smart band" in lower
            || "whoop" in lower || "fitbit" in lower             -> WearableCategory.FITNESS_BAND

            // Smart watches — explicit brand list
            "watch" in lower || "pixel watch" in lower
            || "galaxy watch" in lower || "apple watch" in lower
            || "fenix" in lower || "forerunner" in lower
            || "venu" in lower || "vivoactive" in lower
            || "marq" in lower || "instinct" in lower
            || "ignite" in lower || "vantage" in lower
            || "pacer" in lower || "unite" in lower
            || "gts" in lower || "gtr" in lower
            || "t-rex" in lower || "falcon" in lower
            || "bip" in lower || "cheetah" in lower
            || "versa" in lower || "sense" in lower
            || "luxe" in lower
            || "scan" in lower                                   -> WearableCategory.SMART_WATCH

            "phone" in lower || "pixel" in lower
            || "samsung" in lower && "ring" !in lower
            || "oneplus" in lower || "xiaomi" in lower
            || "oppo" in lower || "nothing" in lower             -> WearableCategory.PHONE

            else                                                 -> WearableCategory.GENERIC_WEARABLE
        }
    }

    // ── Image resolution ──────────────────────────────────────────────────
    // Returns a drawable resource name (without R.drawable. prefix).
    // Returns null → caller falls back to iconFor().

    fun imageFor(deviceType: String?, deviceVersion: String? = ""): String? {
        val h = ((deviceType ?: "") + " " + (deviceVersion ?: "")).lowercase()
        return when {
            "pixel watch" in h                       -> "device_pixel_watch_4"
            "fitbit" in h && "air" in h              -> "device_fitbit_air"
            "fitbit" in h                            -> "device_fitbit"
            "pixel" in h && "watch" !in h            -> "device_pixel_10_pro_xl"
            "pixel" in h                             -> "device_pixel_watch_4"
            else                                     -> null
        }
    }

    // ── Icon fallback ─────────────────────────────────────────────────────

    fun iconFor(category: WearableCategory): ImageVector {
        return when (category) {
            WearableCategory.SMART_WATCH    -> Icons.Filled.Watch
            WearableCategory.FITNESS_BAND   -> Icons.Filled.Watch
            WearableCategory.SMART_RING     -> Icons.Filled.RadioButtonUnchecked
            WearableCategory.CHEST_STRAP    -> Icons.Filled.FavoriteBorder
            WearableCategory.SCALE          -> Icons.Filled.MonitorWeight
            WearableCategory.PHONE          -> Icons.Filled.PhoneAndroid
            else                            -> Icons.Filled.DeviceUnknown
        }
    }

    // ── Attribution: signal → device → metric mapping ─────────────────────
    // Determines which PairedDevice objects contributed to a given metric.
    // Returns MetricAttribution with the list of contributing devices and a
    // summary text describing the overall contribution.

    fun getAttribution(metricTitle: String, devices: List<PairedDevice>): MetricAttribution {
        val relevant = devices.mapNotNull { device ->
            val matching = device.signals.filter { sig ->
                signalMatchesMetric(sig, metricTitle)
            }
            if (matching.isEmpty()) return@mapNotNull null

            val category = resolveCategory(device.deviceType, device.deviceVersion)
            val imageResId = deviceImageResourceId(device.deviceType, device.deviceVersion)
            val icon = iconFor(category)
            val sensorDesc = "Synced via Health Connect"

            DeviceContribution(
                deviceName = device.deviceType ?: "Unknown Device",
                imageRes = imageResId,
                icon = icon,
                batteryPercent = device.batteryLevelPercent,
                sensorDescription = sensorDesc,
            )
        }

        val summaryText = when {
            relevant.isEmpty() -> "No devices recorded this metric"
            relevant.size == 1 -> "${relevant[0].deviceName} recorded this"
            else -> "${relevant.size} devices recorded this"
        }

        return MetricAttribution(
            contributingDevices = relevant,
            summaryText = summaryText,
        )
    }

    private fun deviceImageResourceId(deviceType: String?, deviceVersion: String?): Int? {
        val name = imageFor(deviceType, deviceVersion) ?: return null
        return drawableIdByName(name)
    }

    private fun drawableIdByName(name: String): Int? = when (name) {
        "device_pixel_watch_4"    -> R.drawable.device_pixel_watch_4
        "device_pixel_watch"      -> R.drawable.device_pixel_watch
        "device_pixel_10_pro_xl"  -> R.drawable.device_pixel_10_pro_xl
        "device_fitbit_air"       -> R.drawable.device_fitbit_air
        "device_fitbit"           -> R.drawable.device_fitbit
        else                      -> null
    }

    private fun signalMatchesMetric(signal: String, metricTitle: String): Boolean {
        val s = signal.lowercase()
        val m = metricTitle.lowercase()
        return when {
            m.contains("step")       -> s == "steps" || s == "distance" || s == "floors"
            m.contains("sleep")      -> s == "sleep"
            m.contains("heart") || m.contains("resting") || m.contains("rhr") ->
                s == "heart rate" || s == "resting hr"
            m.contains("hrv")        -> s == "hrv"
            m.contains("spo") || m.contains("oxygen") -> s == "spo2"
            m.contains("breath")     -> s == "breathing"
            m.contains("temp")       -> s == "skin temp"
            m.contains("calori")     -> s == "calories"
            m.contains("distance")   -> s == "distance"
            m.contains("floor")      -> s == "floors"
            m.contains("active")     -> s == "calories" || s == "steps"
            m.contains("weight")     -> s == "weight"
            m.contains("body fat")   -> s == "body fat"
            m.contains("vo2")        -> s == "vo2 max"
            m.contains("water") || m.contains("hydra") -> s == "hydration"
            m.contains("workout") || m.contains("exercise") -> s == "workouts"
            else -> false
        }
    }
}
