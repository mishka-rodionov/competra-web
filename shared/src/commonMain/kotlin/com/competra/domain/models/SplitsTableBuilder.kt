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
    /** Номер КП, реально взятого участником на этой позиции (BY_CHOICE — у каждого свой порядок).
     * Null для FORWARD/MARKING, где КП колонки общий для всех (см. [SplitsTableColumn.controlPoint]). */
    val controlPoint: Int? = null,
)

/** Строка таблицы сплитов — один участник группы. */
data class SplitsTableRow(
    val participant: OrienteeringParticipant,
    val result: OrienteeringResult?,
    val cells: List<SplitsTableCell>,
    /** Сырые очки за фактически взятые КП (BY_CHOICE) — сумма по дистанции, ДО вычета штрафа.
     * Null для FORWARD/MARKING. Считается один раз здесь, чтобы UI не знал про Distance/ControlPoint. */
    val rawScore: Int? = null,
    /** Дистанция, пройденная участником (BY_CHOICE), в метрах — сумма расстояний между
     * последовательно взятыми КП по их координатам. Null для FORWARD/MARKING, когда у дистанции
     * нет координат КП, и когда сумма получилась нулевой (ни одного перегона с известными
     * координатами). Первый взятый КП в сумму не входит — координата точки старта неизвестна. */
    val totalDistanceMeters: Double? = null,
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

/** Точка графика набора очков (BY_CHOICE): момент времени от старта и накопленные очки на этот момент. */
data class ScoreGraphPoint(
    val elapsedSeconds: Long,
    val cumulativeScore: Int,
)

/** Кривая набора очков во времени для одного участника (BY_CHOICE). */
data class ScoreGraphSeries(
    val participant: OrienteeringParticipant,
    val result: OrienteeringResult?,
    val points: List<ScoreGraphPoint>,
)

data class ScoreGraphData(
    val series: List<ScoreGraphSeries>,
    /** Контрольное время группы в секундах от старта — момент, с которого начинает начисляться
     * штраф за опоздание. Null, если у группы нет ограничения по времени. */
    val timeLimitSeconds: Long? = null,
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
 * Сырые очки участника за фактически взятые КП (BY_CHOICE), ДО вычета штрафа — сумма
 * [ControlPoint.score] по номерам КП из [OrienteeringResult.splits]. [OrienteeringResult.totalScore]
 * хранится уже за вычетом штрафа, а при обнулении результата (сильное опоздание) totalScore+scorePenalty
 * не равен фактически заработанным очкам — поэтому считаем от дистанции, как и HTML-экспорт результатов.
 * Фолбэк на totalScore+scorePenalty, если карта очков КП дистанции недоступна.
 */
private fun rawByChoiceScore(result: OrienteeringResult?, scoreByNumber: Map<Int, Int>): Int? {
    if (result == null) return null
    val netScore = result.totalScore ?: return null
    return if (scoreByNumber.isNotEmpty()) {
        result.splits?.sumOf { scoreByNumber[it.controlPoint] ?: 0 } ?: (netScore + result.scorePenalty)
    } else {
        netScore + result.scorePenalty
    }
}

/**
 * Дистанция, пройденная участником (BY_CHOICE), в метрах — сумма расстояний между
 * последовательно взятыми КП по их координатам ([controlPointByNumber]). Первый взятый КП не
 * учитывается — координата точки старта неизвестна. Перегоны с неизвестными координатами (нет
 * хотя бы одной из точек) в сумму не входят — итог остаётся приблизительным, а не null, чтобы
 * частичное отсутствие координат не скрывало всю оценку целиком.
 */
private fun byChoiceDistanceMeters(splits: List<SplitTime>, controlPointByNumber: Map<Int, ControlPoint>): Double? {
    if (controlPointByNumber.isEmpty() || splits.size < 2) return null
    var sum = 0.0
    for (i in 1 until splits.size) {
        val from = controlPointByNumber[splits[i - 1].controlPoint]
        val to = controlPointByNumber[splits[i].controlPoint]
        sum += controlPointDistanceMeters(from, to) ?: 0.0
    }
    return sum.takeIf { it > 0.0 }
}

/**
 * Строит сравнительную таблицу сплитов по группе участников.
 *
 * Для FORWARD/MARKING колонки берутся из самого длинного массива splits среди участников
 * (позиционно, а не по номеру КП — корректно работает и с петлями/"бабочками" в дистанции), с
 * рангами и лучшим перегоном.
 *
 * Для BY_CHOICE у каждого участника свой набор и порядок КП — общий cpOrder не имеет смысла:
 * колонки строятся по позиции (1..максимум сплитов в группе), а какой именно КП стоит за каждой
 * позицией у конкретного участника — заполняется в [SplitsTableCell.controlPoint]. Ранги/лучший
 * перегон/темп не считаются (сравнивать разные реальные перегоны бессмысленно) — тот же подход,
 * что и в HTML-публикации результатов.
 */
fun buildSplitsTable(
    participants: List<OrienteeringParticipant>,
    results: List<OrienteeringResult>,
    distance: Distance? = null,
    direction: String = "FORWARD",
): SplitsTable {
    val resultByParticipantId = results.associateBy { it.participantId }
    val pairs = participants.map { it to resultByParticipantId[it.id] }

    if (direction == "BY_CHOICE") {
        val scoreByNumber = distance?.controlPoints?.associate { it.number to it.score } ?: emptyMap()
        val controlPointByNumber = distance?.controlPoints?.associateBy { it.number } ?: emptyMap()
        val maxSplitsCount = pairs.maxOfOrNull { (_, result) -> result?.splits?.size ?: 0 } ?: 0
        val columns = (1..maxSplitsCount).map { SplitsTableColumn(positionIndex = it, controlPoint = 0) }

        val rows = pairs.map { (participant, result) ->
            val splits = result?.splits ?: emptyList()
            val startTs = anchorStartTime(participant, result)

            val cells = (0 until maxSplitsCount).map { i ->
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
                    SplitsTableCell(
                        deltaSeconds = (splitTs - prevTs) / 1000L,
                        cumulativeSeconds = (splitTs - startTs) / 1000L,
                        deltaRank = null,
                        cumulativeRank = null,
                        isBestLeg = false,
                        controlPoint = splits[i].controlPoint,
                    )
                }
            }

            SplitsTableRow(
                participant = participant,
                result = result,
                cells = cells,
                rawScore = rawByChoiceScore(result, scoreByNumber),
                totalDistanceMeters = byChoiceDistanceMeters(splits, controlPointByNumber),
            )
        }

        return SplitsTable(columns = columns, rows = rows)
    }

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

/**
 * Строит данные графика набора очков во времени для BY_CHOICE (score-О): по оси времени —
 * секунды от старта участника, по оси очков — сумма очков за фактически взятые КП (сырая, ДО
 * вычета штрафа за опоздание — штраф не непрерывная функция времени, а разовое списание по
 * итогу, поэтому в кривую его включать не нужно; итоговое место/очки видны в легенде).
 * Каждая отметка добавляет точку — наклон отрезка между соседними точками показывает темп
 * набора очков на этом отрезке. Если участник финишировал позже последней отметки — добавляется
 * финальная плоская точка на totalTime, чтобы линия доходила до конца гонки.
 * Не финишировавшие исключаются — их кривая до конца не имеет смысла сравнивать.
 */
fun buildScoreGraphData(
    participants: List<OrienteeringParticipant>,
    results: List<OrienteeringResult>,
    distance: Distance? = null,
    timeLimitMinutes: Int? = null,
): ScoreGraphData {
    val scoreByNumber = distance?.controlPoints?.associate { it.number to it.score } ?: emptyMap()
    val resultByParticipantId = results.associateBy { it.participantId }
    val timeLimitSeconds = timeLimitMinutes?.takeIf { it > 0 }?.let { it * 60L }

    val series = participants.mapNotNull { participant ->
        val result = resultByParticipantId[participant.id] ?: return@mapNotNull null
        if (result.status != "FINISHED") return@mapNotNull null
        val startTs = anchorStartTime(participant, result) ?: return@mapNotNull null

        val rawPoints = mutableListOf(ScoreGraphPoint(0L, 0))
        var cumulative = 0
        result.splits?.forEach { split ->
            cumulative += scoreByNumber[split.controlPoint] ?: 0
            rawPoints += ScoreGraphPoint((split.timestamp - startTs) / 1000L, cumulative)
        }
        val finishSeconds = result.totalTime
        if (finishSeconds != null && finishSeconds > rawPoints.last().elapsedSeconds) {
            rawPoints += ScoreGraphPoint(finishSeconds, cumulative)
        }

        val deduction = (cumulative - (result.totalScore ?: cumulative)).coerceAtLeast(0)
        val points = applyLatePenalty(rawPoints, timeLimitSeconds, deduction)

        ScoreGraphSeries(participant = participant, result = result, points = points)
    }

    return ScoreGraphData(series = series, timeLimitSeconds = timeLimitSeconds)
}

/**
 * Применяет линейно нарастающий штраф за опоздание к "сырой" кривой очков: до [timeLimitSeconds]
 * кривая не меняется, после — вычитается штраф, линейно растущий от 0 в момент истечения лимита
 * до [totalDeduction] в момент финиша (последняя точка кривой). За величину штрафа берётся не
 * [OrienteeringResult.scorePenalty] напрямую (оно ненадёжно при полном обнулении результата за
 * сильное опоздание — см. [ControlPoint]-комментарий выше), а разница между суммой очков за
 * реально взятые КП и итоговым зачётным результатом — так конечная точка графика гарантированно
 * совпадает с официальным местом участника в любом случае.
 */
private fun applyLatePenalty(rawPoints: List<ScoreGraphPoint>, timeLimitSeconds: Long?, totalDeduction: Int): List<ScoreGraphPoint> {
    val finishSeconds = rawPoints.last().elapsedSeconds
    if (timeLimitSeconds == null || totalDeduction <= 0 || finishSeconds <= timeLimitSeconds) return rawPoints

    val rampSpan = (finishSeconds - timeLimitSeconds).toDouble()
    fun deductionAt(t: Long): Int =
        if (t <= timeLimitSeconds) 0 else (totalDeduction * (t - timeLimitSeconds) / rampSpan).toInt()

    val result = mutableListOf<ScoreGraphPoint>()
    var boundaryInserted = false
    for (i in rawPoints.indices) {
        val p = rawPoints[i]
        if (p.elapsedSeconds <= timeLimitSeconds) {
            result += p
        } else {
            if (!boundaryInserted) {
                result += ScoreGraphPoint(timeLimitSeconds, rawPoints[i - 1].cumulativeScore)
                boundaryInserted = true
            }
            result += ScoreGraphPoint(p.elapsedSeconds, (p.cumulativeScore - deductionAt(p.elapsedSeconds)).coerceAtLeast(0))
        }
    }
    return result
}
