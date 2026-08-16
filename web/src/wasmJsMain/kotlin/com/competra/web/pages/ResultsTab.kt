package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.ResultRepository
import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.OrienteeringResult
import com.competra.domain.models.ParticipantGroupDetail
import com.competra.web.utils.formatTime
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val LIVE_POLL_INTERVAL_MS = 30_000L
private val LIVE_STATUSES = setOf("IN_PROGRESS", "STARTED")

@Composable
fun ResultsTab(
    competitionId: String,
    groups: List<ParticipantGroupDetail> = emptyList(),
    competitionStatus: String = "",
    resultsStatus: String = "NOT_PUBLISHED",
    direction: String = "FORWARD",
    onParticipantClick: (String) -> Unit = {},
    onGroupSplitsClick: (Long, String, Long?) -> Unit = { _, _, _ -> },
    onRaceGraphClick: (Long, String, Long?) -> Unit = { _, _, _ -> },
) {
    val isByChoice = direction == "BY_CHOICE"
    val repo: ResultRepository = koinInject()
    var results by remember { mutableStateOf<List<OrienteeringResult>>(emptyList()) }
    var participants by remember { mutableStateOf<List<OrienteeringParticipant>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val rResults = repo.getResults(competitionId)
        val rParticipants = repo.getParticipants(competitionId)
        when (rResults) {
            is ApiResult.Success -> results = rResults.data
            is ApiResult.Error -> error = rResults.message
        }
        when (rParticipants) {
            is ApiResult.Success -> participants = rParticipants.data
            is ApiResult.Error -> {}
        }
    }

    LaunchedEffect(competitionId) {
        reload()
        loading = false
    }

    if (competitionStatus in LIVE_STATUSES) {
        LaunchedEffect(competitionId) {
            while (true) {
                delay(LIVE_POLL_INTERVAL_MS)
                reload()
            }
        }
    }

    if (loading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    error?.let {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        return
    }
    if (results.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Результаты ещё не опубликованы", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val participantsById = participants.associateBy { it.id }

    val groupOrder = groups.map { it.groupId }
    val resultsByGroup = results.groupBy { it.groupId }
    val sortedGroupIds = (groupOrder + resultsByGroup.keys.filter { it !in groupOrder }).distinct()
    val groupNamesById = groups.associate { it.groupId to it.title }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (competitionStatus in LIVE_STATUSES) {
            item {
                Text(
                    "● LIVE — обновляется автоматически",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        if (resultsStatus == "PRELIMINARY") {
            item {
                Text(
                    "Результаты предварительные",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }
        sortedGroupIds.forEach { groupId ->
            val groupResults = (resultsByGroup[groupId] ?: emptyList())
                .sortedWith(compareBy(nullsLast()) { it.rank })
            if (groupResults.isEmpty()) return@forEach
            val groupTitle = groupNamesById[groupId] ?: "Группа $groupId"
            val distanceId = groups.firstOrNull { it.groupId == groupId }?.distanceId

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(groupTitle, style = MaterialTheme.typography.titleSmall)
                    if (!isByChoice) {
                        Row {
                            TextButton(onClick = { onGroupSplitsClick(groupId, groupTitle, distanceId) }) { Text("Сплиты") }
                            TextButton(onClick = { onRaceGraphClick(groupId, groupTitle, distanceId) }) { Text("График") }
                        }
                    }
                }
                HorizontalDivider()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("#", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.4f))
                    Text("Участник", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(if (isByChoice) 1.6f else 2f))
                    if (isByChoice) {
                        Text("Очки", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.7f))
                    }
                    Text("Время", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("Статус", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
            }

            items(groupResults) { result ->
                val participant = participantsById[result.participantId]
                ResultRow(
                    result = result,
                    participant = participant,
                    isByChoice = isByChoice,
                    onClick = { onParticipantClick(result.participantId) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ResultRow(
    result: OrienteeringResult,
    participant: OrienteeringParticipant?,
    isByChoice: Boolean,
    onClick: () -> Unit,
) {
    val name = if (participant != null) "${participant.lastName} ${participant.firstName}" else "Участник ${result.participantId}"
    val timeStr = result.totalTime?.let { formatTime(it) } ?: "—"
    val statusStr = resultStatusLabel(result.status)

    // Переходы к сплитам/графику скрыты для BY_CHOICE (порядок КП не регламентирован) —
    // строка результата тоже не кликабельна, кликать там больше некуда.
    val rowModifier = if (isByChoice) {
        Modifier.fillMaxWidth().padding(vertical = 6.dp)
    } else {
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp)
    }

    Row(
        modifier = rowModifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            result.rank?.toString() ?: "—",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.4f),
        )
        Column(modifier = Modifier.weight(if (isByChoice) 1.6f else 2f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            participant?.startNumber?.let {
                Text("№$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (isByChoice) {
            Text(
                result.totalScore?.toString() ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(0.7f),
            )
        }
        Text(
            timeStr,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            statusStr,
            style = MaterialTheme.typography.labelSmall,
            color = resultStatusColor(result.status),
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
internal fun resultStatusColor(status: String) = when (status) {
    "FINISHED" -> MaterialTheme.colorScheme.primary
    "DNF" -> MaterialTheme.colorScheme.error
    "DNS" -> MaterialTheme.colorScheme.onSurfaceVariant
    "DSQ" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun resultStatusLabel(status: String) = when (status) {
    "FINISHED" -> "Финиш"
    "DNF" -> "НФ"
    "DNS" -> "НС"
    "DSQ" -> "Дискв."
    else -> status
}
