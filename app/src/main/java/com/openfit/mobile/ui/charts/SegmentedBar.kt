package com.openfit.mobile.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Single horizontal bar split proportionally into colored segments -
 * matches OpenFit desktop's sleep-stage bar under the "6h 34m" duration.
 * `segments` is (value, color) pairs in left-to-right draw order; zero-value
 * segments are skipped. */
@Composable
fun SegmentedBar(
    segments: List<Pair<Float, Color>>,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
    cornerRadius: Dp = 5.dp,
    animationKey: Long = 0L,
) {
    val total = segments.sumOf { it.first.toDouble() }.toFloat()
    val anim = remember(animationKey, segments) { Animatable(0f) }
    LaunchedEffect(animationKey, segments) {
        if (animationKey == 0L) return@LaunchedEffect
        anim.animateTo(1f, animationSpec = tween(850, easing = FastOutSlowInEasing))
    }
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        if (total <= 0f) return@Canvas
        val radiusPx = cornerRadius.toPx()
        val fullPath = Path().apply {
            addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, 0f, size.width, size.height, CornerRadius(radiusPx, radiusPx)))
        }
        clipPath(fullPath) {
            var x = 0f
            for ((value, color) in segments) {
                if (value <= 0f) continue
                val width = size.width * (value / total) * anim.value
                drawRect(color = color, topLeft = Offset(x, 0f), size = Size(width, size.height))
                x += width
            }
        }
    }
}
