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
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * OpenFit Desktop-styled Line / Trend Area Chart:
 * - Gradient area fill below the line
 * - Smooth or clean connected points with highlight dot
 * - Optional horizontal dashed target / goal line
 * - Axis labels and min/max/average summary stats
 */
@Composable
fun DesktopLineChart(
    values: List<Double?>,
    dates: List<String> = emptyList(),
    color: Color = ChartColors.Movement,
    target: Double? = null,
    targetLabel: String? = "Goal",
    unit: String = "",
    height: Dp = 140.dp,
    showArea: Boolean = true,
    modifier: Modifier = Modifier,
    animationKey: Long = 0L,
) {
    val validValues = values.filterNotNull().filter { it.isFinite() }
    if (validValues.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(height),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No trend data available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val minVal = min(validValues.minOrNull() ?: 0.0, target ?: Double.MAX_VALUE)
    val maxVal = max(validValues.maxOrNull() ?: 1.0, target ?: Double.MIN_VALUE)
    val range = max(maxVal - minVal, 1.0)
    // Add 10% padding top and bottom
    val yMin = floor(minVal - range * 0.1).coerceAtLeast(0.0)
    val yMax = ceil(maxVal + range * 0.1)
    val ySpan = max(yMax - yMin, 1.0)

    val avgVal = validValues.average()
    val latestVal = values.lastOrNull { it != null && it.isFinite() }

    Column(modifier = modifier.fillMaxWidth()) {
        // Summary Header: Latest, Avg, Min, Max
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (latestVal != null) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = if (latestVal >= 1000) "%,d".format(latestVal.toInt()) else "%.1f".format(latestVal).trimEnd('0').trimEnd('.'),
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
                    text = "avg ${if (avgVal >= 1000) "%,d".format(avgVal.toInt()) else "%.0f".format(avgVal)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "min ${if (minVal >= 1000) "%,d".format(minVal.toInt()) else "%.0f".format(minVal)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "max ${if (maxVal >= 1000) "%,d".format(maxVal.toInt()) else "%.0f".format(maxVal)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Canvas Plot Area
        val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        val targetColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)

        val anim = remember(animationKey, values) { Animatable(0f) }
        LaunchedEffect(animationKey, values) {
            if (animationKey == 0L) return@LaunchedEffect
            anim.animateTo(1f, animationSpec = tween(1200, easing = FastOutSlowInEasing))
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
        ) {
            val width = size.width
            val plotHeight = size.height
            val n = values.size
            if (n < 2) return@Canvas

            fun xFor(index: Int): Float = (index.toFloat() / (n - 1).toFloat()) * width
            fun yFor(v: Double): Float = ((yMax - v) / ySpan).toFloat() * plotHeight
            fun yAnimated(v: Double): Float = plotHeight + (yFor(v) - plotHeight) * anim.value

            // Gridlines at 0%, 50%, 100%
            val yMid = yFor(yMin + ySpan / 2.0)
            drawLine(
                color = gridColor,
                start = Offset(0f, 0f),
                end = Offset(width, 0f),
                strokeWidth = 1f,
            )
            drawLine(
                color = gridColor,
                start = Offset(0f, yMid),
                end = Offset(width, yMid),
                strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f),
            )
            drawLine(
                color = gridColor,
                start = Offset(0f, plotHeight),
                end = Offset(width, plotHeight),
                strokeWidth = 1f,
            )

            // Target dashed line
            if (target != null && target in yMin..yMax) {
                val yTarget = yFor(target)
                drawLine(
                    color = targetColor,
                    start = Offset(0f, yTarget),
                    end = Offset(width, yTarget),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
                )
            }

            // Group continuous valid segments
            val segments = mutableListOf<List<Pair<Int, Double>>>()
            var currentSegment = mutableListOf<Pair<Int, Double>>()
            values.forEachIndexed { idx, v ->
                if (v != null && v.isFinite()) {
                    currentSegment.add(idx to v)
                } else {
                    if (currentSegment.isNotEmpty()) {
                        segments.add(currentSegment)
                        currentSegment = mutableListOf()
                    }
                }
            }
            if (currentSegment.isNotEmpty()) {
                segments.add(currentSegment)
            }

            // Draw Area and Line for each segment
            for (seg in segments) {
                if (seg.isEmpty()) continue
                val linePath = Path()
                val areaPath = Path()

                seg.forEachIndexed { i, (idx, v) ->
                    val x = xFor(idx)
                    val y = yAnimated(v)
                    if (i == 0) {
                        linePath.moveTo(x, y)
                        areaPath.moveTo(x, plotHeight)
                        areaPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        areaPath.lineTo(x, y)
                    }
                }

                val lastPoint = seg.last()
                areaPath.lineTo(xFor(lastPoint.first), plotHeight)
                areaPath.close()

                if (showArea) {
                    drawPath(
                        path = areaPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)),
                            startY = 0f,
                            endY = plotHeight,
                        ),
                    )
                }

                drawPath(
                    path = linePath,
                    color = color,
                    style = Stroke(
                        width = 2.5.dp.toPx(),
                        pathEffect = null,
                    ),
                )

                // Draw circles for data points
                for ((idx, v) in seg) {
                    val x = xFor(idx)
                    val y = yAnimated(v)
                    val isLast = idx == seg.last().first
                    drawCircle(
                        color = color,
                        radius = if (isLast) 4.5.dp.toPx() else 2.5.dp.toPx(),
                        center = Offset(x, y),
                    )
                }
            }
        }

        // Bottom Date Labels (start, middle, end)
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
