package com.competra.web.utils

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Разбирает Excel-протокол результатов прошедшего соревнования — формат придуман под задачу
 * оцифровки бумажных протоколов (см. обсуждение фичи), не привязан к какому-то внешнему стандарту.
 *
 * .xlsx — бинарный ZIP+XML, браузерный DOMParser (как для HTML-протокола, [parseResultsHtml])
 * не подходит — используем SheetJS (npm-пакет "xlsx", см. web/build.gradle.kts). Модуль
 * подключается через @JsModule и один раз "прибивается" к globalThis, дальше вся работа с ним —
 * в обычном @JsFun-глее, как и остальной JS-интероп в этом проекте.
 */
@JsModule("xlsx")
private external object XlsxModule : JsAny

@JsFun("(lib) => { if (!globalThis.__competraXLSX) globalThis.__competraXLSX = lib; }")
private external fun jsAttachXlsxLib(lib: JsAny)

private var xlsxAttached = false

private fun ensureXlsxAttached() {
    if (!xlsxAttached) {
        jsAttachXlsxLib(XlsxModule)
        xlsxAttached = true
    }
}

@JsFun(
    "(base64) => { " +
        "const wb = globalThis.__competraXLSX.read(base64, {type: 'base64'}); " +
        "const sheetName = wb.SheetNames[0]; " +
        "const sheet = wb.Sheets[sheetName]; " +
        "if (!sheet) return JSON.stringify({rows: []}); " +
        "const aoa = globalThis.__competraXLSX.utils.sheet_to_json(sheet, {header: 1, raw: false, defval: ''}); " +
        "if (aoa.length === 0) return JSON.stringify({rows: []}); " +
        "const header = aoa[0].map((h) => String(h || '').trim().toLowerCase()); " +
        "const findCol = (aliases) => header.findIndex((h) => aliases.indexOf(h) !== -1); " +
        "const colFio = findCol(['фамилия имя', 'фио', 'участник']); " +
        "const colGroup = findCol(['группа', 'возр. гр', 'возрастная группа', 'категория']); " +
        "const colResult = findCol(['результат', 'время', 'итог']); " +
        "const colPlace = findCol(['место', 'место в группе', 'ранг']); " +
        "const colStart = findCol(['время старта', 'старт']); " +
        "const colFinish = findCol(['время финиша', 'финиш']); " +
        "const rows = []; " +
        "for (let i = 1; i < aoa.length; i++) { " +
        "const r = aoa[i]; " +
        "if (!r || r.length === 0) continue; " +
        "const cell = (idx) => (idx >= 0 && idx < r.length) ? String(r[idx] == null ? '' : r[idx]).trim() : ''; " +
        "const fio = cell(colFio); " +
        "if (fio === '') continue; " +
        "rows.push({ fio: fio, group: cell(colGroup), result: cell(colResult), place: cell(colPlace), " +
        "startOffset: cell(colStart), finishOffset: cell(colFinish) }); " +
        "} " +
        "return JSON.stringify({rows: rows}); " +
        "}"
)
private external fun jsParseResultsExcel(base64: String): String

@Serializable
private data class RawExcelDoc(val rows: List<RawExcelRow> = emptyList())

@Serializable
private data class RawExcelRow(
    val fio: String = "",
    val group: String = "",
    val result: String = "",
    val place: String = "",
    val startOffset: String = "",
    val finishOffset: String = "",
)

/** Одна распознанная и провалидированная строка результата прошедшего соревнования. */
data class ParsedPastResultRow(
    val lastName: String,
    val firstName: String,
    val groupTitle: String,
    /** Секунды. null только при статусе, отличном от FINISHED. */
    val totalTimeSeconds: Long?,
    /** FINISHED / DNF / DNS / DSQ. */
    val status: String,
    val rank: Int?,
    /** Отсечка по общему секундомеру от старта соревнования, секунды. */
    val startOffsetSeconds: Long?,
    val finishOffsetSeconds: Long?,
)

/** Результат разбора файла — распознанные строки плюс сколько всего строк было в файле (для счётчика пропущенных). */
data class ParsedPastResultsFile(
    val rows: List<ParsedPastResultRow>,
    val totalRowsInFile: Int,
)

// "Результат": длительность M.SS / Ч:ММ.СС — точка перед секундами, как в протоколе.
private val durationDotRegex = Regex("""^(?:(\d+):)?(\d{1,2})\.(\d{2})$""")

// "Время старта"/"Время финиша": отсечка по секундомеру M:SS / Ч:ММ:СС — двоеточие.
private val offsetColonRegex = Regex("""^(?:(\d+):)?(\d{1,2}):(\d{2})$""")

private fun parseDurationDotSeconds(text: String): Long? {
    val m = durationDotRegex.matchEntire(text.trim()) ?: return null
    val (h, mi, s) = m.destructured
    return (h.toLongOrNull() ?: 0L) * 3600 + mi.toLong() * 60 + s.toLong()
}

private fun parseOffsetColonSeconds(text: String): Long? {
    val m = offsetColonRegex.matchEntire(text.trim()) ?: return null
    val (h, mi, s) = m.destructured
    return (h.toLongOrNull() ?: 0L) * 3600 + mi.toLong() * 60 + s.toLong()
}

private fun parseStatusWord(text: String): String? = when (text.trim().lowercase()) {
    "снят", "снята" -> "DSQ"
    "н/с" -> "DNS"
    "не финишировал", "не финишировала" -> "DNF"
    else -> null
}

private val parserJson = Json { ignoreUnknownKeys = true }

/**
 * Парсит Excel-файл результатов. Группа в пустой ячейке наследуется от строки выше (как
 * повторяющиеся кавычки в бумажном протоколе). «Результат» обязателен, если не заполнена пара
 * «Время старта»+«Время финиша» — тогда чистое время считается как разница отсечек.
 * Строки, из которых не удалось получить ни статус, ни время, — пропускаются.
 */
fun parseResultsExcel(base64: String): ParsedPastResultsFile {
    ensureXlsxAttached()
    val raw = jsParseResultsExcel(base64)
    val doc = parserJson.decodeFromString<RawExcelDoc>(raw)

    var lastGroup = ""
    val result = mutableListOf<ParsedPastResultRow>()

    doc.rows.forEach { row ->
        val group = row.group.ifBlank { lastGroup }
        lastGroup = group
        if (group.isBlank()) return@forEach

        val nameParts = row.fio.trim().split(Regex("\\s+"), limit = 2)
        val lastName = nameParts.getOrElse(0) { "" }
        val firstName = nameParts.getOrElse(1) { "" }
        if (lastName.isBlank()) return@forEach

        val startOffset = parseOffsetColonSeconds(row.startOffset)
        val finishOffset = parseOffsetColonSeconds(row.finishOffset)
        val statusWord = parseStatusWord(row.result)
        val explicitDuration = parseDurationDotSeconds(row.result)

        val (totalTime, status) = when {
            statusWord != null -> null to statusWord
            explicitDuration != null -> explicitDuration to "FINISHED"
            startOffset != null && finishOffset != null -> (finishOffset - startOffset) to "FINISHED"
            else -> return@forEach
        }

        result += ParsedPastResultRow(
            lastName = lastName,
            firstName = firstName,
            groupTitle = group,
            totalTimeSeconds = totalTime,
            status = status,
            rank = row.place.toIntOrNull(),
            startOffsetSeconds = startOffset,
            finishOffsetSeconds = finishOffset,
        )
    }

    return ParsedPastResultsFile(rows = fillMissingRanks(result), totalRowsInFile = doc.rows.size)
}

/**
 * Если в группе хотя бы у одного финишировавшего не указано место — пересчитывает места для
 * всей группы по времени (сортировка по возрастанию), не полагаясь на частично заполненные
 * вручную значения. DSQ/DNS/DNF места не получают. Группы, где место указано у всех, не трогаем.
 */
private fun fillMissingRanks(rows: List<ParsedPastResultRow>): List<ParsedPastResultRow> {
    val result = rows.toMutableList()
    rows.withIndex().groupBy { it.value.groupTitle }.values.forEach { indexedGroup ->
        val needsAutoRank = indexedGroup.any { it.value.rank == null && it.value.status == "FINISHED" }
        if (!needsAutoRank) return@forEach
        indexedGroup
            .filter { it.value.status == "FINISHED" }
            .sortedBy { it.value.totalTimeSeconds ?: Long.MAX_VALUE }
            .forEachIndexed { place, indexedRow -> result[indexedRow.index] = indexedRow.value.copy(rank = place + 1) }
    }
    return result
}
