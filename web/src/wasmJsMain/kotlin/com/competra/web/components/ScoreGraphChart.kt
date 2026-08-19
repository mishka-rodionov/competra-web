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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.competra.domain.models.ScoreGraphData
import com.competra.web.utils.formatTime
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val TIME_LIMIT_COLOR = Color(0xFFE65100)

/** "Круглый" шаг для оси очков — 1/2/5 * 10^n, ближайший к range/targetTicks. */
private fun niceScoreStep(maxValue: Int, targetTicks: Int = 5): Int {
    if (maxValue <= 0) return 1
    val rough = maxValue.toDouble() / targetTicks
    val magnitude = 10.0.pow(floor(log10(rough)))
    val normalized = rough / magnitude
    val niceNormalized = when {
        normalized <= 1.0 -> 1.0
        normalized <= 2.0 -> 2.0
        normalized <= 5.0 -> 5.0
        else -> 10.0
    }
    return (niceNormalized * magnitude).toInt().coerceAtLeast(1)
}

private val NICE_TIME_STEPS_SECONDS = listOf(15L, 30L, 60L, 120L, 300L, 600L, 900L, 1800L, 3600L, 7200L, 10800L, 21600L)

/** Ближайший "круглый" шаг времени (секунды, 15с/30с/1мин/.../6ч), под который попадает range/targetTicks. */
private fun niceTimeStepSeconds(maxSeconds: Long, targetTicks: Int = 6): Long {
    if (maxSeconds <= 0) return NICE_TIME_STEPS_SECONDS.first()
    val rough = maxSeconds / targetTicks
    return NICE_TIME_STEPS_SECONDS.firstOrNull { it >= rough }
        ?: (NICE_TIME_STEPS_SECONDS.last() * (rough / NICE_TIME_STEPS_SECONDS.last() + 1))
}

/**
 * Линейный график набора очков во времени (BY_CHOICE). Как и [RaceGraphChart], нарисован
 * вручную на Compose Canvas — Vico не публикует таргет wasmJs.
 *
 * Оси размечены сеткой с "круглым" шагом (1/2/5*10^n по очкам, 15с..6ч по времени) для
 * наглядности; вертикальная пунктирная линия отмечает контрольное время группы, если оно задано.
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
    val gridColor = axisColor.copy(alpha = 0.15f)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)

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

        // Сетка по очкам (Y).
        val scoreStep = niceScoreStep(maxScore)
        var scoreTick = scoreStep
        while (scoreTick < maxScore) {
            val y = yFor(scoreTick)
            drawLine(color = gridColor, start = Offset(leftPad, y), end = Offset(size.width, y), strokeWidth = 1f)
            drawText(textMeasurer, scoreTick.toString(), Offset(0f, y - 6f), labelStyle)
            scoreTick += scoreStep
        }

        // Сетка по времени (X).
        val timeStep = niceTimeStepSeconds(maxElapsedSeconds)
        var timeTick = timeStep
        while (timeTick < maxElapsedSeconds) {
            val x = xFor(timeTick)
            drawLine(color = gridColor, start = Offset(x, topPad), end = Offset(x, size.height - bottomPad), strokeWidth = 1f)
            val label = formatTime(timeTick)
            val labelWidth = textMeasurer.measure(label, labelStyle).size.width
            drawText(textMeasurer, label, Offset(x - labelWidth / 2f, size.height - bottomPad + 4f), labelStyle)
            timeTick += timeStep
        }

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

        // Линия контрольного времени группы.
        val timeLimitSeconds = data.timeLimitSeconds
        if (timeLimitSeconds != null && timeLimitSeconds in 1 until maxElapsedSeconds) {
            val x = xFor(timeLimitSeconds)
            drawLine(
                color = TIME_LIMIT_COLOR,
                start = Offset(x, topPad),
                end = Offset(x, size.height - bottomPad),
                strokeWidth = 1.5f,
                pathEffect = dashEffect,
            )
            drawText(
                textMeasurer,
                "лимит",
                Offset(x + 3f, topPad),
                labelStyle.copy(color = TIME_LIMIT_COLOR),
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
