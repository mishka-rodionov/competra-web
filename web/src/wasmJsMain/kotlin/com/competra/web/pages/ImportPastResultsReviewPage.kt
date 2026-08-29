package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.GroupRepository
import com.competra.data.repository.ResultRepository
import com.competra.domain.models.CreateGroupRequest
import com.competra.domain.models.OrienteeringCompetition
import com.competra.domain.models.OrienteeringResult
import com.competra.domain.models.SaveParticipantRequest
import com.competra.domain.models.SaveResultRequest
import com.competra.web.utils.PastResultsImportPlan
import com.competra.web.utils.PastResultsRowPlan
import com.competra.web.utils.findGroupIdIgnoreCase
import com.competra.web.utils.formatTime
import com.competra.web.utils.generateUUID
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * Превью перед импортом результатов прошедшего соревнования из Excel: показывает, какие группы
 * и участники будут созданы, а какие — только обновят результат. При подтверждении сохраняет
 * последовательно группы -> участников -> результаты (каждый следующий шаг зависит от id,
 * полученных на предыдущем).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportPastResultsReviewPage(
    competition: OrienteeringCompetition,
    plan: PastResultsImportPlan,
    existingResults: List<OrienteeringResult>,
    onBack: () -> Unit,
    onImported: () -> Unit,
) {
    val groupRepo: GroupRepository = koinInject()
    val resultRepo: ResultRepository = koinInject()
    val scope = rememberCoroutineScope()

    var importing by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val newParticipantsCount = plan.rowPlans.count { it.existingParticipant == null }
    val updatedCount = plan.rowPlans.size - newParticipantsCount
    val rowsByGroup = plan.rowPlans.groupBy { it.row.groupTitle }.toList().sortedBy { it.first }

    fun runImport() {
        scope.launch {
            importing = true
            error = null

            var createdGroupsByTitle = emptyMap<String, Long>()
            if (plan.groupsToCreate.isNotEmpty()) {
                val requests = plan.groupsToCreate.map { title ->
                    CreateGroupRequest(groupId = null, competitionId = competition.competitionId, title = title)
                }
                when (val gr = groupRepo.saveGroups(requests)) {
                    is ApiResult.Success -> createdGroupsByTitle = plan.groupsToCreate.zip(gr.data.map { it.groupId }).toMap()
                    is ApiResult.Error -> { error = "Группы: ${gr.message}"; importing = false; return@launch }
                }
            }

            fun resolveGroupId(title: String): Long =
                plan.existingGroupIdByTitle.findGroupIdIgnoreCase(title)
                    ?: createdGroupsByTitle.findGroupIdIgnoreCase(title)
                    ?: 0L

            val competitionStart = competition.competition.startDate
            var nextStartNumber = (plan.existingParticipants.mapNotNull { it.startNumber?.toIntOrNull() }.maxOrNull() ?: 0) + 1

            val participantIds = arrayOfNulls<String>(plan.rowPlans.size)
            plan.rowPlans.forEachIndexed { idx, rp -> participantIds[idx] = rp.existingParticipant?.id }

            val newIndexed = plan.rowPlans.withIndex().filter { it.value.existingParticipant == null }
            if (newIndexed.isNotEmpty()) {
                val requests = newIndexed.map { (_, rp) ->
                    SaveParticipantRequest(
                        id = generateUUID(),
                        firstName = rp.row.firstName,
                        lastName = rp.row.lastName,
                        groupId = resolveGroupId(rp.row.groupTitle),
                        groupName = rp.row.groupTitle,
                        competitionId = competition.competitionId,
                        startNumber = nextStartNumber++,
                        startTime = competitionStart + (rp.row.startOffsetSeconds ?: 0L) * 1000,
                    )
                }
                when (val pr = resultRepo.saveParticipants(requests)) {
                    is ApiResult.Success -> {
                        newIndexed.zip(pr.data).forEach { (indexed, saved) -> participantIds[indexed.index] = saved.id }
                    }
                    is ApiResult.Error -> { error = "Участники: ${pr.message}"; importing = false; return@launch }
                }
            }

            val resultRequests = plan.rowPlans.mapIndexedNotNull { idx, rp ->
                val participantId = participantIds[idx] ?: return@mapIndexedNotNull null
                val existingResult = existingResults.firstOrNull { it.participantId == participantId }
                val startMillis = rp.row.startOffsetSeconds?.let { competitionStart + it * 1000 } ?: existingResult?.startTime
                val finishMillis = rp.row.finishOffsetSeconds?.let { competitionStart + it * 1000 } ?: existingResult?.finishTime
                SaveResultRequest(
                    id = existingResult?.id ?: generateUUID(),
                    competitionId = competition.competitionId,
                    groupId = resolveGroupId(rp.row.groupTitle),
                    participantId = participantId,
                    startTime = startMillis,
                    finishTime = finishMillis,
                    totalTime = rp.row.totalTimeSeconds ?: existingResult?.totalTime,
                    rank = rp.row.rank ?: existingResult?.rank,
                    status = rp.row.status,
                    penaltyTime = existingResult?.penaltyTime ?: 0,
                    splits = existingResult?.splits,
                    isEditable = existingResult?.isEditable ?: true,
                    isEdited = true,
                )
            }

            when (val rr = resultRepo.saveResults(resultRequests)) {
                is ApiResult.Success -> { importing = false; showConfirm = false; onImported() }
                is ApiResult.Error -> { error = "Результаты: ${rr.message}"; importing = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Импорт результатов: ${competition.competition.title}", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack, enabled = !importing) { Text("Отмена") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = plan.rowPlans.isNotEmpty() && !importing, onClick = { showConfirm = true }) {
                        if (importing) CircularProgressIndicator(modifier = Modifier.size(18.dp))
                        else Text("Импортировать (${plan.rowPlans.size})")
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Новых групп: ${plan.groupsToCreate.size} • Новых участников: $newParticipantsCount • Обновится: $updatedCount",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (plan.skippedRows > 0) {
                    Text(
                        "Не распознано и пропущено строк: ${plan.skippedRows}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
            }

            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp)) {
                if (plan.rowPlans.isEmpty()) {
                    item {
                        Text(
                            "Нет данных для импорта",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                rowsByGroup.forEach { (groupTitle, rows) ->
                    val isNewGroup = plan.groupsToCreate.any { it.equals(groupTitle, ignoreCase = true) }
                    item(key = "header-$groupTitle") {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(groupTitle, style = MaterialTheme.typography.titleSmall)
                            if (isNewGroup) {
                                Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                                    Text(
                                        "новая группа",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                    items(rows.sortedWith(compareBy(nullsLast()) { it.row.rank }), key = { "${it.row.lastName}-${it.row.firstName}-${it.row.groupTitle}" }) { rowPlan ->
                        PastResultRow(rowPlan)
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!importing) showConfirm = false },
            title = { Text("Импортировать результаты?") },
            text = {
                Text("Будет создано ${plan.groupsToCreate.size} групп, $newParticipantsCount участников, сохранено ${plan.rowPlans.size} результатов.")
            },
            confirmButton = {
                Button(enabled = !importing, onClick = { runImport() }) {
                    if (importing) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Импортировать")
                }
            },
            dismissButton = {
                TextButton(enabled = !importing, onClick = { showConfirm = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun PastResultRow(rowPlan: PastResultsRowPlan) {
    val row = rowPlan.row
    val isNew = rowPlan.existingParticipant == null
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.rank?.toString() ?: "—", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.4f))
        Column(modifier = Modifier.weight(2f)) {
            Text("${row.lastName} ${row.firstName}", style = MaterialTheme.typography.bodyLarge)
            if (isNew) {
                Text("новый участник", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(
            row.totalTimeSeconds?.let { formatTime(it) } ?: "—",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Text(
            resultStatusLabel(row.status),
            style = MaterialTheme.typography.labelSmall,
            color = resultStatusColor(row.status),
            modifier = Modifier.weight(1f),
        )
    }
}
