package com.competra.web.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.competra.domain.diary.TrackPoint
import com.competra.web.utils.OSM_TILE_SIZE
import com.competra.web.utils.latToTileY
import com.competra.web.utils.loadTileBitmap
import com.competra.web.utils.lonToTileX
import kotlinx.coroutines.launch

private const val MIN_ZOOM = 3
private const val MAX_ZOOM = 18
private const val TILE_LOAD_RANGE = 2

/** Отступ вокруг bounding box трека при подборе зума — трек не должен упираться в края. */
private const val BOUNDS_PADDING_FACTOR = 1.25

private fun bestFitZoom(
    minLat: Double, maxLat: Double, minLon: Double, maxLon: Double,
    viewportW: Float, viewportH: Float,
): Int {
    for (z in MAX_ZOOM downTo MIN_ZOOM) {
        val x1 = lonToTileX(minLon, z)
        val x2 = lonToTileX(maxLon, z)
        val y1 = latToTileY(maxLat, z)
        val y2 = latToTileY(minLat, z)
        val widthPx = (x2 - x1) * OSM_TILE_SIZE * BOUNDS_PADDING_FACTOR
        val heightPx = (y2 - y1) * OSM_TILE_SIZE * BOUNDS_PADDING_FACTOR
        if (widthPx <= viewportW && heightPx <= viewportH) return z
    }
    return MIN_ZOOM
}

/**
 * Неинтерактивная карта с треком тренировки: тайлы OSM отрисованы вручную на Canvas
 * (тот же подход, что в MapPickerField.kt), поверх — полилиния по точкам трека с
 * автоматическим подбором зума/центра по bounding box. Без пана и зума жестами.
 */
@Composable
fun TrackMapView(points: List<TrackPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Text("Нет данных трека", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val loadedTiles = remember { mutableStateMapOf<String, ImageBitmap>() }
    val scope = rememberCoroutineScope()

    val minLat = points.minOf { it.lat }
    val maxLat = points.maxOf { it.lat }
    val minLon = points.minOf { it.lon }
    val maxLon = points.maxOf { it.lon }
    val centerLat = (minLat + maxLat) / 2
    val centerLon = (minLon + maxLon) / 2

    Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant).onSizeChanged { containerSize = it }) {
        if (containerSize.width == 0 || containerSize.height == 0) return@Box

        val zoom = bestFitZoom(minLat, maxLat, minLon, maxLon, containerSize.width.toFloat(), containerSize.height.toFloat())
        val centerTileX = lonToTileX(centerLon, zoom)
        val centerTileY = latToTileY(centerLat, zoom)
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
                    val screenX = (tx - centerTileX) * OSM_TILE_SIZE + viewportW / 2f
                    val screenY = (ty - centerTileY) * OSM_TILE_SIZE + viewportH / 2f
                    val bitmap = loadedTiles["$zoom/$tx/$ty"]
                    if (bitmap != null) {
                        drawImage(bitmap, topLeft = Offset(screenX.toFloat(), screenY.toFloat()))
                    }
                }
            }

            fun project(point: TrackPoint): Offset {
                val tx = lonToTileX(point.lon, zoom)
                val ty = latToTileY(point.lat, zoom)
                val x = (tx - centerTileX) * OSM_TILE_SIZE + viewportW / 2f
                val y = (ty - centerTileY) * OSM_TILE_SIZE + viewportH / 2f
                return Offset(x.toFloat(), y.toFloat())
            }

            val screenPoints = points.map(::project)
            for (i in 0 until screenPoints.size - 1) {
                drawLine(
                    color = Color(0xFF2962FF),
                    start = screenPoints[i],
                    end = screenPoints[i + 1],
                    strokeWidth = 4.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            val startPoint = screenPoints.first()
            val endPoint = screenPoints.last()
            drawCircle(color = Color(0xFF2E7D32), radius = 6.dp.toPx(), center = startPoint)
            drawCircle(color = Color(0xFFC62828), radius = 6.dp.toPx(), center = endPoint)
        }
    }
}
