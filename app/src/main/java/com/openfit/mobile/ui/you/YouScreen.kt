package com.openfit.mobile.ui.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.openfit.mobile.ui.charts.ChartColors

@Composable
fun YouScreen(
    onNavigateToVitals: () -> Unit,
    onNavigateToBody: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToSettings: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    "You",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Health deep-dives, history & settings",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item { Spacer(Modifier.height(4.dp)) }

        // Row 1: Vitals + Body
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HubCard(
                    icon     = Icons.Filled.Favorite,
                    title    = "Vitals",
                    subtitle = "Heart, HRV, SpO₂ & more",
                    color    = ChartColors.Heart,
                    onClick  = onNavigateToVitals,
                    modifier = Modifier.weight(1f),
                )
                HubCard(
                    icon     = Icons.Filled.MonitorWeight,
                    title    = "Body",
                    subtitle = "Weight & composition",
                    color    = ChartColors.Body,
                    onClick  = onNavigateToBody,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        // Row 2: History + Devices
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                HubCard(
                    icon     = Icons.Filled.CalendarMonth,
                    title    = "History",
                    subtitle = "Past days & trends",
                    color    = ChartColors.Sleep,
                    onClick  = onNavigateToHistory,
                    modifier = Modifier.weight(1f),
                )
                HubCard(
                    icon     = Icons.Filled.Watch,
                    title    = "Devices",
                    subtitle = "Connections & sync",
                    color    = ChartColors.Hrv,
                    onClick  = onNavigateToDevices,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        }

        // Settings row
        item {
            ListItem(
                headlineContent = {
                    Text("Settings", fontWeight = FontWeight.SemiBold)
                },
                supportingContent = {
                    Text(
                        "Appearance, units, AI, reminders & more",
                        style = MaterialTheme.typography.bodySmall,
                    )
                },
                leadingContent = {
                    Surface(
                        shape  = RoundedCornerShape(10.dp),
                        color  = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                },
                trailingContent = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onNavigateToSettings),
                colors = ListItemDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
            )
        }
    }
}

@Composable
private fun HubCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        onClick  = onClick,
        modifier = modifier,
        colors   = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape  = RoundedCornerShape(10.dp),
                color  = color.copy(alpha = 0.15f),
                modifier = Modifier.size(40.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = title,
                        tint = color,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
