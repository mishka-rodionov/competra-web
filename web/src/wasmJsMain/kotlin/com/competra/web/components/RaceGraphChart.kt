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
import com.competra.domain.models.RaceGraphData
import com.competra.web.utils.DebugErrorReporter
import com.competra.web.utils.formatTime
import kotlin.coroutines.cancellation.CancellationException

/** Циклическая палитра линий графика — 12 цветов, как в Android-версии. */
val raceGraphPalette = listOf(
    Color(0xFF1E88E5), Color(0xFFD81B60), Color(0xFF43A047), Color(0xFFFB8C00),
    Color(0xFF8E24AA), Color(0xFF00ACC1), Color(0xFFF4511E), Color(0xFF3949AB),
    Color(0xFF6D4C41), Color(0xFFC0CA33), Color(0xFF00897B), Color(0xFFE53935),
)

fun raceGraphColor(index: Int): Color = raceGraphPalette[index % raceGraphPalette.size]

private const val CHART_HEIGHT_DP = 300
private const val LEFT_PAD = 48f
private const val RIGHT_PAD = 28f
private const val BOTTOM_PAD = 24f
private const val TOP_PAD = 8f
private const val TAP_HIT_RADIUS_DP = 24

/**
 * Линейный график отставания от лидера. Vico не публикует таргет wasmJs, поэтому график
 * нарисован вручную на Compose Canvas — модель данных (RaceGraphData) при этом та же, что
 * и в Android-версии, так что при появлении wasm-совместимого чарт-движка отрисовку можно
 * будет заменить, не трогая бизнес-логику.
 *
 * Ось X (позиция КП) зумируется и панится пинчем/колесом мыши через [ChartZoomState] — ось Y
 * при этом каждый раз пересчитывается под то, что реально попало в видимый диапазон X, чтобы
 * зум давал не только "растянутую" картинку, но и более точную шкалу. Тап по точке показывает
 * тултип с именем участника и отставанием на КП.
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
        Box(modifier = modifier.fillMaxWidth().height(CHART_HEIGHT_DP.dp), contentAlignment = Alignment.Center) {
            Text("Нет участников для отображения на графике", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val columnCount = data.columns.size
    // 0 — синтетическая точка старта (добавлена в buildRaceGraphData), а не первый КП: без неё
    // кривые расходились бы уже с первого КП и выглядело бы, будто график "не сходится".
    val fullWorldMin = 0.0
    val fullWorldMax = columnCount.coerceAtLeast(1).toDouble()
    val zoomState = remember(data) { ChartZoomState().apply { resetTo((fullWorldMin + fullWorldMax) / 2) } }
    var containerSize by remember(data) { mutableStateOf(IntSize.Zero) }
    var selected by remember(data) { mutableStateOf<Pair<String, Int>?>(null) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = TextStyle(fontSize = 10.sp, color = axisColor)

    // xFor/yFor читают zoomState/containerSize напрямую при каждом вызове (а не через
    // захваченные заранее вычисленные значения), поэтому их можно безопасно звать из
    // pointerInput-обработчика тапа — он может пережить несколько актов зума/пана без
    // перезапуска корутины и не должен получать устаревшие координаты.
    fun currentMaxDelta(): Long {
        val visW = zoomState.visibleWidth(fullWorldMax - fullWorldMin)
        val leftWorld = zoomState.centerWorld - visW / 2
        val rightWorld = zoomState.centerWorld + visW / 2
        return visibleSeries.flatMap { it.points }
            .filter { it.positionIndex >= leftWorld - 1 && it.positionIndex <= rightWorld + 1 }
            .mapNotNull { it.deltaSeconds }
            .maxOrNull() ?: 0L
    }

    // Финальная страховка: если где-то выше в цепочке вычислений всё же проскочило NaN/Infinity
    // (например, из-за пограничного случая, который мы не предусмотрели), эти функции не должны
    // отдавать его дальше в Skia — там такое координатное значение роняет кадр отрисовки без
    // возможности поймать это Kotlin-исключением (см. RaceGraphPage/ChartInteraction).
    fun xFor(positionIndex: Int): Float {
        val plotWidth = (containerSize.width - LEFT_PAD - RIGHT_PAD).coerceAtLeast(0f)
        val visW = zoomState.visibleWidth(fullWorldMax - fullWorldMin)
        val leftWorld = zoomState.centerWorld - visW / 2
        val raw = LEFT_PAD + plotWidth * ((positionIndex - leftWorld) / visW).toFloat()
        return if (raw.isFinite()) raw else LEFT_PAD
    }

    fun yFor(deltaSeconds: Long): Float {
        val plotHeight = (containerSize.height - BOTTOM_PAD - TOP_PAD).coerceAtLeast(0f)
        val maxDelta = currentMaxDelta()
        val raw = if (maxDelta <= 0L) TOP_PAD else TOP_PAD + plotHeight * (deltaSeconds / maxDelta.toFloat())
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
                            var bestPosition = 0
                            var bestDistSq = Float.MAX_VALUE
                            visibleSeries.forEach { series ->
                                series.points.forEach { point ->
                                    val delta = point.deltaSeconds ?: return@forEach
                                    val dx = xFor(point.positionIndex) - offset.x
                                    val dy = yFor(delta) - offset.y
                                    val distSq = dx * dx + dy * dy
                                    if (distSq < bestDistSq) {
                                        bestDistSq = distSq
                                        bestId = series.participant.id
                                        bestPosition = point.positionIndex
                                    }
                                }
                            }
                            selected = if (bestId != null && bestDistSq <= hitRadiusPx * hitRadiusPx) bestId to bestPosition else null
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Throwable) {
                            println("RaceGraphChart: тап по точке упал: $e, offset=$offset")
                            DebugErrorReporter.report("График гонки: тап по точке упал (${e.message ?: e::class.simpleName})")
                        }
                    })
                },
        ) {
            if (containerSize.width == 0 || containerSize.height == 0 || columnCount == 0) return@Canvas
            // Кадр отрисовки идёт через Skia/Wasm без обвязки Compose-корутин — необработанное
            // исключение здесь (например, неучтённый пограничный случай в координатах при зуме)
            // роняет requestAnimationFrame необработанным трапом и вешает весь Recomposer. Ловим
            // и пропускаем этот кадр, а не отдаём исключение наружу.
            try {
            val maxDeltaSeconds = currentMaxDelta()
            val plotRight = size.width - RIGHT_PAD

            // Нулевая линия лидера.
            drawLine(
                color = axisColor.copy(alpha = 0.5f),
                start = Offset(LEFT_PAD, TOP_PAD),
                end = Offset(plotRight, TOP_PAD),
                strokeWidth = 1.5f,
            )
            drawText(textMeasurer, "0:00", Offset(0f, TOP_PAD - 6f), labelStyle)
            val step = niceTimeStepSeconds(maxDeltaSeconds)
            var tick = step
            while (tick < maxDeltaSeconds) {
                val y = yFor(tick)
                drawLine(
                    color = axisColor.copy(alpha = 0.12f),
                    start = Offset(LEFT_PAD, y),
                    end = Offset(plotRight, y),
                    strokeWidth = 1f,
                )
                // Отставание от лидера по смыслу неотрицательное — подписываем положительным
                // временем без минуса, а не "-5:00".
                drawText(textMeasurer, formatTime(tick), Offset(0f, y - 6f), labelStyle)
                tick += step
            }

            // Подписи оси X рисуются только пока точка реально видна в канвасе (без запаса за
            // край) — offset.x, ушедший за size.width, у некоторых реализаций drawText приводит к
            // отрицательному "доступному под текст" width при внутреннем измерении текста, а
            // Constraints с maxWidth < minWidth падает исключением (см. кадр отрисовки выше).
            // coerceIn — вторая, независимая от видимости, страховка на сам рисуемый Offset.
            run {
                val x = xFor(0)
                if (x >= LEFT_PAD && x <= plotRight) {
                    val labelX = (x - 12f).coerceIn(0f, plotRight)
                    drawText(textMeasurer, "Старт", Offset(labelX, size.height - BOTTOM_PAD + 4f), labelStyle)
                }
            }
            data.columns.forEach { column ->
                val x = xFor(column.positionIndex)
                if (x < LEFT_PAD || x > plotRight) return@forEach
                val labelX = (x - 6f).coerceIn(0f, plotRight)
                drawText(
                    textMeasurer,
                    column.controlPoint.toString(),
                    Offset(labelX, size.height - BOTTOM_PAD + 4f),
                    labelStyle,
                )
            }

            // Зум/пан двигают точки за пределы области построения (например, соседняя видимая
            // точка тянет отрезок линии влево от LEFT_PAD, поверх подписей оси) — обрезаем по
            // прямоугольнику графика, а не полагаемся на границы самого Canvas. Границы жёстко
            // страхуем coerceAtLeast: инвертированный прямоугольник (right<left/bottom<top) при
            // экстремальном ресайзе окна — вероятный кандидат на трап в Skia/Wasm, который не
            // ловится Kotlin-catch (см. комментарий у ChartZoomState.zoomTo).
            val clipRight = plotRight.coerceAtLeast(LEFT_PAD + 1f)
            val clipBottom = (size.height - BOTTOM_PAD).coerceAtLeast(TOP_PAD + 1f)
            clipRect(left = LEFT_PAD, top = 0f, right = clipRight, bottom = clipBottom) {
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
                        val isSelected = selected?.first == series.participant.id && selected?.second == point.positionIndex
                        drawCircle(
                            color = color,
                            radius = if (isSelected) 6f else if (isDimmed) 2.5f else 4f,
                            center = Offset(xFor(point.positionIndex), yFor(point.deltaSeconds!!)),
                        )
                    }
                }
            }
            } catch (e: Throwable) {
                println("RaceGraphChart: кадр отрисовки упал: $e, scale=${zoomState.scale} centerWorld=${zoomState.centerWorld} containerSize=$containerSize")
                DebugErrorReporter.report("График гонки: кадр отрисовки упал (${e.message ?: e::class.simpleName})")
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
            val (participantId, positionIndex) = sel
            val series = data.series.firstOrNull { it.participant.id == participantId }
            val point = series?.points?.firstOrNull { it.positionIndex == positionIndex }
            val delta = point?.deltaSeconds
            val positionLabel = if (positionIndex == 0) {
                "Старт"
            } else {
                data.columns.firstOrNull { it.positionIndex == positionIndex }?.controlPoint?.let { "КП $it" }
            }
            if (series != null && delta != null && positionLabel != null && containerSize.width > 0) {
                val name = "${series.participant.lastName} ${series.participant.firstName}".trim()
                val deltaLabel = if (delta == 0L) "Лидер" else formatTime(delta)
                ChartTooltip(
                    pointX = xFor(positionIndex),
                    pointY = yFor(delta),
                    containerWidthPx = containerSize.width.toFloat(),
                    containerHeightPx = containerSize.height.toFloat(),
                    accentColor = raceGraphColor(data.series.indexOf(series)),
                    lines = listOf(name, "$positionLabel · $deltaLabel"),
                )
            }
        }
    }
}
