package com.competra.web.pages

/**
 * Словари значений соревнования по ориентированию — ключи строго совпадают с enum'ами
 * Android/бэкенда, метки — для отображения в выпадающих списках Web.
 */

internal val DIRECTION_OPTIONS = listOf(
    "FORWARD" to "В заданном направлении",
    "BY_CHOICE" to "По выбору",
    "MARKING" to "Маркированная трасса",
)

internal val PUNCHING_SYSTEM_OPTIONS = listOf(
    "PENCIL" to "Карандаш",
    "PUNCH" to "Компостер",
    "SPORTIDUINO" to "Sportiduino",
    "SFR" to "SFR",
    "SPORTIDENT" to "SportIdent",
)

internal val START_TIME_MODE_OPTIONS = listOf(
    "STRICT" to "Строгое время старта",
    "USER_SET" to "Задаётся перед стартом",
    "BY_START_STATION" to "По отметке на старте",
)

/** Интервал между стартами: 20..180 с шагом 20 (как в Android). */
internal val START_INTERVAL_OPTIONS: List<Int> = (1..9).map { it * 20 }

internal fun formatIntervalSeconds(seconds: Int): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return when {
        minutes == 0 -> "$secs сек"
        secs == 0 -> "$minutes мин"
        else -> "$minutes мин $secs сек"
    }
}
