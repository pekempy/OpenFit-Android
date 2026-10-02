package com.openfit.mobile.ui.common

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp

/** Shared "Today"-style stat card: icon + label header, a big value, and an
 * optional muted sub-label - the same visual unit reused across
 * Today/Activity/Sleep/Health/Body so every screen shares one convention
 * instead of each inventing its own card.
 *
 * Long-pressing any stat card triggers haptic feedback and displays a brief
 * informational Toast explaining what the stat is, how it is measured, and what
 * it means for health/recovery. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetricCard(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    subLabel: String? = null,
    accentColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    explanation: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val inspector = LocalMetricInspector.current
    val defaultPrimary = MaterialTheme.colorScheme.primary
    val detail = remember(label, value, icon, accentColor, defaultPrimary) {
        val base = MetricKnowledge.getDetail(label, currentValue = value, fallbackIcon = icon)
        if (accentColor != defaultPrimary) base.copy(accentColor = accentColor) else base
    }

    ElevatedCard(
        modifier = modifier
            .clip(CardDefaults.elevatedShape)
            .combinedClickable(
                onClick = {
                    if (onClick != null) onClick() else inspector.showToast(detail)
                },
                onLongClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (onLongClick != null) onLongClick() else inspector.showSheet(detail)
                },
            ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
            if (subLabel != null) {
                Spacer(Modifier.height(2.dp))
                Text(subLabel, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Comprehensive educational descriptions for health metrics formatted for Toast notifications. */
fun getMetricExplanation(label: String): String {
    val key = label.lowercase().trim()
    return when {
        "hrv" in key ->
            "HRV: Beat variation via PPG sensors. Higher indicates better recovery."
        "spo2" in key || "blood oxygen" in key || "oxygen" in key ->
            "SpO2: Blood oxygen via optical PPG. 95–100% is typical during sleep."
        "resting" in key || "rhr" in key ->
            "Resting HR: Pulse at rest via PPG. Lower indicates heart efficiency."
        "breath" in key || "respiratory" in key ->
            "Breathing: Respiration via HRV. 12–20 rpm is healthy baseline."
        "temperature" in key || "temp" in key ->
            "Skin Temp: Wrist delta (°C) from baseline. Tracks recovery & sleep."
        "step" in key || "movement" in key ->
            "Movement: Steps via accelerometer toward your 10k goal."
        "calorie" in key ->
            "Calories: Total burn combining resting BMR with active motion."
        "distance" in key ->
            "Distance: Walked & run distance from steps, stride & GPS."
        "floor" in key ->
            "Floors: Elevation gained (~3m/floor) via altimeter."
        "active" in key || "zone" in key ->
            "Zone Minutes: Time in cardio/peak HR. 150 min/wk aids longevity."
        "sleep" in key ->
            "Sleep: Duration & stages via motion/HR. 7–9h aids cell repair."
        "weight" in key ->
            "Weight: Body mass in kg from smart scales or logs over time."
        "body fat" in key || "fat" in key ->
            "Body Fat: Mass % from fat tissue via scale bioimpedance."
        "hydration" in key || "water" in key ->
            "Hydration: Fluid intake in litres toward your daily goal."
        "cardio" in key || "vo2" in key ->
            "VO2 Max: Peak oxygen uptake (mL/kg/min). Aerobic benchmark."
        else ->
            "$label: Monitored by your paired devices and synced with Google Health Connect."
    }
}

@Composable
fun EmptyStateMessage(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LoadingBlock(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}
