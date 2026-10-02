package com.openfit.mobile.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** Simple 24-bucket bar chart - matches OpenFit desktop's "Steps per hour"
 * chart: solid colored bars with rounded tops, 00:00/12:00/23:00 axis
 * labels underneath. `values` must have exactly 24 entries (hour 0-23). */
@Composable
fun HourlyBarChart(
    values: List<Int>,
    color: Color,
    modifier: Modifier = Modifier,
    animationKey: Long = 0L,
) {
    val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
    val anim = remember(animationKey, values) { Animatable(0f) }
    LaunchedEffect(animationKey, values) { anim.animateTo(1f, animationSpec = tween(1000, easing = FastOutSlowInEasing)) }

    Column(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.fillMaxWidth().height(120.dp)) {
            val barCount = values.size.coerceAtLeast(1)
            val gap = size.width * 0.006f
            val barWidth = (size.width - gap * (barCount - 1)) / barCount
            values.forEachIndexed { index, value ->
                val fraction = value.toFloat() / max.toFloat()
                val barHeight = size.height * fraction * anim.value
                val left = index * (barWidth + gap)
                val top = size.height - barHeight
                if (barHeight > 0f) {
                    val radius = (barWidth / 2f).coerceAtMost(6f)
                    val path = Path().apply {
                        addRoundRect(
                            RoundRect(
                                left, top, left + barWidth, size.height,
                                CornerRadius(radius, radius), CornerRadius(radius, radius),
                                CornerRadius(0f, 0f), CornerRadius(0f, 0f),
                            ),
                        )
                    }
                    drawPath(path, color = color)
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
            Text("00:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("12:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("23:00", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
