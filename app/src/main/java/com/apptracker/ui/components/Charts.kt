package com.apptracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.apptracker.ui.theme.CategoryColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun UsageBarChart(
    items: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    maxItems: Int = 5
) {
    val displayItems = items.take(maxItems)
    val maxValue = displayItems.maxOfOrNull { it.second } ?: 1f

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        displayItems.forEachIndexed { index, (label, value) ->
            val fraction = if (maxValue > 0) value / maxValue else 0f
            val color = CategoryColors[index % CategoryColors.size]

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(0.35f),
                    maxLines = 1
                )

                Box(
                    modifier = Modifier
                        .weight(0.5f)
                        .height(16.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                            .height(16.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(color)
                    )
                }

                Text(
                    text = formatValue(value),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(0.15f)
                )
            }
        }
    }
}

@Composable
fun StatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(12.dp)
            )
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun RadarChart(
    values: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    maxValue: Float = 100f
) {
    if (values.isEmpty()) return
    val outlineColor = MaterialTheme.colorScheme.outline
    val radarColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Canvas(modifier = Modifier.size(220.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension * 0.36f
            val steps = 4
            val axisCount = values.size

            repeat(steps) { stepIndex ->
                val scale = (stepIndex + 1f) / steps
                val polygon = Path()
                values.indices.forEach { index ->
                    val angle = (-PI / 2.0) + (2 * PI * index / axisCount)
                    val point = Offset(
                        x = center.x + (radius * scale * cos(angle)).toFloat(),
                        y = center.y + (radius * scale * sin(angle)).toFloat()
                    )
                    if (index == 0) polygon.moveTo(point.x, point.y) else polygon.lineTo(point.x, point.y)
                }
                polygon.close()
                drawPath(
                    path = polygon,
                    color = outlineColor.copy(alpha = 0.25f),
                    style = Stroke(width = 1.5f)
                )
            }

            values.forEachIndexed { index, _ ->
                val angle = (-PI / 2.0) + (2 * PI * index / axisCount)
                val point = Offset(
                    x = center.x + (radius * cos(angle)).toFloat(),
                    y = center.y + (radius * sin(angle)).toFloat()
                )
                drawLine(
                    color = outlineColor.copy(alpha = 0.35f),
                    start = center,
                    end = point,
                    strokeWidth = 1.2f
                )
            }

            val fillPath = Path()
            values.forEachIndexed { index, (_, value) ->
                val fraction = (value / maxValue).coerceIn(0f, 1f)
                val angle = (-PI / 2.0) + (2 * PI * index / axisCount)
                val point = Offset(
                    x = center.x + (radius * fraction * cos(angle)).toFloat(),
                    y = center.y + (radius * fraction * sin(angle)).toFloat()
                )
                if (index == 0) fillPath.moveTo(point.x, point.y) else fillPath.lineTo(point.x, point.y)
            }
            fillPath.close()

            drawPath(fillPath, color = radarColor.copy(alpha = 0.18f))
            drawPath(fillPath, color = radarColor, style = Stroke(width = 3f))

            values.forEachIndexed { index, (_, value) ->
                val fraction = (value / maxValue).coerceIn(0f, 1f)
                val angle = (-PI / 2.0) + (2 * PI * index / axisCount)
                val point = Offset(
                    x = center.x + (radius * fraction * cos(angle)).toFloat(),
                    y = center.y + (radius * fraction * sin(angle)).toFloat()
                )
                drawCircle(color = radarColor, radius = 6f, center = point)
            }
        }

        values.chunked(2).forEachIndexed { rowIndex, rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                rowItems.forEachIndexed { itemIndex, (label, value) ->
                    val color: Color = CategoryColors[(rowIndex * 2 + itemIndex) % CategoryColors.size]
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(color)
                        )
                        Text(
                            text = "$label ${value.toInt()}",
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LineChart(
    points: List<Pair<String, Float>>,
    modifier: Modifier = Modifier,
    maxPoints: Int = 24
) {
    if (points.isEmpty()) return
    val displayPoints = if (points.size > maxPoints) {
        val step = max(1, points.size / maxPoints)
        points.filterIndexed { index, _ -> index % step == 0 }.takeLast(maxPoints)
    } else {
        points
    }
    val values = displayPoints.map { it.second }
    val minValue = values.minOrNull() ?: 0f
    val maxValue = values.maxOrNull() ?: 100f
    val range = (maxValue - minValue).coerceAtLeast(1f)
    val lineColor = MaterialTheme.colorScheme.primary
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Canvas(modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
        ) {
            val left = 20f
            val right = size.width - 12f
            val top = 12f
            val bottom = size.height - 24f
            val width = (right - left).coerceAtLeast(1f)
            val height = (bottom - top).coerceAtLeast(1f)

            repeat(4) { step ->
                val y = top + (height * step / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1f
                )
            }

            val path = Path()
            val fillPath = Path()
            displayPoints.forEachIndexed { index, (_, value) ->
                val x = if (displayPoints.size == 1) left else left + width * index / (displayPoints.size - 1)
                val normalized = (value - minValue) / range
                val y = bottom - (normalized * height)
                if (index == 0) {
                    path.moveTo(x, y)
                    fillPath.moveTo(x, bottom)
                    fillPath.lineTo(x, y)
                } else {
                    path.lineTo(x, y)
                    fillPath.lineTo(x, y)
                }
            }
            val lastX = if (displayPoints.size == 1) left else left + width
            fillPath.lineTo(lastX, bottom)
            fillPath.close()

            drawPath(fillPath, color = lineColor.copy(alpha = 0.14f))
            drawPath(path, color = lineColor, style = Stroke(width = 4f))

            displayPoints.forEachIndexed { index, (_, value) ->
                val x = if (displayPoints.size == 1) left else left + width * index / (displayPoints.size - 1)
                val normalized = (value - minValue) / range
                val y = bottom - (normalized * height)
                drawCircle(color = lineColor, radius = 4f, center = Offset(x, y))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = displayPoints.first().first,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = displayPoints.last().first,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatValue(value: Float): String = when {
    value >= 1_073_741_824 -> "%.1fG".format(value / 1_073_741_824)
    value >= 1_048_576 -> "%.1fM".format(value / 1_048_576)
    value >= 1_024 -> "%.1fK".format(value / 1_024)
    value >= 100 -> "%.0f".format(value)
    value >= 1 -> "%.1f".format(value)
    else -> "%.2f".format(value)
}
