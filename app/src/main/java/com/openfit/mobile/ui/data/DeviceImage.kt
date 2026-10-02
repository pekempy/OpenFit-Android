package com.openfit.mobile.ui.data

import androidx.annotation.DrawableRes
import com.openfit.mobile.R
import com.openfit.mobile.model.WearableRegistry

object DeviceImage {
    /**
     * Returns a drawable resource ID for the given device, or null if no
     * matching raster image exists (caller should fall back to [WearableRegistry.iconFor]).
     *
     * Matching is case-insensitive and checks both [deviceType] (display name)
     * and [deviceVersion] (type label from HC).
     */
    @DrawableRes
    fun forDeviceType(deviceType: String?, deviceVersion: String?): Int? {
        val name = WearableRegistry.imageFor(
            deviceType.orEmpty(),
            deviceVersion.orEmpty()
        ) ?: return null
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
}
