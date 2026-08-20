package com.competra.web.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sinh
import kotlin.math.tan
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image

/** Число тайлов по одной стороне на заданном зуме (2^zoom). */
fun tilesPerSide(zoom: Int): Double = 2.0.pow(zoom)

/** Дробная координата тайла X для долготы. */
fun lonToTileX(lon: Double, zoom: Int): Double = (lon + 180.0) / 360.0 * tilesPerSide(zoom)

/** Дробная координата тайла Y для широты (Web Mercator). */
fun latToTileY(lat: Double, zoom: Int): Double {
    val latRad = lat * PI / 180.0
    return (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * tilesPerSide(zoom)
}

/** Долгота по дробной координате тайла X. */
fun tileXToLon(x: Double, zoom: Int): Double = x / tilesPerSide(zoom) * 360.0 - 180.0

/** Широта по дробной координате тайла Y (Web Mercator). */
fun tileYToLat(y: Double, zoom: Int): Double {
    val n = PI - 2.0 * PI * y / tilesPerSide(zoom)
    return 180.0 / PI * atan(sinh(n))
}

const val OSM_TILE_SIZE = 256

const val MIN_MAP_ZOOM = 3
const val MAX_MAP_ZOOM = 18

fun osmTileUrl(zoom: Int, x: Int, y: Int): String = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

private val tileHttpClient = HttpClient()

/** Общий на весь модуль кэш загруженных тайлов OSM, переиспользуется всеми canvas-картами. */
val tileBitmapCache = mutableMapOf<String, ImageBitmap>()

/** Кэш произвольных растровых изображений (например, карт дистанций), ключ — их URL. */
private val urlBitmapCache = mutableMapOf<String, ImageBitmap>()

/**
 * Ключи тайлов и URL, для которых загрузка уже завершилась неудачей — не повторяем попытку
 * при каждом перезапуске LaunchedEffect. Без этого каждая рекомпозиция карты (а их может быть
 * много подряд, например пока идёт fit) заново лупит сеть по заведомо недоступному хосту
 * (см. политику tile.openstreetmap.org), что ощущается как зависание страницы.
 */
private val failedKeys = mutableSetOf<String>()

private fun decodeToImageBitmap(bytes: ByteArray): ImageBitmap {
    val image = Image.makeFromEncoded(bytes)
    val skiaBitmap = Bitmap().apply {
        allocPixels(image.imageInfo)
        image.readPixels(this)
        setImmutable()
    }
    return skiaBitmap.asComposeImageBitmap()
}

suspend fun loadTileBitmap(zoom: Int, x: Int, y: Int): ImageBitmap? {
    val key = "$zoom/$x/$y"
    tileBitmapCache[key]?.let { return it }
    if (key in failedKeys) return null
    return try {
        val bytes = tileHttpClient.get(osmTileUrl(zoom, x, y)).bodyAsBytes()
        val bitmap = decodeToImageBitmap(bytes)
        tileBitmapCache[key] = bitmap
        bitmap
    } catch (e: Exception) {
        failedKeys += key
        null
    }
}

/** Загружает произвольное растровое изображение по URL (например, экспортированную из mapper карту дистанции). */
suspend fun loadImageBitmapFromUrl(url: String): ImageBitmap? {
    urlBitmapCache[url]?.let { return it }
    if (url in failedKeys) return null
    return try {
        val bytes = tileHttpClient.get(url).bodyAsBytes()
        val bitmap = decodeToImageBitmap(bytes)
        urlBitmapCache[url] = bitmap
        bitmap
    } catch (e: Exception) {
        failedKeys += url
        null
    }
}

/** Подбирает максимальный зум, на котором bounding box (с отступом paddingFactor) помещается в viewport. */
fun bestFitZoom(
    minLat: Double, maxLat: Double, minLon: Double, maxLon: Double,
    viewportW: Float, viewportH: Float,
    paddingFactor: Double = 1.25,
): Int {
    for (z in MAX_MAP_ZOOM downTo MIN_MAP_ZOOM) {
        val x1 = lonToTileX(minLon, z)
        val x2 = lonToTileX(maxLon, z)
        val y1 = latToTileY(maxLat, z)
        val y2 = latToTileY(minLat, z)
        val widthPx = (x2 - x1) * OSM_TILE_SIZE * paddingFactor
        val heightPx = (y2 - y1) * OSM_TILE_SIZE * paddingFactor
        if (widthPx <= viewportW && heightPx <= viewportH) return z
    }
    return MIN_MAP_ZOOM
}
