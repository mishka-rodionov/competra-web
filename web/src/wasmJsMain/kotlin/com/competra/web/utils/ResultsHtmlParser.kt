package com.competra.web.utils

import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.OrienteeringResult
import com.competra.domain.models.SaveResultRequest
import com.competra.domain.models.SplitTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json

/**
 * Разбирает HTML-протокол результатов (формат buildHtmlContent() из Android-приложения,
 * table.rezult + якоря <a name="..."> для групп) через нативный браузерный DOMParser —
 * Jsoup на wasmJs недоступен (JVM-only).
 */
@JsFun(
    "(html) => { " +
        "const doc = new DOMParser().parseFromString(html, 'text/html'); " +
        "const tables = Array.from(doc.querySelectorAll('table.rezult')); " +
        "const groups = tables.map((table) => { " +
        "let title = ''; let el = table.previousElementSibling; " +
        "while (el) { if (el.tagName === 'A' && el.hasAttribute('name')) { title = el.getAttribute('name'); break; } el = el.previousElementSibling; } " +
        "const trs = Array.from(table.querySelectorAll('tr')); " +
        "if (trs.length === 0) return { title: title, rows: [] }; " +
        "const headerThs = Array.from(trs[0].querySelectorAll('th')); " +
        "const cpNumbers = headerThs.slice(7).map((th) => { const m = th.textContent.match(/\\(([0-9]+)\\)/); return m ? parseInt(m[1], 10) : 0; }); " +
        "const rows = trs.slice(1).map((tr) => { " +
        "const tds = Array.from(tr.querySelectorAll('td')); " +
        "if (tds.length < 7) return null; " +
        "const startNumber = tds[1].textContent.trim(); " +
        "const fullName = tds[2].textContent.trim(); " +
        "const resultText = tds[4].textContent.trim(); " +
        "const rankText = tds[5].textContent.trim(); " +
        "const splitCells = tds.slice(7); " +
        "const splits = splitCells.map((td, i) => { const inner = td.innerHTML; const parts = inner.split(/<br\\s*\\/?>/i); const first = (parts[0] || '').replace(/<[^>]+>/g, '').trim(); return { cp: (cpNumbers[i] || 0), text: first }; }); " +
        "return { startNumber: startNumber, fullName: fullName, resultText: resultText, rankText: rankText, splits: splits }; " +
        "}).filter((r) => r !== null); " +
        "return { title: title, rows: rows }; " +
        "}); " +
        "return JSON.stringify({ groups: groups }); " +
        "}"
)
private external fun jsParseResultsHtml(html: String): String

@Serializable
private data class ParsedHtmlDoc(val groups: List<ParsedHtmlGroup> = emptyList())

@Serializable
private data class ParsedHtmlGroup(val title: String = "", val rows: List<ParsedHtmlRow> = emptyList())

@Serializable
private data class ParsedHtmlRow(
    val startNumber: String = "",
    val fullName: String = "",
    val resultText: String = "",
    val rankText: String = "",
    val splits: List<ParsedHtmlSplit> = emptyList(),
)

@Serializable
private data class ParsedHtmlSplit(val cp: Int = 0, val text: String = "")

/** Одна строка результата, распознанная в HTML-протоколе (ещё не сопоставленная с участником). */
data class ParsedResultRow(
    val groupTitle: String,
    val startNumber: String,
    val fullName: String,
    val totalTimeSeconds: Long?,
    val status: String?,
    val rank: Int?,
    /** Контрольный пункт -> кумулятивное время от старта в секундах (null = участник его не прошёл). */
    val splits: List<Pair<Int, Long?>>,
)

private val raceTimeRegex = Regex("""^(\d+):(\d{2}):(\d{2})$""")
private val splitCellRegex = Regex("""^(\d+):(\d{2}):(\d{2})\(\d+\)$""")

private fun parseRaceTimeSeconds(text: String): Long? {
    val m = raceTimeRegex.matchEntire(text.trim()) ?: return null
    val (h, mi, s) = m.destructured
    return h.toLong() * 3600 + mi.toLong() * 60 + s.toLong()
}

private fun parseSplitCumulSeconds(text: String): Long? {
    val m = splitCellRegex.matchEntire(text.trim()) ?: return null
    val (h, mi, s) = m.destructured
    return h.toLong() * 3600 + mi.toLong() * 60 + s.toLong()
}

/** Обратное преобразование statusText из buildHtmlContent() (строки 461-467 Android-файла). */
private fun parseResultStatus(text: String): Pair<String?, Long?> = when {
    text.isBlank() -> null to null
    text == "снят" -> "DSQ" to null
    text == "н/с" -> "DNS" to null
    text == "не финишировал" -> "DNF" to null
    else -> parseRaceTimeSeconds(text)?.let { "FINISHED" to it } ?: (null to null)
}

private val parserJson = Json { ignoreUnknownKeys = true }

/** Парсит HTML-протокол результатов в плоский список строк, без сопоставления с участниками. */
fun parseResultsHtml(html: String): List<ParsedResultRow> {
    val raw = jsParseResultsHtml(html)
    val doc = parserJson.decodeFromString<ParsedHtmlDoc>(raw)
    return doc.groups.flatMap { group ->
        group.rows.mapNotNull { row ->
            if (row.startNumber.isBlank()) return@mapNotNull null
            val (status, totalTimeSeconds) = parseResultStatus(row.resultText)
            ParsedResultRow(
                groupTitle = group.title,
                startNumber = row.startNumber,
                fullName = row.fullName,
                totalTimeSeconds = totalTimeSeconds,
                status = status,
                rank = row.rankText.toIntOrNull(),
                splits = row.splits.map { it.cp to parseSplitCumulSeconds(it.text) },
            )
        }
    }
}

/** Одна изменившаяся и заматченная строка импорта, готовая к показу в превью и к отправке. */
data class ImportResultRow(
    val participant: OrienteeringParticipant,
    val existing: OrienteeringResult?,
    val request: SaveResultRequest,
    val changeSummary: String,
)

data class ImportResultsDiff(
    val changed: List<ImportResultRow>,
    val unmatched: List<ParsedResultRow>,
)

/**
 * Сопоставляет распарсенные строки с текущими участниками (по startNumber — номер уникален
 * в рамках всего соревнования) и текущими результатами, оставляя только реально изменившиеся строки.
 */
fun buildResultsDiff(
    parsedRows: List<ParsedResultRow>,
    participants: List<OrienteeringParticipant>,
    currentResults: List<OrienteeringResult>,
    competitionId: String,
): ImportResultsDiff {
    val participantsByNumber = participants
        .filter { !it.startNumber.isNullOrBlank() }
        .associateBy { it.startNumber!! }
    val resultsByParticipantId = currentResults.associateBy { it.participantId }

    val changed = mutableListOf<ImportResultRow>()
    val unmatched = mutableListOf<ParsedResultRow>()

    parsedRows.forEach { row ->
        val participant = participantsByNumber[row.startNumber]
        if (participant == null) {
            unmatched += row
            return@forEach
        }

        val existing = resultsByParticipantId[participant.id]
        val newSplits = existing?.startTime?.let { startTime ->
            row.splits
                .mapNotNull { (cp, cumulSec) -> cumulSec?.let { SplitTime(cp, startTime + it * 1000) } }
                .takeIf { it.isNotEmpty() }
        }

        val newStatus = row.status ?: existing?.status ?: "FINISHED"
        val newTotalTime = row.totalTimeSeconds ?: existing?.totalTime
        val newRank = row.rank ?: existing?.rank

        val isChanged = existing == null ||
            existing.totalTime != newTotalTime ||
            existing.rank != newRank ||
            existing.status != newStatus ||
            !splitsEqual(existing.splits, newSplits)

        if (!isChanged) return@forEach

        val request = SaveResultRequest(
            id = existing?.id ?: generateUUID(),
            competitionId = competitionId,
            groupId = participant.groupId,
            participantId = participant.id,
            startTime = existing?.startTime,
            finishTime = existing?.finishTime,
            totalTime = newTotalTime,
            rank = newRank,
            status = newStatus,
            penaltyTime = existing?.penaltyTime ?: 0,
            splits = newSplits,
            isEditable = existing?.isEditable ?: true,
            isEdited = true,
        )

        val groupMismatch = !participant.groupName.isNullOrBlank() &&
            row.groupTitle.isNotBlank() &&
            participant.groupName != row.groupTitle

        changed += ImportResultRow(
            participant = participant,
            existing = existing,
            request = request,
            changeSummary = buildChangeSummary(existing, request, groupMismatch),
        )
    }

    return ImportResultsDiff(changed, unmatched)
}

private fun splitsEqual(a: List<SplitTime>?, b: List<SplitTime>?): Boolean =
    a.orEmpty().sortedBy { it.controlPoint } == b.orEmpty().sortedBy { it.controlPoint }

private fun buildChangeSummary(existing: OrienteeringResult?, request: SaveResultRequest, groupMismatch: Boolean): String {
    val parts = mutableListOf<String>()
    if (groupMismatch) {
        parts += "⚠ группа в файле не совпадает с текущей группой участника"
    }
    if (existing?.rank != request.rank) {
        parts += "Место: ${existing?.rank ?: "—"} → ${request.rank ?: "—"}"
    }
    if (existing?.totalTime != request.totalTime) {
        val oldT = existing?.totalTime?.let { formatTime(it) } ?: "—"
        val newT = request.totalTime?.let { formatTime(it) } ?: "—"
        parts += "Время: $oldT → $newT"
    }
    if (existing?.status != request.status) {
        parts += "Статус: ${existing?.status ?: "—"} → ${request.status}"
    }
    val oldSplitsCount = existing?.splits?.size ?: 0
    val newSplitsCount = request.splits?.size ?: 0
    if (oldSplitsCount != newSplitsCount) {
        parts += "КП: $oldSplitsCount → $newSplitsCount"
    }
    return parts.joinToString("; ").ifEmpty { "Без изменений" }
}
