package com.openfit.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Material 3's default type scale on the system font (Roboto/Google Sans
// depending on OEM) - deliberately not overridden with a custom font family
// so it inherits whatever the device's Material You setup already uses.
val OpenFitTypography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Light, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = 0.sp),
    headlineLarge  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = (-0.25).sp),
    headlineSmall  = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = (-0.15).sp),
    titleLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = (-0.1).sp),
    titleMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyLarge      = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.sp),
    bodyMedium     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Normal,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.sp),
    labelLarge     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.3.sp),
    labelMedium    = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),
    labelSmall     = TextStyle(fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium,   fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
