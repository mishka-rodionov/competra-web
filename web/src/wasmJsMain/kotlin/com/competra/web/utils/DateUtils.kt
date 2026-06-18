package com.competra.web.utils

@JsFun("(ms) => new Date(ms).toLocaleDateString('ru-RU')")
private external fun jsFormatDate(ms: Double): String

@JsFun("() => crypto.randomUUID()")
external fun generateUUID(): String

fun Long.toLocaleDateString(): String = jsFormatDate(this.toDouble())

fun formatTime(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}

private fun isLeapYear(year: Int): Boolean = year % 4 == 0 && (year % 100 != 0 || year % 400 == 0)

fun parseDateStringToMillis(dateStr: String): Long? {
    return try {
        val parts = dateStr.trim().split("-")
        if (parts.size != 3) return null
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val day = parts[2].toInt()
        if (month < 1 || month > 12 || day < 1 || day > 31) return null

        var totalDays = 0L
        for (y in 1970 until year) {
            totalDays += if (isLeapYear(y)) 366 else 365
        }
        val monthDays = intArrayOf(31, if (isLeapYear(year)) 29 else 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        for (m in 0 until month - 1) {
            totalDays += monthDays[m]
        }
        totalDays += (day - 1)
        totalDays * 24 * 60 * 60 * 1000L
    } catch (e: Exception) {
        null
    }
}
