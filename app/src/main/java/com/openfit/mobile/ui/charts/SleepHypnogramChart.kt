package com.openfit.mobile.ui.charts

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.openfit.mobile.model.SleepStageSegment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * OpenFit Desktop-styled Sleep Stage Timeline (Hypnogram):
 * Renders 4 discrete tiers: Awake, REM, Light, Deep
 * with transition connectors and colored horizontal segments.
 */
@Composable
fun SleepHypnogramChart(
    segments: List<SleepStageSegment>,
    height: Dp = 160.dp,
    animationKey: Long = 0L,
    modifier: Modifier = Modifier,
) {
    if (segments.isEmpty()) {
        Box(
            modifier = modifier.fillMaxWidth().height(height),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "No sleep stage timeline available",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val parsedSegments = segments.mapNotNull { seg ->
        val start = runCatching { Instant.parse(seg.startTimeIso).toEpochMilli() }.getOrNull()
        val end = runCatching { Instant.parse(seg.endTimeIso).toEpochMilli() }.getOrNull()
        if (start != null && end != null && end > start) {
            Triple(seg.stage.lowercase(), start, end)
        } else null
    }.sortedBy { it.second }

    if (parsedSegments.isEmpty()) return

    val startEpoch = parsedSegments.first().second
    val endEpoch = parsedSegments.last().third
    val totalDuration = (endEpoch - startEpoch).coerceAtLeast(1L)

    val stageKeys = listOf("wake", "rem", "light", "deep")
    val stageLabels = listOf("Awake", "REM", "Light", "Deep")

    val gridColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    val connectorColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)

    val anim = remember(animationKey, segments) { Animatable(0f) }
    LaunchedEffect(animationKey, segments) { anim.animateTo(1f, animationSpec = tween(1400, easing = FastOutSlowInEasing)) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().height(height)) {
            // Stage labels column on the left
            Column(
                modifier = Modifier.height(height).padding(end = 8.dp),
                verticalArrangement = Arrangement.SpaceAround,
            ) {
                stageLabels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // Timeline Canvas
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
            ) {
                val width = size.width
                val plotH = size.height
                val rowHeight = plotH / stageKeys.size

                fun xFor(epoch: Long): Float {
                    return ((epoch - startEpoch).toFloat() / totalDuration.toFloat()) * width
                }

                fun yFor(stage: String): Float {
                    val idx = stageKeys.indexOf(stage).coerceAtLeast(0)
                    return (idx + 0.5f) * rowHeight
                }

                val revealEpoch = startEpoch + (totalDuration * anim.value).toLong()

                // Gridlines for each stage level
                stageKeys.indices.forEach { i ->
                    val y = (i + 0.5f) * rowHeight
                    drawLine(
                        color = gridColor,
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1f,
                    )
                }

                // Connector vertical lines between transitions
                for (i in 0 until parsedSegments.size - 1) {
                    val curr = parsedSegments[i]
                    val next = parsedSegments[i + 1]
                    val x = xFor(curr.third)
                    val y1 = yFor(curr.first)
                    val y2 = yFor(next.first)
                    if (curr.third <= revealEpoch) {
                        drawLine(
                            color = connectorColor,
                            start = Offset(x, y1),
                            end = Offset(x, y2),
                            strokeWidth = 1.5.dp.toPx(),
                        )
                    }
                }

                // Stage colored horizontal segments
                for (seg in parsedSegments) {
                    val stage = seg.first
                    val x1 = xFor(seg.second)
                    val x2 = xFor(minOf(seg.third, revealEpoch)).coerceAtLeast(x1)
                    val y = yFor(stage)
                    val col = ChartColors.sleepStageColor(stage)

                    if (seg.second <= revealEpoch) {
                        drawLine(
                            color = col,
                            start = Offset(x1, y),
                            end = Offset(x2, y),
                            strokeWidth = 5.dp.toPx(),
                        )
                    }
                }
            }
        }

        // Time labels at bottom
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 48.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val startTimeStr = formatTimeEpoch(startEpoch)
            val midTimeStr = formatTimeEpoch(startEpoch + totalDuration / 2)
            val endTimeStr = formatTimeEpoch(endEpoch)

            Text(startTimeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(midTimeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(endTimeStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun formatTimeEpoch(epoch: Long): String {
    return runCatching {
        val instant = Instant.ofEpochMilli(epoch)
        val zone = ZoneId.systemDefault()
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()).format(instant.atZone(zone))
    }.getOrDefault("")
}
