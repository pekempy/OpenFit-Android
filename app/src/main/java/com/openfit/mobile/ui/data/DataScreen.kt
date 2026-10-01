package com.openfit.mobile.ui.data

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.mobile.AppContainer
import com.openfit.mobile.model.DailySnapshot
import com.openfit.mobile.model.HealthSnapshotBundle
import com.openfit.mobile.model.PairedDevice
import com.openfit.mobile.ui.charts.BulletChart
import com.openfit.mobile.ui.charts.ChartColors
import com.openfit.mobile.ui.common.EmptyStateMessage
import com.openfit.mobile.ui.common.LoadingBlock
import com.openfit.mobile.ui.today.TodayUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataScreen(container: AppContainer, state: TodayUiState, onRefresh: () -> Unit) {
    // Request BLUETOOTH_CONNECT at runtime (Android 12+) so the repository
    // can read battery levels from bonded BT devices via the hidden API.
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
        val btPermission = android.Manifest.permission.BLUETOOTH_CONNECT
        val ctx = androidx.compose.ui.platform.LocalContext.current
        val alreadyGranted = androidx.core.content.ContextCompat.checkSelfPermission(
            ctx, btPermission
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val launcher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted -> if (granted) onRefresh() }
        LaunchedEffect(Unit) {
            if (!alreadyGranted) launcher.launch(btPermission)
        }
    }
    Scaffold(topBar = { TopAppBar(title = { Text("Data & Devices") }) }) { padding ->
        val isRefreshing = (state as? TodayUiState.Success)?.isRefreshing ?: false
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            when (state) {
                is TodayUiState.Loading -> LoadingBlock(Modifier.fillMaxSize())
                is TodayUiState.NotConnected -> EmptyStateMessage(
                    "Connect Google Health in Settings to see your paired devices and sync coverage.",
                    Modifier.fillMaxSize(),
                )
                is TodayUiState.Error -> EmptyStateMessage(state.message, Modifier.fillMaxSize())
                is TodayUiState.Success -> {
                    val bundle = state.bundle
                    val devices = bundle.devices.ifEmpty {
                        listOf(
                            PairedDevice(
                                id = "health_connect_default",
                                deviceType = "Google Health Connect",
                                deviceVersion = "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}",
                                batteryLevelPercent = null,
                                lastSyncTimeIso = Instant.now().toString(),
                            )
                        )
                    }
                    val availableMetrics = countAvailableMetrics(bundle)
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Text(
                                text = "Connected Devices & Sources",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        items(devices, key = { it.id }) { device ->
                            DeviceCard(device = device, availableMetrics = availableMetrics)
                        }
                        item { PrivacyCard() }
                        item { DataCoverageCard(bundle = bundle) }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceCard(device: PairedDevice, availableMetrics: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Connection badge matching Desktop: emerald dot + "Connected"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF10B981).copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF10B981))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Connected",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                    )
                }

                if (device.batteryLevelPercent != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.BatteryFull,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "${device.batteryLevelPercent}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val drawableId = DeviceImage.forDeviceType(device.deviceType, device.deviceVersion)
                if (drawableId != null) {
                    Image(
                        painter = painterResource(id = drawableId),
                        contentDescription = null,
                        modifier = Modifier.size(68.dp),
                        contentScale = ContentScale.Fit,
                    )
                } else {
                    val fallbackIcon = when (device.deviceVersion) {
                        "Phone" -> Icons.Filled.Smartphone
                        "Scale" -> Icons.Filled.MonitorWeight
                        "Ring" -> Icons.Filled.RadioButtonChecked
                        "Manual Entry" -> Icons.Filled.EditNote
                        else -> Icons.Filled.Watch
                    }
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            fallbackIcon,
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    val title = device.deviceType?.takeIf { it.isNotBlank() }
                        ?: device.deviceVersion?.takeIf { it.isNotBlank() }
                        ?: "Health Connect Device"
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = device.deviceVersion ?: "Health Connect Provider",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (device.signals.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    device.signals.forEach { signal ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                        ) {
                            Text(
                                text = signal,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Sync,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = formatSyncTime(device.lastSyncTimeIso),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CloudDone,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${device.signals.size} signals providing",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981).copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Security,
                    contentDescription = null,
                    tint = Color(0xFF10B981),
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Encrypted On-Device Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Health records and credentials are protected by the Android Health Connect sandbox and Android Keystore. Data never leaves your device without your consent.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DataCoverageCard(bundle: HealthSnapshotBundle) {
    val today = bundle.today
    val trend = bundle.trend

    fun hasMovement(metric: (DailySnapshot) -> Number?): Boolean {
        val t = metric(today)?.toDouble() ?: 0.0
        return t > 0.0 || trend.any { (metric(it)?.toDouble() ?: 0.0) > 0.0 }
    }

    fun hasSignal(metric: (DailySnapshot) -> Any?): Boolean {
        return metric(today) != null || trend.any { metric(it) != null }
    }

    // Which real paired device(s) actually reported each group of metrics
    // today - derived from PairedDevice.signals (itself built from real
    // Health Connect record metadata), not a fixed hardware assumption.
    fun sourceDeviceLabel(signalNames: List<String>): String {
        val matching = bundle.devices.filter { dev ->
            dev.signals.any { signal -> signalNames.any { it.equals(signal, ignoreCase = true) } }
        }
        return if (matching.isEmpty()) {
            "No connected source"
        } else {
            matching.joinToString(" & ") { it.deviceType ?: it.deviceVersion ?: "Device" }
        }
    }

    val coverageItems = listOf(
        CoverageGroup(
            label = "Movement",
            icon = Icons.Filled.DirectionsRun,
            sourceDevice = sourceDeviceLabel(listOf("Steps", "Calories", "Distance", "Floors")),
            items = listOf(
                "Steps" to hasMovement { it.steps },
                "Calories" to hasMovement { it.calories },
                "Distance" to hasMovement { it.distanceMeters },
                "Floors" to hasMovement { it.floors },
                "Active min" to hasMovement { it.activeMinutes },
            ),
        ),
        CoverageGroup(
            label = "Heart",
            icon = Icons.Filled.Favorite,
            sourceDevice = sourceDeviceLabel(listOf("Resting HR", "Heart Rate", "HRV", "VO2 Max")),
            items = listOf(
                "Resting HR" to hasSignal { it.restingHeartRateBpm },
                "HRV" to hasSignal { it.hrvMillis },
                "VO2 Max" to hasSignal { it.vo2Max },
            ),
        ),
        CoverageGroup(
            label = "Sleep",
            icon = Icons.Filled.NightsStay,
            sourceDevice = sourceDeviceLabel(listOf("Sleep")),
            items = listOf(
                "Duration" to (today.sleep != null || trend.any { it.sleep != null }),
                "Stages" to (today.sleep?.stages?.isNotEmpty() == true || trend.any { it.sleep?.stages?.isNotEmpty() == true }),
                "Efficiency" to (today.sleep?.efficiencyPercent != null || trend.any { it.sleep?.efficiencyPercent != null }),
            ),
        ),
        CoverageGroup(
            label = "Nightly Signals",
            icon = Icons.Filled.MonitorHeart,
            sourceDevice = sourceDeviceLabel(listOf("HRV", "SpO2", "Breathing", "Skin Temp")),
            items = listOf(
                "HRV RMSSD" to hasSignal { it.hrvMillis },
                "SpO2" to hasSignal { it.spo2Percent },
                "Breathing" to hasSignal { it.breathingRatePerMin },
                "Skin Temp" to hasSignal { it.skinTemperatureDeltaC },
            ),
        ),
        CoverageGroup(
            label = "Body & Nutrition",
            icon = Icons.Filled.MonitorWeight,
            sourceDevice = sourceDeviceLabel(listOf("Weight", "Body Fat", "Hydration")),
            items = listOf(
                "Weight" to hasSignal { it.weightKg },
                "Body Fat" to hasSignal { it.bodyFatPercent },
                "Hydration" to hasMovement { it.waterLiters },
            ),
        ),
    )

    val totalTracked = coverageItems.sumOf { it.items.count { item -> item.second } }
    val totalAvailable = coverageItems.sumOf { it.items.size }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "Sync quality",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "Data Coverage",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            BulletChart(
                value = totalTracked.toDouble(),
                target = totalAvailable.toDouble(),
                max = totalAvailable.toDouble(),
                label = "Available metrics",
                valueLabel = "$totalTracked / $totalAvailable",
                color = Color(0xFF10B981),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            coverageItems.forEachIndexed { index, group ->
                CoverageRow(group = group)
                if (index < coverageItems.size - 1) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                }
            }
        }
    }
}

private data class CoverageGroup(
    val label: String,
    val icon: ImageVector,
    val sourceDevice: String,
    val items: List<Pair<String, Boolean>>,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CoverageRow(group: CoverageGroup) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    group.icon,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = group.label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                text = group.sourceDevice,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            group.items.forEach { (name, active) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (active) Color(0xFF10B981).copy(alpha = 0.12f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    if (active) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = Color(0xFF10B981),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = name,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) Color(0xFF10B981) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun countAvailableMetrics(bundle: HealthSnapshotBundle): Int {
    val today = bundle.today
    val trend = bundle.trend
    var count = 0
    if ((today.steps ?: 0) > 0 || trend.any { (it.steps ?: 0) > 0 }) count++
    if ((today.calories ?: 0.0) > 0.0 || trend.any { (it.calories ?: 0.0) > 0.0 }) count++
    if ((today.distanceMeters ?: 0.0) > 0.0 || trend.any { (it.distanceMeters ?: 0.0) > 0.0 }) count++
    if ((today.floors ?: 0) > 0 || trend.any { (it.floors ?: 0) > 0 }) count++
    if ((today.activeMinutes ?: 0) > 0 || trend.any { (it.activeMinutes ?: 0) > 0 }) count++
    if (today.restingHeartRateBpm != null || trend.any { it.restingHeartRateBpm != null }) count++
    if (today.hrvMillis != null || trend.any { it.hrvMillis != null }) count++
    if (today.spo2Percent != null || trend.any { it.spo2Percent != null }) count++
    if (today.breathingRatePerMin != null || trend.any { it.breathingRatePerMin != null }) count++
    if (today.skinTemperatureDeltaC != null || trend.any { it.skinTemperatureDeltaC != null }) count++
    if (today.vo2Max != null || trend.any { it.vo2Max != null }) count++
    if (today.weightKg != null || trend.any { it.weightKg != null }) count++
    if (today.bodyFatPercent != null || trend.any { it.bodyFatPercent != null }) count++
    if ((today.waterLiters ?: 0.0) > 0.0 || trend.any { (it.waterLiters ?: 0.0) > 0.0 }) count++
    if (today.sleep != null || trend.any { it.sleep != null }) count++
    return count
}

private fun formatSyncTime(iso: String?): String {
    if (iso.isNullOrBlank()) return "Synced recently"
    return runCatching {
        val instant = Instant.parse(iso)
        val zone = ZoneId.systemDefault()
        val time = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(instant.atZone(zone))
        "Updated today at $time"
    }.getOrDefault("Updated recently")
}
