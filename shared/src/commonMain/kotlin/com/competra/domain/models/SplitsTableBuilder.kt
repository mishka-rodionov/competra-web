package com.competra.domain.models

import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Колонка таблицы сплитов — один контрольный пункт по позиции в дистанции (не по номеру КП). */
data class SplitsTableColumn(
    val positionIndex: Int,
    val controlPoint: Int,
)

/** Ячейка таблицы сплитов для одного участника на одном КП. */
data class SplitsTableCell(
    val deltaSeconds: Long?,
    val cumulativeSeconds: Long?,
    val deltaRank: Int?,
    val cumulativeRank: Int?,
    val isBestLeg: Boolean,
    val paceMinPerKm: Double? = null,
)

/** Строка таблицы сплитов — один участник группы. */
data class SplitsTableRow(
    val participant: OrienteeringParticipant,
    val result: OrienteeringResult?,
    val cells: List<SplitsTableCell>,
)

data class SplitsTable(
    val columns: List<SplitsTableColumn>,
    val rows: List<SplitsTableRow>,
)

/** Точка графика гонки для одного КП: отставание участника от лидера в секундах. */
data class RaceGraphPoint(
    val positionIndex: Int,
    val controlPoint: Int,
    val deltaSeconds: Long?,
)

/** Кривая отставания от лидера для одного участника по всем КП дистанции. */
data class RaceGraphSeries(
    val participant: OrienteeringParticipant,
    val result: OrienteeringResult?,
    val points: List<RaceGraphPoint>,
)

data class RaceGraphData(
    val columns: List<SplitsTableColumn>,
    val series: List<RaceGraphSeries>,
)

private const val EARTH_RADIUS_METERS = 6_371_000.0

/** Расстояние между двумя КП по WGS84 (haversine), в метрах. Null, если у одного из КП нет координат. */
fun controlPointDistanceMeters(from: ControlPoint?, to: ControlPoint?): Double? {
    val lat1 = from?.latitude ?: return null
    val lon1 = from.longitude ?: return null
    val lat2 = to?.latitude ?: return null
    val lon2 = to?.longitude ?: return null
    val dLat = (lat2 - lat1) * PI / 180.0
    val dLon = (lon2 - lon1) * PI / 180.0
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(lat1 * PI / 180.0) * cos(lat2 * PI / 180.0) * sin(dLon / 2) * sin(dLon / 2)
    return EARTH_RADIUS_METERS * 2 * atan2(sqrt(a), sqrt(1 - a))
}

/** Темп на перегоне (мин/км), либо null если длина перегона неизвестна/нулевая. */
fun paceMinPerKm(deltaSeconds: Long, legLengthMeters: Double?): Double? {
    if (legLengthMeters == null || legLengthMeters <= 0) return null
    return (deltaSeconds / 60.0) / (legLengthMeters / 1000.0)
}

/** Длина перегона (м) для каждой позиции cpOrder: null для первой позиции и там, где нет координат. */
private fun legLengthsMeters(distance: Distance?, cpOrder: List<Int>): List<Double?> {
    val expected = distance?.controlPoints ?: return List(cpOrder.size) { null }
    return cpOrder.indices.map { i ->
        if (i == 0) null else controlPointDistanceMeters(expected.getOrNull(i - 1), expected.getOrNull(i))
    }
}

/** Анкер отсчёта: startTime результата, а если он потерян — плановое startTime участника. */
private fun anchorStartTime(participant: OrienteeringParticipant, result: OrienteeringResult?): Long? =
    result?.startTime ?: participant.startTime

private fun statusSortOrder(status: String?): Int = when (status) {
    "FINISHED" -> 0
    "DSQ" -> 1
    "DNF" -> 2
    "DNS" -> 3
    "STARTED" -> 4
    "REGISTERED" -> 5
    else -> 9
}

/**
 * Порядок строк результатов/таблицы сплитов: по статусу, затем по времени (или по очкам
 * по убыванию для направления "по выбору" — рогейн/score-О).
 */
fun sortedForResults(
    participants: List<OrienteeringParticipant>,
    results: List<OrienteeringResult>,
    direction: String = "FORWARD",
): List<OrienteeringParticipant> {
    val resultByParticipantId = results.associateBy { it.participantId }
    return if (direction == "BY_CHOICE") {
        participants.sortedWith(
            compareBy<OrienteeringParticipant> { statusSortOrder(resultByParticipantId[it.id]?.status) }
                .thenByDescending { resultByParticipantId[it.id]?.totalScore ?: 0 }
                .thenBy { resultByParticipantId[it.id]?.finishTime ?: Long.MAX_VALUE }
        )
    } else {
        participants.sortedWith(
            compareBy(
                { p: OrienteeringParticipant -> statusSortOrder(resultByParticipantId[p.id]?.status) },
                { p: OrienteeringParticipant -> resultByParticipantId[p.id]?.totalTime ?: Long.MAX_VALUE },
            )
        )
    }
}

/**
 * Строит сравнительную таблицу сплитов по группе участников.
 * Колонки берутся из самого длинного массива splits среди участников (позиционно, а не по
 * номеру КП — корректно работает и с петлями/"бабочками" в дистанции).
 */
fun buildSplitsTable(
    participants: List<OrienteeringParticipant>,
    results: List<OrienteeringResult>,
    distance: Distance? = null,
): SplitsTable {
    val resultByParticipantId = results.associateBy { it.participantId }
    val pairs = participants.map { it to resultByParticipantId[it.id] }

    val cpOrder = pairs
        .mapNotNull { (_, result) -> result?.splits }
        .maxByOrNull { it.size }
        ?.map { it.controlPoint }
        ?: emptyList()

    val columns = cpOrder.mapIndexed { i, cp -> SplitsTableColumn(positionIndex = i + 1, controlPoint = cp) }
    val legLengths = legLengthsMeters(distance, cpOrder)

    val cumulRanks: List<Map<String, Int>> = cpOrder.indices.map { i ->
        pairs.mapNotNull { (participant, result) ->
            val splits = result?.splits ?: return@mapNotNull null
            val startTs = anchorStartTime(participant, result) ?: return@mapNotNull null
            if (i < splits.size) participant.id to (splits[i].timestamp - startTs) else null
        }.sortedBy { it.second }.mapIndexed { rank, (id, _) -> id to (rank + 1) }.toMap()
    }

    val deltaRanks: List<Map<String, Int>> = cpOrder.indices.map { i ->
        pairs.mapNotNull { (participant, result) ->
            val splits = result?.splits ?: return@mapNotNull null
            val startTs = anchorStartTime(participant, result) ?: return@mapNotNull null
            if (i < splits.size) {
                val prevTs = if (i == 0) startTs else splits[i - 1].timestamp
                participant.id to (splits[i].timestamp - prevTs)
            } else null
        }.sortedBy { it.second }.mapIndexed { rank, (id, _) -> id to (rank + 1) }.toMap()
    }

    val rows = pairs.map { (participant, result) ->
        val splits = result?.splits ?: emptyList()
        val startTs = anchorStartTime(participant, result)

        val cells = cpOrder.indices.map { i ->
            if (startTs == null || i >= splits.size) {
                SplitsTableCell(
                    deltaSeconds = null,
                    cumulativeSeconds = null,
                    deltaRank = null,
                    cumulativeRank = null,
                    isBestLeg = false,
                )
            } else {
                val splitTs = splits[i].timestamp
                val prevTs = if (i == 0) startTs else splits[i - 1].timestamp
                val cumulSec = (splitTs - startTs) / 1000L
                val deltaSec = (splitTs - prevTs) / 1000L
                val cumulRank = cumulRanks[i][participant.id]
                val deltaRank = deltaRanks[i][participant.id]
                val pace = paceMinPerKm(deltaSec, legLengths.getOrNull(i))

                SplitsTableCell(
                    deltaSeconds = deltaSec,
                    cumulativeSeconds = cumulSec,
                    deltaRank = deltaRank,
                    cumulativeRank = cumulRank,
                    isBestLeg = deltaRank == 1,
                    paceMinPerKm = pace,
                )
            }
        }

        SplitsTableRow(participant = participant, result = result, cells = cells)
    }

    return SplitsTable(columns = columns, rows = rows)
}

/**
 * Строит данные графика гонки (отставание от "виртуального" лидера — минимальное кумулятивное
 * время на каждом КП среди финишировавших) из уже построенной таблицы сплитов.
 * Участники не в статусе FINISHED полностью исключаются: их кривая отставания не имеет смысла.
 */
fun buildRaceGraphData(table: SplitsTable): RaceGraphData {
    val finishedRows = table.rows.filter { it.result?.status == "FINISHED" }

    val leaderCumulativeByColumn = table.columns.indices.map { i ->
        finishedRows.mapNotNull { it.cells.getOrNull(i)?.cumulativeSeconds }.minOrNull()
    }

    val series = finishedRows.map { row ->
        val points = table.columns.mapIndexed { i, column ->
            val cumulative = row.cells.getOrNull(i)?.cumulativeSeconds
            val leader = leaderCumulativeByColumn[i]
            RaceGraphPoint(
                positionIndex = column.positionIndex,
                controlPoint = column.controlPoint,
                deltaSeconds = if (cumulative != null && leader != null) cumulative - leader else null,
            )
        }
        RaceGraphSeries(participant = row.participant, result = row.result, points = points)
    }

    return RaceGraphData(columns = table.columns, series = series)
}
