package com.competra.web.utils

import com.competra.domain.diary.TrackPoint

@JsFun("(iso) => Date.parse(iso)")
private external fun jsParseIsoDate(iso: String): Double

private val TRKPT_BLOCK = Regex("""<trkpt\b[^>]*>.*?</trkpt>""", RegexOption.DOT_MATCHES_ALL)
private val TRKPT_OPEN = Regex("""<trkpt\b([^>]*)>""")
private val LAT_ATTR = Regex("""lat="(-?[0-9.]+)"""")
private val LON_ATTR = Regex("""lon="(-?[0-9.]+)"""")
private val TIME_TAG = Regex("""<time>([^<]+)</time>""")

/**
 * Извлекает точки трека из содержимого GPX-файла. Парсинг регулярками, а не через
 * полноценный DOM-парсер — формат `<trkpt lat lon><time>` достаточно простой и
 * стабильный у экспортёров треков (Strava, Garmin, OSMAnd и т.п.).
 * Если у точки нет `<time>`, ей присваивается синтетическое смещение по индексу (1с/точку).
 */
fun parseGpxTrackPoints(gpxContent: String): List<TrackPoint> =
    TRKPT_BLOCK.findAll(gpxContent).mapIndexedNotNull { index, match ->
        val block = match.value
        val openTag = TRKPT_OPEN.find(block)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
        val lat = LAT_ATTR.find(openTag)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapIndexedNotNull null
        val lon = LON_ATTR.find(openTag)?.groupValues?.get(1)?.toDoubleOrNull() ?: return@mapIndexedNotNull null
        val timeStr = TIME_TAG.find(block)?.groupValues?.get(1)
        val ms = timeStr?.let { jsParseIsoDate(it).takeIf { ms -> !ms.isNaN() }?.toLong() }
        TrackPoint(lat = lat, lon = lon, timestampMs = ms ?: index * 1000L)
    }.toList()
