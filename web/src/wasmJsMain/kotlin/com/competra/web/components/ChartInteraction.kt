package com.competra.web.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.competra.web.utils.DebugErrorReporter
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Состояние зума/пана линейных графиков (RaceGraphChart, ScoreGraphChart) — только по оси X
 * (единицы графика: позиция КП или секунды). Ось Y всегда авто-подгоняется каждым чартом под
 * данные, попадающие в текущий видимый диапазон X. scale=1.0 — весь диапазон данных помещается
 * в ширину графика; больше — приближение.
 */
class ChartZoomState {
    var scale: Double by mutableStateOf(1.0)
        private set
    var centerWorld: Double by mutableStateOf(0.0)
        private set

    fun visibleWidth(fullWidth: Double): Double = fullWidth / scale

    /** Сбрасывает зум к 1.0 и центрирует на [center] — вызывать при первой инициализации данных. */
    fun resetTo(center: Double) {
        scale = 1.0
        centerWorld = center
    }

    private fun clamp(worldMin: Double, worldMax: Double) {
        val fullWidth = worldMax - worldMin
        val width = visibleWidth(fullWidth)
        centerWorld = if (width >= fullWidth) {
            (worldMin + worldMax) / 2
        } else {
            centerWorld.coerceIn(worldMin + width / 2, worldMax - width / 2)
        }
    }

    fun panBy(worldDelta: Double, worldMin: Double, worldMax: Double) {
        if (!worldDelta.isFinite()) {
            println("ChartZoomState.panBy: отклонён нефинитный worldDelta=$worldDelta")
            return
        }
        centerWorld += worldDelta
        clamp(worldMin, worldMax)
    }

    /**
     * [detectTransformGestures] изредка отдаёт NaN/Infinity для zoom/pan — например, при быстром
     * pinch-жесте трекпада в момент, когда расстояние между пальцами кратковременно почти нулевое
     * (деление на ~0 в его внутреннем расчёте). Без этой проверки такое значение записывалось бы в
     * [scale]/[centerWorld] и оставалось там навсегда (NaN не проходит мимо через coerceIn — все
     * сравнения с NaN ложны), после чего все экранные координаты чарта становились NaN и рендер
     * в Skia/Wasm падал с необработанным трапом. Проверяем именно входные параметры: если они
     * конечны, то и все производные от них величины ниже тоже гарантированно конечны.
     */
    fun zoomTo(newScaleRaw: Double, focalWorld: Double, focalFraction: Double, worldMin: Double, worldMax: Double, maxScale: Double) {
        if (!newScaleRaw.isFinite() || !focalWorld.isFinite() || !focalFraction.isFinite()) {
            println("ChartZoomState.zoomTo: отклонены нефинитные newScaleRaw=$newScaleRaw focalWorld=$focalWorld focalFraction=$focalFraction")
            return
        }
        val newScale = newScaleRaw.coerceIn(1.0, maxScale)
        if (newScale == scale) return
        scale = newScale
        val newWidth = visibleWidth(worldMax - worldMin)
        centerWorld = focalWorld - (focalFraction - 0.5) * newWidth
        clamp(worldMin, worldMax)
    }
}

private const val CHART_WHEEL_ZOOM_SENSITIVITY = 1.0 / 300.0
private const val CHART_MAX_SCALE = 12.0

/**
 * Пинч/драг (detectTransformGestures — один жест на оба действия, как в [DistanceMapView]) и зум
 * колесом мыши/трекпадом, применённые только к оси X графика. [leftPad] — левый отступ области
 * построения в px, [plotWidth] читает актуальную ширину этой области на момент жеста (а не
 * замороженное значение на момент первой композиции — ширина меняется при ресайзе окна).
 */
fun Modifier.chartZoomGestures(
    state: ChartZoomState,
    worldMin: Double,
    worldMax: Double,
    leftPad: Float,
    plotWidth: () -> Float,
): Modifier {
    if (worldMax <= worldMin) return this
    return this
        // Ключ включает state (не только диапазон): у RaceGraphChart/ScoreGraphChart он
        // пересоздаётся через remember(data) при перезагрузке данных, и если новый набор данных
        // случайно даёт тот же диапазон world, жест обязан переключиться на новый объект
        // состояния, а не продолжать держать корутину со старым (иначе зум "отвяжется" от UI).
        .pointerInput(state, worldMin, worldMax) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                // Ловим Throwable, а не Exception: необработанное исключение в этой корутине
                // (например, из-за какого-то не предусмотренного здесь пограничного случая в
                // геометрии жеста) останавливает весь Compose Recomposer и вешает всё приложение,
                // а не только жест на этом графике — см. тот же паттерн в MapTiles.kt.
                try {
                    val width = plotWidth()
                    if (width <= 0f) return@detectTransformGestures
                    val fullWidth = worldMax - worldMin
                    if (pan.x != 0f) {
                        val visibleWidth = state.visibleWidth(fullWidth)
                        state.panBy(-pan.x / width * visibleWidth, worldMin, worldMax)
                    }
                    if (zoom != 1f) {
                        val visibleWidth = state.visibleWidth(fullWidth)
                        val leftWorld = state.centerWorld - visibleWidth / 2
                        val fraction = ((centroid.x - leftPad) / width).coerceIn(0f, 1f).toDouble()
                        val focalWorld = leftWorld + fraction * visibleWidth
                        state.zoomTo(state.scale * zoom, focalWorld, fraction, worldMin, worldMax, CHART_MAX_SCALE)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    println("chartZoomGestures (пинч/драг) упал: $e, centroid=$centroid pan=$pan zoom=$zoom")
                    DebugErrorReporter.report("Зум графика: жест пинч/драг упал (${e.message ?: e::class.simpleName})")
                }
            }
        }
        .pointerInput(state, worldMin, worldMax) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type != PointerEventType.Scroll) continue
                    val change = event.changes.firstOrNull() ?: continue
                    try {
                        val width = plotWidth()
                        if (width <= 0f) continue
                        val fullWidth = worldMax - worldMin
                        val visibleWidth = state.visibleWidth(fullWidth)
                        val leftWorld = state.centerWorld - visibleWidth / 2
                        val fraction = ((change.position.x - leftPad) / width).coerceIn(0f, 1f).toDouble()
                        val focalWorld = leftWorld + fraction * visibleWidth
                        val deltaZoom = -change.scrollDelta.y * CHART_WHEEL_ZOOM_SENSITIVITY
                        state.zoomTo(state.scale * 2.0.pow(deltaZoom), focalWorld, fraction, worldMin, worldMax, CHART_MAX_SCALE)
                        change.consume()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        println("chartZoomGestures (колесо) упал: $e, scrollDelta=${change.scrollDelta}")
                        DebugErrorReporter.report("Зум графика: колесо мыши упало (${e.message ?: e::class.simpleName})")
                    }
                }
            }
        }
}

/**
 * Всплывающая подсказка с данными точки графика, позиционируется рядом с [pointX]/[pointY]
 * (px внутри контейнера графика) и отражается к противоположному краю, если не помещается.
 */
@Composable
fun ChartTooltip(
    pointX: Float,
    pointY: Float,
    containerWidthPx: Float,
    containerHeightPx: Float,
    accentColor: Color,
    lines: List<String>,
) {
    val density = LocalDensity.current
    val tooltipWidthDp = 160.dp
    val tooltipWidthPx = with(density) { tooltipWidthDp.toPx() }
    val lineHeightPx = with(density) { 18.dp.toPx() }
    val tooltipHeightPx = lines.size * lineHeightPx + with(density) { 16.dp.toPx() }
    val gap = with(density) { 12.dp.toPx() }

    val flipLeft = pointX + gap + tooltipWidthPx > containerWidthPx
    val tooltipX = (if (flipLeft) pointX - gap - tooltipWidthPx else pointX + gap)
        .coerceIn(0f, (containerWidthPx - tooltipWidthPx).coerceAtLeast(0f))
    val tooltipY = (pointY - tooltipHeightPx / 2)
        .coerceIn(0f, (containerHeightPx - tooltipHeightPx).coerceAtLeast(0f))

    Box(
        modifier = Modifier
            .offset { IntOffset(tooltipX.roundToInt(), tooltipY.roundToInt()) }
            .width(tooltipWidthDp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .border(1.dp, accentColor, RoundedCornerShape(8.dp))
            .padding(8.dp),
    ) {
        Column {
            lines.forEachIndexed { index, line ->
                Text(
                    line,
                    style = if (index == 0) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelSmall,
                    fontWeight = if (index == 0) FontWeight.Bold else FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}
