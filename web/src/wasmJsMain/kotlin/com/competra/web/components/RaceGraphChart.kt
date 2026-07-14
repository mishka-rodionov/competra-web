package com.competra.web.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.competra.domain.models.RaceGraphData
import com.competra.web.utils.formatTime

/** Циклическая палитра линий графика — 12 цветов, как в Android-версии. */
val raceGraphPalette = listOf(
    Color(0xFF1E88E5), Color(0xFFD81B60), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFF4511E), Color(0xFF3949AB),
    Color(0xFF6D4C41), Color(0xFFC0CA33), Color(0xFF00897B), Color(0xFFE53935),
)

fun raceGraphColor(index: Int): Color = raceGraphPalette[index % raceGraphPalette.size]

/**
 * Линейный график отставания от лидера. Vico не публикует таргет wasmJs, поэтому график
 * нарисован вручную на Compose Canvas — модель данных (RaceGraphData) при этом та же, что
 * и в Android-версии, так что при появлении wasm-совместимого чарт-движка отрисовку можно
 * будет заменить, не трогая бизнес-логику.
 */
@Composable
fun RaceGraphChart(
    data: RaceGraphData,
    visibleParticipantIds: Set<String>,
    highlightedParticipantId: String?,
    modifier: Modifier = Modifier,
) {
    val visibleSeries = data.series.filter { it.participant.id in visibleParticipantIds }

    if (visibleSeries.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth().height(280.dp), contentAlignment = Alignment.Center) {
            Text("Нет участников для отображения на графике", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val maxDeltaSeconds = visibleSeries.flatMap { it.points }.mapNotNull { it.deltaSeconds }.maxOrNull() ?: 0L
    val columnCount = data.columns.size
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)

    Canvas(modifier = modifier.fillMaxWidth().height(280.dp)) {
        val leftPad = 48f
        val bottomPad = 24f
        val topPad = 8f
        val plotWidth = size.width - leftPad
        val plotHeight = size.height - bottomPad - topPad
        if (plotWidth <= 0f || plotHeight <= 0f || columnCount == 0) return@Canvas

        fun xFor(positionIndex: Int): Float =
            if (columnCount <= 1) leftPad + plotWidth / 2f
            else leftPad + plotWidth * (positionIndex - 1) / (columnCount - 1).toFloat()

        fun yFor(deltaSeconds: Long): Float =
            if (maxDeltaSeconds <= 0L) topPad
            else topPad + plotHeight * (deltaSeconds / maxDeltaSeconds.toFloat())

        // Нулевая линия лидера.
        drawLine(
            color = axisColor.copy(alpha = 0.5f),
            start = Offset(leftPad, topPad),
            end = Offset(size.width, topPad),
            strokeWidth = 1.5f,
        )
        drawText(textMeasurer, "0:00", Offset(0f, topPad - 6f), labelStyle)
        if (maxDeltaSeconds > 0) {
            drawText(
                textMeasurer,
                "-${formatTime(maxDeltaSeconds)}",
                Offset(0f, size.height - bottomPad - 12f),
                labelStyle,
            )
        }

        data.columns.forEach { column ->
            drawText(
                textMeasurer,
                column.controlPoint.toString(),
                Offset(xFor(column.positionIndex) - 6f, size.height - bottomPad + 4f),
                labelStyle,
            )
        }

        visibleSeries.forEach { series ->
            val originalIndex = data.series.indexOf(series)
            val baseColor = raceGraphColor(originalIndex)
            val isDimmed = highlightedParticipantId != null && highlightedParticipantId != series.participant.id
            val color = if (isDimmed) baseColor.copy(alpha = 0.25f) else baseColor

            val points = series.points.filter { it.deltaSeconds != null }
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                drawLine(
                    color = color,
                    start = Offset(xFor(p0.positionIndex), yFor(p0.deltaSeconds!!)),
                    end = Offset(xFor(p1.positionIndex), yFor(p1.deltaSeconds!!)),
                    strokeWidth = if (isDimmed) 2f else 3f,
                    cap = StrokeCap.Round,
                )
            }
            points.forEach { point ->
                drawCircle(
                    color = color,
                    radius = if (isDimmed) 2.5f else 4f,
                    center = Offset(xFor(point.positionIndex), yFor(point.deltaSeconds!!)),
                )
            }
        }
    }
}
