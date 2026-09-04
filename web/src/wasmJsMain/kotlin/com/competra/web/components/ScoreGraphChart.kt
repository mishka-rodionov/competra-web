package com.competra.web.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.competra.domain.models.ScoreGraphData
import com.competra.web.utils.DebugErrorReporter
import com.competra.web.utils.formatTime
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

private val TIME_LIMIT_COLOR = Color(0xFFE65100)

private const val CHART_HEIGHT_DP = 300
private const val LEFT_PAD = 40f
private const val RIGHT_PAD = 28f
private const val BOTTOM_PAD = 24f
private const val TOP_PAD = 8f
private const val TAP_HIT_RADIUS_DP = 24

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

/**
 * Ближайший "круглый" шаг времени (секунды, 15с/30с/1мин/.../6ч), под который попадает
 * range/targetTicks. Не private — переиспользуется в [RaceGraphChart] для сетки по оси Y.
 */
fun niceTimeStepSeconds(maxSeconds: Long, targetTicks: Int = 6): Long {
    if (maxSeconds <= 0) return NICE_TIME_STEPS_SECONDS.first()
    val rough = maxSeconds / targetTicks
    return NICE_TIME_STEPS_SECONDS.firstOrNull { it >= rough }
        ?: (NICE_TIME_STEPS_SECONDS.last() * (rough / NICE_TIME_STEPS_SECONDS.last() + 1))
}

/**
 * Линейный график набора очков во времени (BY_CHOICE). Как и [RaceGraphChart], нарисован
 * вручную на Compose Canvas — Vico не публикует таргет wasmJs.
 *
 * Ось X (время) зумируется и панится пинчем/колесом мыши через [ChartZoomState], сетка обеих
 * осей пересчитывается под текущий видимый диапазон, тап по точке показывает тултип с именем
 * участника, временем и накопленными очками.
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
        Box(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp), contentAlignment = Alignment.Center) {
            Text("Нет участников для отображения на графике", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val fullWorldMin = 0.0
    val fullMaxElapsed = data.series.flatMap { it.points }.maxOfOrNull { it.elapsedSeconds } ?: 0L
    val fullWorldMax = fullMaxElapsed.toDouble()
    val zoomState = remember(data) { ChartZoomState().apply { resetTo((fullWorldMin + fullWorldMax) / 2) } }
    var containerSize by remember(data) { mutableStateOf(IntSize.Zero) }
    var selected by remember(data) { mutableStateOf<Pair<String, Long>?>(null) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)
    val gridColor = axisColor.copy(alpha = 0.15f)
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f)

    // xFor/yFor читают zoomState/containerSize напрямую при каждом вызове — безопасно звать из
    // pointerInput-обработчика тапа даже без перезапуска его корутины на каждый жест зума/пана.
    fun visibleWorldRange(): Pair<Double, Double> {
        if (fullWorldMax <= fullWorldMin) return fullWorldMin to fullWorldMin
        val visW = zoomState.visibleWidth(fullWorldMax - fullWorldMin)
        return (zoomState.centerWorld - visW / 2) to (zoomState.centerWorld + visW / 2)
    }

    fun currentMaxScore(): Int {
        val (leftWorld, rightWorld) = visibleWorldRange()
        val pad = (rightWorld - leftWorld) * 0.05
        return visibleSeries.flatMap { it.points }
            .filter { it.elapsedSeconds >= leftWorld - pad && it.elapsedSeconds <= rightWorld + pad }
            .maxOfOrNull { it.cumulativeScore } ?: 0
    }

    // Финальная страховка от NaN/Infinity перед тем, как координата уйдёт в Skia — см. тот же
    // комментарий у RaceGraphChart.xFor/yFor.
    fun xFor(elapsedSeconds: Long): Float {
        val plotWidth = (containerSize.width - LEFT_PAD - RIGHT_PAD).coerceAtLeast(0f)
        if (fullWorldMax <= fullWorldMin) return LEFT_PAD
        val (leftWorld, _) = visibleWorldRange()
        val visW = fullWorldMax - fullWorldMin
        val visibleWidth = zoomState.visibleWidth(visW)
        val raw = LEFT_PAD + plotWidth * ((elapsedSeconds - leftWorld) / visibleWidth).toFloat()
        return if (raw.isFinite()) raw else LEFT_PAD
    }

    fun yFor(score: Int): Float {
        val plotHeight = (containerSize.height - BOTTOM_PAD - TOP_PAD).coerceAtLeast(0f)
        val maxScore = currentMaxScore()
        val raw = if (maxScore <= 0) TOP_PAD + plotHeight else TOP_PAD + plotHeight * (1f - score / maxScore.toFloat())
        return if (raw.isFinite()) raw else TOP_PAD
    }

    Box(
        modifier = modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp).onSizeChanged { containerSize = it },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(CHART_HEIGHT_DP.dp)
                .chartZoomGestures(
                    state = zoomState,
                    worldMin = fullWorldMin,
                    worldMax = fullWorldMax,
                    leftPad = LEFT_PAD,
                    plotWidth = { (containerSize.width - LEFT_PAD - RIGHT_PAD).coerceAtLeast(0f) },
                )
                .pointerInput(data, visibleParticipantIds) {
                    val hitRadiusPx = with(density) { TAP_HIT_RADIUS_DP.dp.toPx() }
                    detectTapGestures(onTap = { offset ->
                        try {
                            var bestId: String? = null
                            var bestElapsed = 0L
                            var bestDistSq = Float.MAX_VALUE
                            visibleSeries.forEach { series ->
                                series.points.forEach { point ->
                                    val dx = xFor(point.elapsedSeconds) - offset.x
                                    val dy = yFor(point.cumulativeScore) - offset.y
                                    val distSq = dx * dx + dy * dy
                                    if (distSq < bestDistSq) {
                                        bestDistSq = distSq
                                        bestId = series.participant.id
                                        bestElapsed = point.elapsedSeconds
                                    }
                                }
                            }
                            selected = if (bestId != null && bestDistSq <= hitRadiusPx * hitRadiusPx) bestId to bestElapsed else null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            println("ScoreGraphChart: тап по точке упал: $e, offset=$offset")
                            DebugErrorReporter.report("График очков: тап по точке упал (${e.message ?: e::class.simpleName})")
                        }
                    })
                },
        ) {
            if (containerSize.width == 0 || containerSize.height == 0) return@Canvas
            // Кадр отрисовки идёт через Skia/Wasm без обвязки Compose-корутин — необработанное
            // исключение здесь роняет requestAnimationFrame необработанным трапом и вешает весь
            // Recomposer. Ловим и пропускаем этот кадр, а не отдаём исключение наружу.
            try {
            val maxScore = currentMaxScore()
            val (leftWorld, rightWorld) = visibleWorldRange()
            val visibleSpan = (rightWorld - leftWorld).toLong().coerceAtLeast(1)
            val plotRight = size.width - RIGHT_PAD

            // Сетка по очкам (Y).
            val scoreStep = niceScoreStep(maxScore)
            var scoreTick = scoreStep
            while (scoreTick < maxScore) {
                val y = yFor(scoreTick)
                drawLine(color = gridColor, start = Offset(LEFT_PAD, y), end = Offset(plotRight, y), strokeWidth = 1f)
                drawText(textMeasurer, scoreTick.toString(), Offset(0f, y - 6f), labelStyle)
                scoreTick += scoreStep
            }

            // Сетка по времени (X) — от начала видимого окна, а не всегда от нуля.
            val timeStep = niceTimeStepSeconds(visibleSpan)
            var timeTick = (floor(leftWorld / timeStep) * timeStep).toLong().coerceAtLeast(0)
            if (timeTick < leftWorld) timeTick += timeStep
            while (timeTick <= rightWorld) {
                val x = xFor(timeTick)
                drawLine(color = gridColor, start = Offset(x, TOP_PAD), end = Offset(x, size.height - BOTTOM_PAD), strokeWidth = 1f)
                val label = formatTime(timeTick)
                val labelWidth = textMeasurer.measure(label, labelStyle).size.width
                // coerceIn страхует Offset.x от выхода за канвас — см. комментарий у RaceGraphChart:
                // такое приводит к Constraints-исключению внутри drawText на некоторых кадрах.
                val labelX = (x - labelWidth / 2f).coerceIn(0f, (plotRight - labelWidth).coerceAtLeast(0f))
                drawText(textMeasurer, label, Offset(labelX, size.height - BOTTOM_PAD + 4f), labelStyle)
                timeTick += timeStep
            }

            // Оси.
            drawLine(
                color = axisColor.copy(alpha = 0.5f),
                start = Offset(LEFT_PAD, TOP_PAD),
                end = Offset(LEFT_PAD, size.height - BOTTOM_PAD),
                strokeWidth = 1.5f,
            )
            drawLine(
                color = axisColor.copy(alpha = 0.5f),
                start = Offset(LEFT_PAD, size.height - BOTTOM_PAD),
                end = Offset(plotRight, size.height - BOTTOM_PAD),
                strokeWidth = 1.5f,
            )

            // Линия контрольного времени группы.
            val timeLimitSeconds = data.timeLimitSeconds
            if (timeLimitSeconds != null && timeLimitSeconds in leftWorld.toLong()..rightWorld.toLong()) {
                val x = xFor(timeLimitSeconds)
                drawLine(
                    color = TIME_LIMIT_COLOR,
                    start = Offset(x, TOP_PAD),
                    end = Offset(x, size.height - BOTTOM_PAD),
                    strokeWidth = 1.5f,
                    pathEffect = dashEffect,
                )
                drawText(
                    textMeasurer,
                    "лимит",
                    Offset((x + 3f).coerceIn(0f, plotRight), TOP_PAD),
                    labelStyle.copy(color = TIME_LIMIT_COLOR),
                )
            }

            // Зум/пан двигают точки за пределы области построения — обрезаем по прямоугольнику
            // графика, а не полагаемся на границы самого Canvas (см. RaceGraphChart). Границы
            // жёстко страхуем coerceAtLeast от инвертированного прямоугольника.
            val clipRight = plotRight.coerceAtLeast(LEFT_PAD + 1f)
            val clipBottom = (size.height - BOTTOM_PAD).coerceAtLeast(TOP_PAD + 1f)
            clipRect(left = LEFT_PAD, top = 0f, right = clipRight, bottom = clipBottom) {
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
                        val isSelected = selected?.first == series.participant.id && selected?.second == point.elapsedSeconds
                        drawCircle(
                            color = color,
                            radius = if (isSelected) 6f else if (isDimmed) 2.5f else 4f,
                            center = Offset(xFor(point.elapsedSeconds), yFor(point.cumulativeScore)),
                        )
                    }
                }
            }
            } catch (e: Throwable) {
                println("ScoreGraphChart: кадр отрисовки упал: $e, scale=${zoomState.scale} centerWorld=${zoomState.centerWorld} containerSize=$containerSize")
                DebugErrorReporter.report("График очков: кадр отрисовки упал (${e.message ?: e::class.simpleName})")
            }
        }

        if (zoomState.scale > 1.0) {
            IconButton(
                onClick = { zoomState.resetTo((fullWorldMin + fullWorldMax) / 2) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), CircleShape),
            ) {
                Icon(Icons.Filled.RestartAlt, contentDescription = "Сбросить масштаб")
            }
        }

        val sel = selected
        if (sel != null) {
            val (participantId, elapsedSeconds) = sel
            val series = data.series.firstOrNull { it.participant.id == participantId }
            val point = series?.points?.firstOrNull { it.elapsedSeconds == elapsedSeconds }
            if (series != null && point != null && containerSize.width > 0) {
                val name = "${series.participant.lastName} ${series.participant.firstName}".trim()
                ChartTooltip(
                    pointX = xFor(elapsedSeconds),
                    pointY = yFor(point.cumulativeScore),
                    containerWidthPx = containerSize.width.toFloat(),
                    containerHeightPx = containerSize.height.toFloat(),
                    accentColor = raceGraphColor(data.series.indexOf(series)),
                    lines = listOf(name, "${formatTime(elapsedSeconds)} · ${point.cumulativeScore} очк."),
                )
            }
        }
    }
}
