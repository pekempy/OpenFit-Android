package com.openfit.mobile.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * OpenFit Desktop-styled Bullet Chart:
 * Horizontal track with filled progress, optional goal target pip, and label/value captions.
 */
@Composable
fun BulletChart(
    value: Double?,
    target: Double? = null,
    max: Double = 100.0,
    label: String,
    valueLabel: String? = null,
    color: Color = ChartColors.Movement,
    modifier: Modifier = Modifier,
) {
    if (value == null) return
    val safeMax = max(max, max(value, target ?: 0.0)).coerceAtLeast(1.0)
    val valuePercent = (value / safeMax).toFloat().coerceIn(0f, 1f)
    val targetPercent = target?.let { (it / safeMax).toFloat().coerceIn(0f, 1f) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Value fill
            Box(
                modifier = Modifier
                    .fillMaxWidth(valuePercent)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )

            // Target marker line
            if (targetPercent != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(targetPercent)
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(2.5.dp)
                            .height(14.dp)
                            .background(MaterialTheme.colorScheme.onSurface)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = valueLabel ?: if (value >= 1000) "%,d".format(value.toInt()) else "%.1f".format(value),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
