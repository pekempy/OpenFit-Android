package com.openfit.mobile.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/**
 * OpenFit Desktop-styled Column / Bar Chart for daily trends:
 * - Rounded bar tops
 * - Target dashed line (e.g. 10,000 steps goal, 8h sleep goal)
 * - Highlights current / latest bar
 * - Start / Mid / End date labels
 */
@Composable
fun DesktopColumnChart(
    values: List<Double?>,
    dates: List<String> = emptyList(),
    color: Color = ChartColors.Movement,
    target: Double? = null,
    targetLabel: String? = "Goal",
    unit: String = "",
    height: Dp = 140.dp,
    formatter: (Double) -> String = { if (it >= 1000) "%,d".format(it.toInt()) else "%.0f".format(it) },
    modifier: Modifier = Modifier,
) {
    val validValues = values.filterNotNull().filter { it.isFinite() }
    if (validValues.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(height),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No data available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val maxVal = max(validValues.maxOrNull() ?: 1.0, target ?: 0.0)
    val avgVal = validValues.average()
    val latestVal = values.lastOrNull { it != null && it.isFinite() }

    Column(modifier = modifier.fillMaxWidth()) {
        // Summary Header: Latest, Average
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (latestVal != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatter(latestVal),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    if (unit.isNotBlank()) {
                        Text(
                            text = " $unit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 2.dp),
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "avg ${formatter(avgVal)}${if (unit.isNotBlank()) " $unit" else ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (target != null) {
                    Text(
                        text = "$targetLabel ${formatter(target)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
        val targetColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
        var trigger by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { trigger = true }
        val anim by animateFloatAsState(
            if (trigger) 1f else 0f,
            tween(700, easing = FastOutSlowInEasing),
            label = "colAnim"
        )


        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val width = size.width
            val plotHeight = size.height
            val count = values.size
            if (count == 0) return@Canvas

            val gap = width * 0.015f
            val barWidth = ((width - gap * (count - 1)) / count).coerceAtLeast(3f)

            // Gridline at base
            drawLine(
                color = gridColor,
                start = Offset(0f, plotHeight),
                end = Offset(width, plotHeight),
                strokeWidth = 1f,
            )

            // Target dashed line
            if (target != null && target > 0.0 && target <= maxVal * 1.05) {
                val yTarget = (plotHeight * (1.0 - target / maxVal)).toFloat().coerceIn(0f, plotHeight)
                drawLine(
                    color = targetColor,
                    start = Offset(0f, yTarget),
                    end = Offset(width, yTarget),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                )
            }

            // Draw bars
            values.forEachIndexed { index, v ->
                val numeric = v ?: 0.0
                val fraction = (numeric / maxVal).toFloat().coerceIn(0f, 1f)
                val barH = plotHeight * fraction * anim
                val left = index * (barWidth + gap)
                val top = plotHeight - barH
                val isToday = index == values.size - 1

                val barColor = if (v == null || v <= 0.0) {
                    color.copy(alpha = 0.15f)
                } else if (isToday) {
                    color
                } else {
                    color.copy(alpha = 0.78f)
                }

                if (barH > 0f) {
                    val radius = (barWidth / 2f).coerceAtMost(5.dp.toPx())
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left = left,
                                top = top,
                                right = left + barWidth,
                                bottom = plotHeight,
                                topLeftCornerRadius = CornerRadius(radius, radius),
                                topRightCornerRadius = CornerRadius(radius, radius),
                                bottomLeftCornerRadius = CornerRadius(0f, 0f),
                                bottomRightCornerRadius = CornerRadius(0f, 0f),
                            )
                        )
                    }
                    drawPath(path, color = barColor)
                }
            }
        }

        // Bottom Date Labels
        if (dates.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val startLabel = formatShortDate(dates.firstOrNull())
                val midIdx = dates.size / 2
                val midLabel = formatShortDate(dates.getOrNull(midIdx))
                val endLabel = formatShortDate(dates.lastOrNull())

                Text(startLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(midLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(endLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

private fun formatShortDate(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val date = LocalDate.parse(iso)
        date.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
    }.getOrDefault(iso.takeLast(5))
}
