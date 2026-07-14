package com.competra.domain.diary

/** Точка GPS-трека тренировки. */
data class TrackPoint(
    val lat: Double,
    val lon: Double,
    val timestampMs: Long,
)

/**
 * Кодирует/декодирует GPS-трек тренировки одной компактной строкой вместо построчного
 * хранения (на порядок компактнее для часовой тренировки — тысячи точек).
 *
 * Формат: точки через `;`, поля точки через `:` — `latE5:lonE5:tOffsetSec`.
 * `lat`/`lon` округляются до 5 знаков (~1.1м точности) и хранятся как Int (`* 100_000`),
 * время — как смещение в секундах от [TrackPoint.timestampMs] тренировки.
 *
 * Этот кодек продублирован в Android-приложении и на бэкенде (eSport) — при изменении
 * формата нужно синхронно поправить все три копии.
 */
object TrackCodec {

    private const val POINT_SEPARATOR = ";"
    private const val FIELD_SEPARATOR = ":"
    private const val COORD_PRECISION = 100_000.0

    fun encode(startedAtMs: Long, points: List<TrackPoint>): String =
        points.joinToString(POINT_SEPARATOR) { point ->
            val latE5 = kotlin.math.round(point.lat * COORD_PRECISION).toLong()
            val lonE5 = kotlin.math.round(point.lon * COORD_PRECISION).toLong()
            val tOffsetSec = (point.timestampMs - startedAtMs) / 1000L
            "$latE5$FIELD_SEPARATOR$lonE5$FIELD_SEPARATOR$tOffsetSec"
        }

    fun decode(startedAtMs: Long, encoded: String?): List<TrackPoint> {
        if (encoded.isNullOrBlank()) return emptyList()
        return encoded.split(POINT_SEPARATOR).mapNotNull { chunk ->
            val fields = chunk.split(FIELD_SEPARATOR)
            if (fields.size != 3) return@mapNotNull null
            val latE5 = fields[0].toLongOrNull() ?: return@mapNotNull null
            val lonE5 = fields[1].toLongOrNull() ?: return@mapNotNull null
            val tOffsetSec = fields[2].toLongOrNull() ?: return@mapNotNull null
            TrackPoint(
                lat = latE5 / COORD_PRECISION,
                lon = lonE5 / COORD_PRECISION,
                timestampMs = startedAtMs + tOffsetSec * 1000L,
            )
        }
    }
}
