package com.competra.web.utils

@JsFun("(ms) => new Date(ms).toLocaleDateString('ru-RU')")
private external fun jsFormatDate(ms: Double): String

@JsFun("(ms) => { const d = new Date(ms); return d.getFullYear() + '-' + String(d.getMonth()+1).padStart(2,'0') + '-' + String(d.getDate()).padStart(2,'0'); }")
private external fun jsToInputDate(ms: Double): String

@JsFun("() => crypto.randomUUID()")
external fun generateUUID(): String

// --- Часовые пояса и время через браузерный Intl (java.time на wasmJs недоступен) ---

@JsFun(
    "() => { try { return Intl.supportedValuesOf('timeZone').join(','); } " +
        "catch(e) { return 'UTC,Europe/Kaliningrad,Europe/Moscow,Europe/Samara,Asia/Yekaterinburg,Asia/Omsk,Asia/Krasnoyarsk,Asia/Irkutsk,Asia/Yakutsk,Asia/Vladivostok,Asia/Magadan,Asia/Kamchatka'; } }"
)
private external fun jsAvailableTimeZones(): String

@JsFun(
    "(year, month, day, hour, minute, zone) => { " +
        "const asUTC = Date.UTC(year, month - 1, day, hour, minute, 0); " +
        "const dtf = new Intl.DateTimeFormat('en-US', { timeZone: zone, year:'numeric', month:'numeric', day:'numeric', hour:'numeric', minute:'numeric', second:'numeric', hour12:false }); " +
        "const parts = dtf.formatToParts(new Date(asUTC)); const m = {}; " +
        "for (const p of parts) m[p.type] = p.value; let h = parseInt(m.hour); if (h === 24) h = 0; " +
        "const shown = Date.UTC(parseInt(m.year), parseInt(m.month) - 1, parseInt(m.day), h, parseInt(m.minute), parseInt(m.second)); " +
        "return asUTC - (shown - asUTC); }"
)
private external fun jsZonedDateTimeToUtcMillis(
    year: Int, month: Int, day: Int, hour: Int, minute: Int, zone: String,
): Double

@JsFun("(ms, zone) => new Intl.DateTimeFormat('en-CA', { timeZone: zone, year:'numeric', month:'2-digit', day:'2-digit' }).format(new Date(ms))")
private external fun jsUtcMillisToZonedDate(ms: Double, zone: String): String

@JsFun("(ms, zone) => new Intl.DateTimeFormat('en-GB', { timeZone: zone, hour:'2-digit', minute:'2-digit', hour12:false }).format(new Date(ms))")
private external fun jsUtcMillisToZonedTime(ms: Double, zone: String): String

@JsFun(
    "(ms) => { const d = new Date(ms); return d.getUTCFullYear() + '-' + (d.getUTCMonth() + 1) + '-' + d.getUTCDate(); }"
)
private external fun jsUtcDateParts(ms: Double): String

@JsFun(
    "(zone) => { try { const dtf = new Intl.DateTimeFormat('en-US', { timeZone: zone, timeZoneName:'longOffset' }); " +
        "const parts = dtf.formatToParts(new Date()); const tzn = parts.find(p => p.type === 'timeZoneName'); " +
        "let off = tzn ? tzn.value : 'GMT'; off = off.replace('GMT', 'UTC'); if (off === 'UTC') off = 'UTC+00:00'; " +
        "return zone + ' (' + off + ')'; } catch(e) { return zone; } }"
)
private external fun jsZoneOffsetLabel(zone: String): String

const val DEFAULT_TIME_ZONE = "Europe/Moscow"

/** Список IANA-часовых поясов (из браузера). */
fun availableTimeZones(): List<String> =
    jsAvailableTimeZones().split(",").filter { it.isNotBlank() }

/** Подпись зоны вида `Europe/Moscow (UTC+03:00)`. */
fun zoneOffsetLabel(zoneId: String): String = jsZoneOffsetLabel(zoneId)

/**
 * Переводит выбранную в календаре дату (UTC-полночь, как отдаёт Material3 DatePicker) и время
 * «ЧЧ:ММ» в UTC-таймстамп с учётом часового пояса [zoneId].
 */
fun zonedDateTimeToUtcMillis(datePickerUtcMillis: Long, timeStr: String, zoneId: String): Long {
    val parts = jsUtcDateParts(datePickerUtcMillis.toDouble()).split("-")
    val year = parts.getOrNull(0)?.toIntOrNull() ?: return datePickerUtcMillis
    val month = parts.getOrNull(1)?.toIntOrNull() ?: 1
    val day = parts.getOrNull(2)?.toIntOrNull() ?: 1
    val (hour, minute) = parseTimeStr(timeStr)
    return jsZonedDateTimeToUtcMillis(year, month, day, hour, minute, zoneId).toLong()
}

/** Дата «ГГГГ-ММ-ДД» из UTC-таймстампа в часовом поясе [zoneId]. */
fun utcMillisToZonedDate(ms: Long, zoneId: String): String = jsUtcMillisToZonedDate(ms.toDouble(), zoneId)

/** Время «ЧЧ:ММ» из UTC-таймстампа в часовом поясе [zoneId]. */
fun utcMillisToZonedTime(ms: Long, zoneId: String): String =
    jsUtcMillisToZonedTime(ms.toDouble(), zoneId).let { if (it.startsWith("24:")) "00:" + it.substring(3) else it }

private fun parseTimeStr(timeStr: String): Pair<Int, Int> {
    val p = timeStr.split(":")
    val h = p.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 0
    val m = p.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return h to m
}

fun Long.toLocaleDateString(): String = jsFormatDate(this.toDouble())

fun Long.toDateInputString(): String = jsToInputDate(this.toDouble())

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
