package com.competra.web.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.competra.web.utils.MAX_MAP_ZOOM
import com.competra.web.utils.MIN_MAP_ZOOM
import com.competra.web.utils.OSM_TILE_SIZE
import com.competra.web.utils.bestFitZoom
import com.competra.web.utils.latToTileY
import com.competra.web.utils.loadImageBitmapFromUrl
import com.competra.web.utils.loadTileBitmap
import com.competra.web.utils.lonToTileX
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val TILE_LOAD_RANGE = 2

/** Отступ вокруг карты дистанции при подборе зума — карта не должна упираться в края. */
private const val BOUNDS_PADDING_FACTOR = 1.1

/** Шаг одного клика по +/-: 2^0.5 ≈ 1.41x размера — заметно, но не "прыжок через весь экран". */
private const val ZOOM_STEP = 0.5

/**
 * Карта дистанции: тайлы OSM, поверх — растровая карта соревнования (экспорт из mapper),
 * наложенная по её WGS84-углам. Карта считается north-up (без поворота) — так печатаются
 * ориентировочные карты по конвенции ИОФ.
 *
 * По умолчанию (`interactive = false`) — статичный превью-режим: авто bounding box по углам
 * карты, без жестов, используется в компактной карточке дистанции. С `interactive = true`
 * (используется в развёрнутом виде, [com.competra.web.pages.ExpandedDistanceMap]) появляется
 * драг для перемещения и кнопки +/- для зума.
 *
 * Зум непрерывный (Double), а не по целым уровням тайлов: OSM отдаёт тайлы только на целых
 * zoom, поэтому тайлы всегда грузятся под ближайший целый уровень ([tileZoomOf]), а разница
 * между ним и текущим "эффективным" зумом компенсируется масштабом при отрисовке — этим же
 * масштабом рисуется и растровая карта поверх, так что оба слоя всегда пропорциональны друг
 * другу и один клик по +/- не даёт разрыв в 2x.
 */
@Composable
fun DistanceMapView(
    mapUrl: String,
    topLeftLat: Double,
    topLeftLng: Double,
    bottomRightLat: Double,
    bottomRightLng: Double,
    modifier: Modifier = Modifier,
    interactive: Boolean = false,
) {
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val loadedTiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    var mapImage by remember(mapUrl) { mutableStateOf<ImageBitmap?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(mapUrl) {
        mapImage = loadImageBitmapFromUrl(mapUrl)
    }

    val minLat = minOf(topLeftLat, bottomRightLat)
    val maxLat = maxOf(topLeftLat, bottomRightLat)
    val minLon = minOf(topLeftLng, bottomRightLng)
    val maxLon = maxOf(topLeftLng, bottomRightLng)
    val fitCenterLat = (minLat + maxLat) / 2
    val fitCenterLon = (minLon + maxLon) / 2

    // Используются только в interactive-режиме: подбираются один раз при первом появлении
    // размеров контейнера, дальше меняются жестами (drag) и кнопками зума, а не пересчитываются
    // заново при каждой рекомпозиции — иначе любой жест сразу же "отскакивал" бы обратно к fit.
    // interactiveZoom — непрерывный "эффективный" зум; interactiveCenterTileX/Y хранятся в
    // тайловых координатах уровня tileZoomOf(interactiveZoom) на момент последнего изменения.
    var interactiveZoom by remember { mutableStateOf<Double?>(null) }
    var interactiveCenterTileX by remember { mutableStateOf(0.0) }
    var interactiveCenterTileY by remember { mutableStateOf(0.0) }

    fun tileZoomOf(effectiveZoom: Double) = effectiveZoom.roundToInt().coerceIn(MIN_MAP_ZOOM, MAX_MAP_ZOOM)
    fun scaleOf(effectiveZoom: Double) = 2.0.pow(effectiveZoom - tileZoomOf(effectiveZoom))

    // Инициализация fit-зума для interactive-режима — строго один раз за всё время жизни
    // composable (иначе запись состояния на каждое изменение containerSize рискует зациклиться,
    // если размер контейнера хоть немного "дрожит" между кадрами). Ждём через snapshotFlow +
    // debounce, пока containerSize перестанет меняться, чтобы не поймать промежуточный маленький
    // размер, который Compose иногда репортит до того, как раскладка полноэкранного оверлея
    // устаканится — раньше это приводило к заниженному стартовому зуму.
    LaunchedEffect(interactive) {
        if (!interactive) return@LaunchedEffect
        snapshotFlow { containerSize }
            .filter { it.width > 0 && it.height > 0 }
            .debounce(150)
            .first()
            .let { size ->
                val fitZoom = bestFitZoom(minLat, maxLat, minLon, maxLon, size.width.toFloat(), size.height.toFloat(), BOUNDS_PADDING_FACTOR)
                interactiveZoom = fitZoom.toDouble()
                interactiveCenterTileX = lonToTileX(fitCenterLon, fitZoom)
                interactiveCenterTileY = latToTileY(fitCenterLat, fitZoom)
            }
    }

    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .onSizeChanged { containerSize = it }
            .let { base ->
                if (!interactive) base else base.pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        val effZoom = interactiveZoom ?: return@detectDragGestures
                        val effectiveTileSize = OSM_TILE_SIZE * scaleOf(effZoom)
                        interactiveCenterTileX -= dragAmount.x / effectiveTileSize
                        interactiveCenterTileY -= dragAmount.y / effectiveTileSize
                    }
                }
            },
    ) {
        if (containerSize.width == 0 || containerSize.height == 0) return@Box
        if (interactive && interactiveZoom == null) return@Box

        val zoom: Int
        val scale: Double
        val centerTileX: Double
        val centerTileY: Double
        if (interactive) {
            val effZoom = interactiveZoom!!
            zoom = tileZoomOf(effZoom)
            scale = scaleOf(effZoom)
            centerTileX = interactiveCenterTileX
            centerTileY = interactiveCenterTileY
        } else {
            zoom = bestFitZoom(minLat, maxLat, minLon, maxLon, containerSize.width.toFloat(), containerSize.height.toFloat(), BOUNDS_PADDING_FACTOR)
            scale = 1.0
            centerTileX = lonToTileX(fitCenterLon, zoom)
            centerTileY = latToTileY(fitCenterLat, zoom)
        }
        val effectiveTileSize = (OSM_TILE_SIZE * scale).toFloat()
        val baseTileX = kotlin.math.floor(centerTileX).toInt()
        val baseTileY = kotlin.math.floor(centerTileY).toInt()

        LaunchedEffect(baseTileX, baseTileY, zoom) {
            val maxTile = (1 shl zoom) - 1
            for (dx in -TILE_LOAD_RANGE..TILE_LOAD_RANGE) {
                for (dy in -TILE_LOAD_RANGE..TILE_LOAD_RANGE) {
                    val tx = baseTileX + dx
                    val ty = baseTileY + dy
                    if (tx < 0 || ty < 0 || tx > maxTile || ty > maxTile) continue
                    val key = "$zoom/$tx/$ty"
                    if (loadedTiles.containsKey(key)) continue
                    scope.launch {
                        val bitmap = loadTileBitmap(zoom, tx, ty)
                        if (bitmap != null) loadedTiles[key] = bitmap
                    }
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val viewportW = size.width
            val viewportH = size.height
            val maxTile = (1 shl zoom) - 1

            for (dx in -TILE_LOAD_RANGE..TILE_LOAD_RANGE) {
                for (dy in -TILE_LOAD_RANGE..TILE_LOAD_RANGE) {
                    val tx = baseTileX + dx
                    val ty = baseTileY + dy
                    if (tx < 0 || ty < 0 || tx > maxTile || ty > maxTile) continue
                    val screenX = (tx - centerTileX) * effectiveTileSize + viewportW / 2f
                    val screenY = (ty - centerTileY) * effectiveTileSize + viewportH / 2f
                    val bitmap = loadedTiles["$zoom/$tx/$ty"]
                    if (bitmap != null) {
                        drawImage(
                            image = bitmap,
                            dstOffset = IntOffset(screenX.toInt(), screenY.toInt()),
                            dstSize = IntSize(effectiveTileSize.toInt().coerceAtLeast(1), effectiveTileSize.toInt().coerceAtLeast(1)),
                        )
                    }
                }
            }

            mapImage?.let { img ->
                fun screenX(lon: Double) = ((lonToTileX(lon, zoom) - centerTileX) * effectiveTileSize + viewportW / 2f).toFloat()
                fun screenY(lat: Double) = ((latToTileY(lat, zoom) - centerTileY) * effectiveTileSize + viewportH / 2f).toFloat()
                val left = screenX(topLeftLng)
                val top = screenY(topLeftLat)
                val right = screenX(bottomRightLng)
                val bottom = screenY(bottomRightLat)
                drawImage(
                    image = img,
                    dstOffset = IntOffset(left.toInt(), top.toInt()),
                    dstSize = IntSize((right - left).toInt().coerceAtLeast(1), (bottom - top).toInt().coerceAtLeast(1)),
                )
            }
        }

        if (mapImage == null) {
            // Статичный текст без анимации — индикатор с непрерывной анимацией (CircularProgressIndicator)
            // здесь ранее коррелировал с зависанием страницы на этом Skiko-канвасе.
            Text(
                "Загрузка карты — файл большой, может занять минуту",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(MaterialTheme.shapes.small)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        if (interactive) {
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            ) {
                ZoomButton(icon = Icons.Filled.Add, contentDescription = "Приблизить") {
                    val current = interactiveZoom ?: return@ZoomButton
                    val next = (current + ZOOM_STEP).coerceIn(MIN_MAP_ZOOM.toDouble(), MAX_MAP_ZOOM.toDouble())
                    if (next != current) {
                        val oldTileZoom = tileZoomOf(current)
                        val newTileZoom = tileZoomOf(next)
                        if (newTileZoom != oldTileZoom) {
                            val factor = 2.0.pow(newTileZoom - oldTileZoom)
                            interactiveCenterTileX *= factor
                            interactiveCenterTileY *= factor
                        }
                        interactiveZoom = next
                    }
                }
                ZoomButton(icon = Icons.Filled.Remove, contentDescription = "Отдалить") {
                    val current = interactiveZoom ?: return@ZoomButton
                    val next = (current - ZOOM_STEP).coerceIn(MIN_MAP_ZOOM.toDouble(), MAX_MAP_ZOOM.toDouble())
                    if (next != current) {
                        val oldTileZoom = tileZoomOf(current)
                        val newTileZoom = tileZoomOf(next)
                        if (newTileZoom != oldTileZoom) {
                            val factor = 2.0.pow(newTileZoom - oldTileZoom)
                            interactiveCenterTileX *= factor
                            interactiveCenterTileY *= factor
                        }
                        interactiveZoom = next
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomButton(icon: androidx.compose.ui.graphics.vector.ImageVector, contentDescription: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(4.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
    ) {
        IconButton(onClick = onClick) {
            Icon(icon, contentDescription = contentDescription)
        }
    }
}
