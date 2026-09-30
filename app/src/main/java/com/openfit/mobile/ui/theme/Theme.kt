package com.openfit.mobile.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.openfit.mobile.data.settings.AccentColorTheme

// Fallback palette for devices below Android 12 (no dynamic colour) - a
// deep-blue/teal scheme evoking the desktop OpenFit dashboard's dark UI.
private val FallbackDark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFF9ED3C4),
    tertiary = Color(0xFFF2B8B5),
    background = Color(0xFF101112),
    surface = Color(0xFF1B1B1F),
)
private val FallbackLight = lightColorScheme(
    primary = Color(0xFF1A5FB4),
    secondary = Color(0xFF2E7D6B),
    tertiary = Color(0xFFB3261E),
    background = Color(0xFFFBFBFE),
    surface = Color(0xFFFFFFFF),
)

/** Base hue for each fixed accent preset; CUSTOM resolves from the user's
 * own hex string instead (falling back to the Teal hue if unset/invalid). */
private fun presetSeedColor(theme: AccentColorTheme, customHex: String?): Color = when (theme) {
    AccentColorTheme.TEAL -> Color(0xFF00BFA5)
    AccentColorTheme.PIXEL_BLUE -> Color(0xFF4285F4)
    AccentColorTheme.CORAL -> Color(0xFFFF7043)
    AccentColorTheme.EMERALD -> Color(0xFF2ECC71)
    AccentColorTheme.VIOLET -> Color(0xFF8B5CF6)
    AccentColorTheme.CUSTOM -> parseHexColorOrNull(customHex) ?: Color(0xFF00BFA5)
}

fun parseHexColorOrNull(hex: String?): Color? {
    if (hex.isNullOrBlank()) return null
    val cleaned = hex.trim().removePrefix("#")
    if (cleaned.length != 6 && cleaned.length != 8) return null
    return runCatching { Color(android.graphics.Color.parseColor("#$cleaned")) }.getOrNull()
}

/** Derives a full colour scheme from a single seed colour by tinting the
 * fixed fallback scheme's primary/secondary/tertiary roles - a simple,
 * dependency-free stand-in for a full Material tonal-palette generator
 * (which Compose only auto-derives from a live wallpaper, not an arbitrary
 * seed). Good enough contrast for a fixed accent choice without needing the
 * device wallpaper. */
private fun schemeForSeed(dark: Boolean, seed: Color): ColorScheme {
    val base = if (dark) FallbackDark else FallbackLight
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(seed.toArgb(), hsv)
    fun tone(value: Float, saturation: Float = hsv[1]): Color =
        Color(android.graphics.Color.HSVToColor(floatArrayOf(hsv[0], saturation.coerceIn(0f, 1f), value.coerceIn(0f, 1f))))
    return base.copy(
        primary = seed,
        onPrimary = if (dark) Color.Black else Color.White,
        primaryContainer = if (dark) tone(0.35f) else tone(0.92f, hsv[1] * 0.35f),
        onPrimaryContainer = if (dark) tone(0.92f) else tone(0.2f),
        secondary = tone(if (dark) 0.75f else 0.4f, hsv[1] * 0.5f),
        tertiary = tone(if (dark) 0.82f else 0.48f, hsv[1] * 0.3f),
    )
}

@Composable
fun OpenFitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You: use the device wallpaper-derived palette on Android 12+,
    // exactly what the Google Health / Fit apps do; falls back to a fixed
    // or user-chosen accent palette below API 31 or when disabled in Settings.
    dynamicColor: Boolean = true,
    accentColor: AccentColorTheme = AccentColorTheme.TEAL,
    customAccentColorHex: String? = null,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> schemeForSeed(darkTheme, presetSeedColor(accentColor, customAccentColorHex))
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = OpenFitTypography,
        content = content,
    )
}
