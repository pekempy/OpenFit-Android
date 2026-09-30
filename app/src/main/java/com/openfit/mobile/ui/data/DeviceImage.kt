package com.openfit.mobile.ui.data

import androidx.annotation.DrawableRes
import com.openfit.mobile.R

/** Maps a Google Health API `deviceType` (or free-text `deviceVersion`) to a
 * representative product image. Images are sourced from Wikimedia Commons
 * under CC-BY-SA-4.0 (device_pixel_watch.png: "Google Pixel Watch (Matte
 * Black + Obsidian).svg" by Mliu92; device_fitbit.png: "Fitbit versa.jpg") -
 * see res/drawable-nodpi for the bundled files. Unrecognised device types
 * fall back to a generic vector watch icon drawn in the UI layer instead of
 * a raster image. */
object DeviceImage {
    @DrawableRes
    fun forDeviceType(deviceType: String?, deviceVersion: String?): Int? {
        val haystack = "${deviceType.orEmpty()} ${deviceVersion.orEmpty()}".lowercase()
        return when {
            "watch" in haystack -> R.drawable.device_pixel_watch_4
            "air" in haystack || "fitbit" in haystack -> R.drawable.device_fitbit_air
            "pixel 10" in haystack || "pro xl" in haystack || "phone" in haystack || "google pixel" in haystack -> R.drawable.device_pixel_10_pro_xl
            else -> null
        }
    }
}
