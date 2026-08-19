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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.competra.domain.models.ScoreGraphData
import com.competra.web.utils.formatTime

/**
 * Линейный график набора очков во времени (BY_CHOICE). Как и [RaceGraphChart], нарисован
 * вручную на Compose Canvas — Vico не публикует таргет wasmJs.
 */
@Composable
fun ScoreGraphChart(
    data: ScoreGraphData,
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

    val maxElapsedSeconds = visibleSeries.flatMap { it.points }.maxOfOrNull { it.elapsedSeconds } ?: 0L
    val maxScore = visibleSeries.flatMap { it.points }.maxOfOrNull { it.cumulativeScore } ?: 0
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)

    Canvas(modifier = modifier.fillMaxWidth().height(280.dp)) {
        val leftPad = 40f
        val bottomPad = 24f
        val topPad = 8f
        val plotWidth = size.width - leftPad
        val plotHeight = size.height - bottomPad - topPad
        if (plotWidth <= 0f || plotHeight <= 0f) return@Canvas

        fun xFor(elapsedSeconds: Long): Float =
            if (maxElapsedSeconds <= 0L) leftPad
            else leftPad + plotWidth * (elapsedSeconds / maxElapsedSeconds.toFloat())

        fun yFor(score: Int): Float =
            if (maxScore <= 0) topPad + plotHeight
            else topPad + plotHeight * (1f - score / maxScore.toFloat())

        // Оси.
        drawLine(
            color = axisColor.copy(alpha = 0.5f),
            start = Offset(leftPad, topPad),
            end = Offset(leftPad, size.height - bottomPad),
            strokeWidth = 1.5f,
        )
        drawLine(
            color = axisColor.copy(alpha = 0.5f),
            start = Offset(leftPad, size.height - bottomPad),
            end = Offset(size.width, size.height - bottomPad),
            strokeWidth = 1.5f,
        )
        drawText(textMeasurer, maxScore.toString(), Offset(0f, topPad - 2f), labelStyle)
        drawText(textMeasurer, "0", Offset(0f, size.height - bottomPad - 12f), labelStyle)
        drawText(textMeasurer, "0:00", Offset(leftPad, size.height - bottomPad + 4f), labelStyle)
        if (maxElapsedSeconds > 0) {
            val maxLabel = formatTime(maxElapsedSeconds)
            val maxLabelWidth = textMeasurer.measure(maxLabel, labelStyle).size.width
            drawText(
                textMeasurer,
                maxLabel,
                Offset(size.width - maxLabelWidth, size.height - bottomPad + 4f),
                labelStyle,
            )
        }

        visibleSeries.forEach { series ->
            val originalIndex = data.series.indexOf(series)
            val baseColor = raceGraphColor(originalIndex)
            val isDimmed = highlightedParticipantId != null && highlightedParticipantId != series.participant.id
            val color = if (isDimmed) baseColor.copy(alpha = 0.25f) else baseColor

            val points = series.points
            for (i in 0 until points.size - 1) {
                val p0 = points[i]
                val p1 = points[i + 1]
                drawLine(
                    color = color,
                    start = Offset(xFor(p0.elapsedSeconds), yFor(p0.cumulativeScore)),
                    end = Offset(xFor(p1.elapsedSeconds), yFor(p1.cumulativeScore)),
                    strokeWidth = if (isDimmed) 2f else 3f,
                    cap = StrokeCap.Round,
                )
            }
            points.forEach { point ->
                drawCircle(
                    color = color,
                    radius = if (isDimmed) 2.5f else 4f,
                    center = Offset(xFor(point.elapsedSeconds), yFor(point.cumulativeScore)),
                )
            }
        }
    }
}
