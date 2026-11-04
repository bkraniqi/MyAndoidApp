package com.moneta.finance.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/**
 * Bar chart with one or more series per slot (e.g. income and expenses per month).
 * Tapping a slot selects it; the caller shows the exact values for the selection.
 */
@Composable
fun BarChart(
    series: List<List<Long>>,
    colors: List<Color>,
    labels: List<String>,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 180.dp,
    labelEvery: Int = 1,
    description: String = "Bar chart",
) {
    val slots = labels.size
    val progress = remember { Animatable(0f) }
    LaunchedEffect(series) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(700, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val maxValue = max(1L, series.maxOfOrNull { s -> s.maxOrNull() ?: 0L } ?: 1L)

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(slots, selectedIndex) {
                detectTapGestures { offset ->
                    if (slots == 0) return@detectTapGestures
                    val index = (offset.x / (size.width / slots.toFloat())).toInt().coerceIn(0, slots - 1)
                    onSelect(if (index == selectedIndex) null else index)
                }
            },
    ) {
        if (slots == 0) return@Canvas
        val labelHeight = 20.dp.toPx()
        val chartHeight = size.height - labelHeight
        val slotWidth = size.width / slots
        val groupWidth = slotWidth * 0.62f
        val barGap = 2.dp.toPx()
        val barWidth = max(2f, (groupWidth - barGap * (series.size - 1)) / series.size)
        val radius = min(barWidth / 2f, 6.dp.toPx())

        for (fraction in listOf(0.5f, 1f)) {
            val y = chartHeight - chartHeight * fraction
            drawLine(gridColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        }
        drawLine(gridColor, Offset(0f, chartHeight), Offset(size.width, chartHeight), strokeWidth = 1.5f)

        for (i in 0 until slots) {
            val left = i * slotWidth + (slotWidth - groupWidth) / 2f
            val dim = selectedIndex != null && selectedIndex != i
            series.forEachIndexed { s, values ->
                val v = values.getOrElse(i) { 0L }
                if (v <= 0L) return@forEachIndexed
                val h = (v.toDouble() / maxValue.toDouble()).toFloat() * (chartHeight - 4.dp.toPx()) * progress.value
                val x = left + s * (barWidth + barGap)
                val color = colors[s % colors.size].copy(alpha = if (dim) 0.3f else 1f)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, chartHeight - h),
                    size = Size(barWidth, h),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
            if (i % labelEvery == 0 || i == selectedIndex) {
                val layout = measurer.measure(labels[i], if (i == selectedIndex) selectedLabelStyle else labelStyle)
                val x = (i * slotWidth + slotWidth / 2f - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
                drawText(layout, topLeft = Offset(x, chartHeight + 4.dp.toPx()))
            }
        }
    }
}

data class DonutSlice(val value: Long, val color: Color)

@Composable
fun DonutChart(
    slices: List<DonutSlice>,
    selectedIndex: Int?,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    thickness: Dp = 26.dp,
    center: @Composable () -> Unit = {},
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(slices) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val total = slices.sumOf { it.value }.coerceAtLeast(0L)
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size).semantics { contentDescription = "Category breakdown chart" }) {
            val stroke = thickness.toPx()
            val inset = stroke / 2f + 4.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(track, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            if (total <= 0L) return@Canvas
            var start = -90f
            val gap = if (slices.size > 1) 1.5f else 0f
            slices.forEachIndexed { i, slice ->
                val sweep = (slice.value.toDouble() / total.toDouble() * 360.0).toFloat() * progress.value
                val selected = selectedIndex == i
                val dim = selectedIndex != null && !selected
                if (sweep > gap) {
                    drawArc(
                        color = slice.color.copy(alpha = if (dim) 0.35f else 1f),
                        startAngle = start + gap / 2f,
                        sweepAngle = sweep - gap,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(if (selected) stroke * 1.25f else stroke, cap = StrokeCap.Butt),
                    )
                }
                start += sweep
            }
        }
        center()
    }
}

/** Line chart for values that may be negative (e.g. balance), with a highlighted selection. */
@Composable
fun LineChart(
    values: List<Long>,
    labels: List<String>,
    color: Color,
    selectedIndex: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 170.dp,
    labelEvery: Int = 1,
    description: String = "Line chart",
) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(values) {
        progress.snapTo(0f)
        progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing))
    }
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val selectedLabelStyle = labelStyle.copy(color = MaterialTheme.colorScheme.onSurface)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val n = values.size

    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics { contentDescription = description }
            .pointerInput(n, selectedIndex) {
                detectTapGestures { offset ->
                    if (n == 0) return@detectTapGestures
                    val step = size.width / n.toFloat()
                    val index = (offset.x / step).toInt().coerceIn(0, n - 1)
                    onSelect(if (index == selectedIndex) null else index)
                }
            },
    ) {
        if (n == 0) return@Canvas
        val labelHeight = 20.dp.toPx()
        val top = 10.dp.toPx()
        val chartHeight = size.height - labelHeight - top
        val minV = min(0L, values.min())
        val maxV = max(0L, values.max())
        val span = max(1L, maxV - minV).toDouble()
        val step = size.width / n
        fun x(i: Int) = i * step + step / 2f
        fun y(v: Long) = top + ((maxV - v).toDouble() / span).toFloat() * chartHeight

        val zeroY = y(0L)
        drawLine(gridColor, Offset(0f, zeroY), Offset(size.width, zeroY), strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))

        val visible = max(1, (n * progress.value).toInt().coerceAtMost(n))
        val line = Path()
        val fill = Path()
        for (i in 0 until visible) {
            val px = x(i)
            val py = y(values[i])
            if (i == 0) {
                line.moveTo(px, py); fill.moveTo(px, zeroY); fill.lineTo(px, py)
            } else {
                line.lineTo(px, py); fill.lineTo(px, py)
            }
        }
        fill.lineTo(x(visible - 1), zeroY)
        fill.close()
        drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0.02f)), startY = top, endY = top + chartHeight))
        drawPath(line, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
        for (i in 0 until visible) {
            val selected = i == selectedIndex
            val r = if (selected) 6.dp.toPx() else 3.dp.toPx()
            drawCircle(color, r, Offset(x(i), y(values[i])))
            if (selected) drawCircle(surface, r / 2.2f, Offset(x(i), y(values[i])))
        }
        for (i in 0 until n) {
            if (i % labelEvery != 0 && i != selectedIndex) continue
            val layout = measurer.measure(labels[i], if (i == selectedIndex) selectedLabelStyle else labelStyle)
            val lx = (x(i) - layout.size.width / 2f).coerceIn(0f, size.width - layout.size.width)
            drawText(layout, topLeft = Offset(lx, size.height - labelHeight + 4.dp.toPx()))
        }
    }
}
