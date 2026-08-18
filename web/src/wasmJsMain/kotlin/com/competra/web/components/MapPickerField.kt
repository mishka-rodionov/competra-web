package com.competra.web.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.competra.web.utils.OSM_TILE_SIZE
import com.competra.web.utils.latToTileY
import com.competra.web.utils.loadTileBitmap
import com.competra.web.utils.lonToTileX
import com.competra.web.utils.tileXToLon
import com.competra.web.utils.tileYToLat
import kotlinx.coroutines.launch

private const val DEFAULT_ZOOM = 14
private const val MIN_ZOOM = 3
private const val MAX_ZOOM = 18
private const val MOSCOW_LAT = 55.75222
private const val MOSCOW_LON = 37.61556
private const val TILE_LOAD_RANGE = 2

/**
 * Панорамируемая карта для выбора координат старта. Карта неподвижна под перекрестием в
 * центре — пользователь двигает саму карту, а не маркер (тот же UX, что и в Android MapPickerScreen).
 * Тайлы — OpenStreetMap, отрисованы вручную на Canvas: Compose Multiplatform для wasmJs не даёт
 * встраивать произвольный DOM (Leaflet/MapLibre), а Vico-подобных карт-виджетов для Wasm нет.
 */
@Composable
fun MapPickerField(
    latitude: Double?,
    longitude: Double?,
    onPick: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var centerLat by remember { mutableStateOf(latitude ?: MOSCOW_LAT) }
    var centerLon by remember { mutableStateOf(longitude ?: MOSCOW_LON) }
    var zoom by remember { mutableIntStateOf(DEFAULT_ZOOM) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val loadedTiles = remember { mutableStateMapOf<String, ImageBitmap>() }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(280.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragOffset += dragAmount
                        },
                        onDragEnd = {
                            val tilesPerSide = (1 shl zoom).toDouble()
                            val deltaTileX = -dragOffset.x / OSM_TILE_SIZE
                            val deltaTileY = -dragOffset.y / OSM_TILE_SIZE
                            val newTileX = (lonToTileX(centerLon, zoom) + deltaTileX).coerceIn(0.0, tilesPerSide)
                            val newTileY = (latToTileY(centerLat, zoom) + deltaTileY).coerceIn(0.0, tilesPerSide)
                            centerLon = tileXToLon(newTileX, zoom)
                            centerLat = tileYToLat(newTileY, zoom)
                            dragOffset = Offset.Zero
                        },
                    )
                },
        ) {
            val centerTileX = lonToTileX(centerLon, zoom)
            val centerTileY = latToTileY(centerLat, zoom)
            val baseTileX = kotlin.math.floor(centerTileX).toInt()
            val baseTileY = kotlin.math.floor(centerTileY).toInt()

            Canvas(modifier = Modifier.fillMaxWidth().height(280.dp)) {
                val viewportW = size.width
                val viewportH = size.height
                val maxTile = (1 shl zoom) - 1

                for (dx in -TILE_LOAD_RANGE..TILE_LOAD_RANGE) {
                    for (dy in -TILE_LOAD_RANGE..TILE_LOAD_RANGE) {
                        val tx = baseTileX + dx
                        val ty = baseTileY + dy
                        if (tx < 0 || ty < 0 || tx > maxTile || ty > maxTile) continue

                        val screenX = (tx - centerTileX) * OSM_TILE_SIZE + viewportW / 2f + dragOffset.x
                        val screenY = (ty - centerTileY) * OSM_TILE_SIZE + viewportH / 2f + dragOffset.y

                        val bitmap = loadedTiles["$zoom/$tx/$ty"]
                        if (bitmap != null) {
                            drawImage(bitmap, topLeft = Offset(screenX.toFloat(), screenY.toFloat()))
                        }
                    }
                }

                val cx = viewportW / 2f
                val cy = viewportH / 2f
                val arm = 12.dp.toPx()
                drawLine(Color.Red, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
                drawLine(Color.Red, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)
            }

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

            Column(modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                FilledIconButton(onClick = { if (zoom < MAX_ZOOM) zoom++ }) { Text("+") }
                FilledIconButton(onClick = { if (zoom > MIN_ZOOM) zoom-- }, modifier = Modifier.padding(top = 4.dp)) { Text("−") }
            }
        }

        Text(
            "${formatCoord(centerLat)}, ${formatCoord(centerLon)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )

        Button(
            onClick = { onPick(roundCoord(centerLat), roundCoord(centerLon)) },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        ) {
            Text("Зафиксировать координаты старта")
        }
    }
}

/**
 * Поле координат для форм-визардов: показывает текущее значение и кнопку, открывающую
 * [MapPickerField] в [Dialog]. [MapPickerField] рисует тайлы на [Canvas] и внутри скроллящегося
 * списка (LazyColumn/Column.verticalScroll) на wasmJs-таргете съезжает с места при скролле —
 * Canvas там не переезжает вместе с остальным контентом. Диалог не скроллится, поэтому проблема
 * не проявляется.
 */
@Composable
fun CoordinatesPickerField(
    latitude: Double?,
    longitude: Double?,
    onPick: (latitude: Double, longitude: Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Text(
            if (latitude != null && longitude != null) "${formatCoord(latitude)}, ${formatCoord(longitude)}" else "Не указаны",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedButton(onClick = { showPicker = true }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(if (latitude != null) "Изменить на карте" else "Выбрать на карте")
        }
    }

    if (showPicker) {
        Dialog(onDismissRequest = { showPicker = false }) {
            Surface(shape = MaterialTheme.shapes.medium) {
                Box(modifier = Modifier.padding(16.dp)) {
                    MapPickerField(
                        latitude = latitude,
                        longitude = longitude,
                        onPick = { lat, lon -> onPick(lat, lon); showPicker = false },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

private fun roundCoord(value: Double): Double = kotlin.math.round(value * 100_000.0) / 100_000.0

private fun formatCoord(value: Double): String = roundCoord(value).toString()
