package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import org.koin.compose.koinInject

@Composable
fun ResultsTab(competitionId: String, groups: List<ParticipantGroupDetail> = emptyList()) {
    val repo: ResultRepository = koinInject()
    var results by remember { mutableStateOf<List<OrienteeringResult>>(emptyList()) }
    var participants by remember { mutableStateOf<List<OrienteeringParticipant>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(competitionId) {
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
        loading = false
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
        sortedGroupIds.forEach { groupId ->
            val groupResults = (resultsByGroup[groupId] ?: emptyList())
                .sortedWith(compareBy(nullsLast()) { it.rank })
            if (groupResults.isEmpty()) return@forEach

            item {
                Text(
                    groupNamesById[groupId] ?: "Группа $groupId",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                HorizontalDivider()
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("#", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.4f))
                    Text("Участник", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(2f))
                    Text("Время", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text("Статус", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                }
                HorizontalDivider()
            }

            items(groupResults) { result ->
                val participant = participantsById[result.participantId]
                ResultRow(result = result, participant = participant)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun ResultRow(result: OrienteeringResult, participant: OrienteeringParticipant?) {
    val name = if (participant != null) "${participant.lastName} ${participant.firstName}" else "Участник ${result.participantId}"
    val timeStr = result.totalTime?.let { formatTime(it) } ?: "—"
    val statusStr = resultStatusLabel(result.status)

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            result.rank?.toString() ?: "—",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(0.4f),
        )
        Column(modifier = Modifier.weight(2f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            participant?.startNumber?.let {
                Text("№$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun resultStatusColor(status: String) = when (status) {
    "FINISHED" -> MaterialTheme.colorScheme.primary
    "DNF" -> MaterialTheme.colorScheme.error
    "DNS" -> MaterialTheme.colorScheme.onSurfaceVariant
    "DSQ" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun resultStatusLabel(status: String) = when (status) {
    "FINISHED" -> "Финиш"
    "DNF" -> "НФ"
    "DNS" -> "НС"
    "DSQ" -> "Дискв."
    else -> status
}
