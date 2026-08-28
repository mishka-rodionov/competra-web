package com.competra.web.utils

import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.ParticipantGroupDetail

/** Одна строка плана импорта — распознанные данные плюс уже существующий участник, если нашёлся. */
data class PastResultsRowPlan(
    val row: ParsedPastResultRow,
    val existingParticipant: OrienteeringParticipant?,
)

data class PastResultsImportPlan(
    /** Названия групп, которых ещё нет в соревновании — будут созданы при импорте. */
    val groupsToCreate: List<String>,
    /** Уже существующие в соревновании группы: название -> id. */
    val existingGroupIdByTitle: Map<String, Long>,
    val rowPlans: List<PastResultsRowPlan>,
    /** Строки файла, которые не удалось распознать (см. [ParsedPastResultsFile.totalRowsInFile]). */
    val skippedRows: Int,
    /**
     * Все участники соревнования на момент построения плана (не только сматченные из файла) —
     * нужны, чтобы новым участникам назначить стартовые номера, не пересекающиеся с уже
     * существующими (в т.ч. с теми, кого нет в этом Excel-файле вовсе).
     */
    val existingParticipants: List<OrienteeringParticipant>,
)

/** Ищет группу по названию без учёта регистра — Excel и текущие группы могут отличаться регистром. */
fun Map<String, Long>.findGroupIdIgnoreCase(title: String): Long? =
    entries.firstOrNull { it.key.equals(title, ignoreCase = true) }?.value

/**
 * Строит план импорта: какие группы создать, каких участников создать (не найдены по ФИО+группе
 * среди уже заведённых), и результаты для всех строк. Побочных эффектов нет — сохранение делает
 * [com.competra.web.pages.ImportPastResultsReviewPage] после подтверждения.
 */
fun buildPastResultsPlan(
    parsedFile: ParsedPastResultsFile,
    existingGroups: List<ParticipantGroupDetail>,
    existingParticipants: List<OrienteeringParticipant>,
): PastResultsImportPlan {
    val existingGroupIdByTitle = existingGroups.associate { it.title to it.groupId }
    val groupsToCreate = parsedFile.rows.map { it.groupTitle }.distinct()
        .filter { existingGroupIdByTitle.findGroupIdIgnoreCase(it) == null }

    fun findExisting(row: ParsedPastResultRow): OrienteeringParticipant? =
        existingParticipants.firstOrNull {
            it.lastName.equals(row.lastName, ignoreCase = true) &&
                it.firstName.equals(row.firstName, ignoreCase = true) &&
                it.groupName?.equals(row.groupTitle, ignoreCase = true) == true
        }

    return PastResultsImportPlan(
        groupsToCreate = groupsToCreate,
        existingGroupIdByTitle = existingGroupIdByTitle,
        rowPlans = parsedFile.rows.map { row -> PastResultsRowPlan(row, findExisting(row)) },
        skippedRows = parsedFile.totalRowsInFile - parsedFile.rows.size,
        existingParticipants = existingParticipants,
    )
}
