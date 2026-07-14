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

fun osmTileUrl(zoom: Int, x: Int, y: Int): String = "https://tile.openstreetmap.org/$zoom/$x/$y.png"

private val tileHttpClient = HttpClient()

/** Общий на весь модуль кэш загруженных тайлов OSM, переиспользуется всеми canvas-картами. */
val tileBitmapCache = mutableMapOf<String, ImageBitmap>()

suspend fun loadTileBitmap(zoom: Int, x: Int, y: Int): ImageBitmap? {
    val key = "$zoom/$x/$y"
    tileBitmapCache[key]?.let { return it }
    return try {
        val bytes = tileHttpClient.get(osmTileUrl(zoom, x, y)).bodyAsBytes()
        val image = Image.makeFromEncoded(bytes)
        val skiaBitmap = Bitmap().apply {
            allocPixels(image.imageInfo)
            image.readPixels(this)
            setImmutable()
        }
        val bitmap = skiaBitmap.asComposeImageBitmap()
        tileBitmapCache[key] = bitmap
        bitmap
    } catch (e: Exception) {
        null
    }
}
