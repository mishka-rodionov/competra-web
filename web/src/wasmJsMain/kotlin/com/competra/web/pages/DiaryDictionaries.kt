package com.competra.web.pages

/**
 * Словари значений тренировочного дневника — ключи строго совпадают с enum'ами
 * Android/бэкенда, метки — для отображения в выпадающих списках Web.
 */

internal val SPORT_TYPE_OPTIONS = listOf(
    "RUNNING" to "Бег",
    "CYCLING" to "Велоспорт",
    "SKIING" to "Лыжи",
)

internal fun sportTypeLabel(sportType: String): String = SPORT_TYPE_OPTIONS.firstOrNull { it.first == sportType }?.second ?: sportType

internal val WORKOUT_STATUS_OPTIONS = listOf(
    "COMPLETED" to "Выполнена",
    "PLANNED" to "Запланирована",
)

internal fun workoutStatusLabel(status: String): String = WORKOUT_STATUS_OPTIONS.firstOrNull { it.first == status }?.second ?: status

internal val SKI_STYLE_OPTIONS = listOf(
    "CLASSIC" to "Классика",
    "SKATE" to "Коньковый",
)

internal fun skiStyleLabel(style: String): String = SKI_STYLE_OPTIONS.firstOrNull { it.first == style }?.second ?: style

/** "1 ч 5 мин" / "45 мин" — компактный формат длительности для карточек списка. */
internal fun formatWorkoutDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return when {
        hours > 0 && minutes > 0 -> "$hours ч $minutes мин"
        hours > 0 -> "$hours ч"
        else -> "$minutes мин"
    }
}

/** "5.2 км" — дистанция в километрах с одним знаком после запятой. */
internal fun formatDistanceKm(meters: Int): String {
    val km = meters / 1000.0
    val rounded = kotlin.math.round(km * 10) / 10
    return "$rounded км"
}
