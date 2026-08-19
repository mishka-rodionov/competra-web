package com.competra.web.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.ResultRepository
import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.ParticipantGroupDetail
import com.competra.web.utils.DEFAULT_TIME_ZONE
import com.competra.web.utils.utcMillisToZonedTime
import org.koin.compose.koinInject

@Composable
fun StartProtocolTab(
    competitionId: String,
    groups: List<ParticipantGroupDetail> = emptyList(),
    timeZoneId: String = DEFAULT_TIME_ZONE,
    onParticipantClick: (String) -> Unit = {},
) {
    val repo: ResultRepository = koinInject()
    var participants by remember { mutableStateOf<List<OrienteeringParticipant>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(competitionId) {
        when (val r = repo.getParticipants(competitionId)) {
            is ApiResult.Success -> participants = r.data
            is ApiResult.Error -> error = r.message
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
    if (participants.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
            Text("Участники ещё не зарегистрированы", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }

    val zone = timeZoneId.takeIf { it.isNotBlank() } ?: DEFAULT_TIME_ZONE

    // Стартовая минута — порядковый номер физического времени старта среди всех различных
    // времён старта в соревновании (1-я, 2-я, ...). При массовом старте у всех одно время —
    // все попадают в 1-ю минуту.
    val minuteByStartTime = participants.mapNotNull { it.startTime }.distinct().sorted()
        .withIndex().associate { (idx, time) -> time to (idx + 1) }

    val participantsByGroup = participants.groupBy { it.groupId }
    val groupOrder = sortedStartGroups(groups).map { it.groupId }
    val extraGroupIds = participantsByGroup.keys.filter { it !in groupOrder }
    val sortedGroupIds = (groupOrder + extraGroupIds).filter { !participantsByGroup[it].isNullOrEmpty() }
    val groupNamesById = groups.associate { it.groupId to it.title }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(sortedGroupIds) { groupId ->
            val groupParticipants = (participantsByGroup[groupId] ?: emptyList())
                .sortedWith(compareBy({ it.startTime ?: Long.MAX_VALUE }, { it.startNumber?.toIntOrNull() ?: Int.MAX_VALUE }))
            StartProtocolGroupCard(
                groupTitle = groupNamesById[groupId] ?: "Группа $groupId",
                participants = groupParticipants,
                minuteByStartTime = minuteByStartTime,
                zone = zone,
                onParticipantClick = onParticipantClick,
            )
        }
    }
}

/**
 * Мужские группы первыми, затем женские, остальные (смешанные/не указано) — в конце.
 *
 * Поле `gender` у групп сейчас заполняется редко ("M"/"F", см. [GENDER_OPTIONS] в
 * ManageCompetitionPage), поэтому если оно не задано, пол определяется по префиксу
 * названия группы ("М17", "Ж21" — стандартная нотация категорий в ориентировании).
 */
private fun sortedStartGroups(groups: List<ParticipantGroupDetail>): List<ParticipantGroupDetail> {
    fun genderPriority(group: ParticipantGroupDetail): Int {
        val gender = group.gender ?: inferGenderFromTitle(group.title)
        return when (gender) {
            "M" -> 0
            "F" -> 1
            else -> 2
        }
    }
    return groups.sortedBy { genderPriority(it) }
}

private fun inferGenderFromTitle(title: String): String? = when {
    title.startsWith("М", ignoreCase = true) -> "M"
    title.startsWith("Ж", ignoreCase = true) -> "F"
    else -> null
}

@Composable
private fun StartProtocolGroupCard(
    groupTitle: String,
    participants: List<OrienteeringParticipant>,
    minuteByStartTime: Map<Long, Int>,
    zone: String,
    onParticipantClick: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(groupTitle, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("№", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.5f))
                Text("Участник", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(2f))
                Text("Время старта", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text("Стартовый интервал", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            participants.forEach { participant ->
                StartProtocolRow(
                    participant = participant,
                    minuteByStartTime = minuteByStartTime,
                    zone = zone,
                    onClick = { onParticipantClick(participant.id) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun StartProtocolRow(
    participant: OrienteeringParticipant,
    minuteByStartTime: Map<Long, Int>,
    zone: String,
    onClick: () -> Unit,
) {
    val name = "${participant.lastName} ${participant.firstName}"
    val timeStr = participant.startTime?.let { utcMillisToZonedTime(it, zone) } ?: "—"
    val minuteStr = participant.startTime?.let { minuteByStartTime[it] }?.let { formatStartInterval(it) } ?: "—"

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(participant.startNumber ?: "—", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(0.5f))
        Column(modifier = Modifier.weight(2f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium)
            participant.commandName?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(timeStr, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(minuteStr, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** Порядковый номер минуты старта в виде "01:00", "02:00" и т.д. */
private fun formatStartInterval(minute: Int): String = "${minute.toString().padStart(2, '0')}:00"
