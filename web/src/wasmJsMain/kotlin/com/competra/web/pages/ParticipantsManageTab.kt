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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.competra.domain.models.OrienteeringCompetition
import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.ParticipantGroupDetail
import com.competra.domain.models.SaveParticipantRequest
import com.competra.web.components.LabeledDropdown
import com.competra.web.components.TimeField
import com.competra.web.utils.generateUUID
import com.competra.web.utils.utcMillisToZonedTime
import com.competra.web.utils.zonedDateTimeToUtcMillis
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantsManageTab(competition: OrienteeringCompetition) {
    val groupRepo: GroupRepository = koinInject()
    val resultRepo: ResultRepository = koinInject()
    val scope = rememberCoroutineScope()
    val competitionId = competition.competitionId

    var groups by remember { mutableStateOf<List<ParticipantGroupDetail>>(emptyList()) }
    var participants by remember { mutableStateOf<List<OrienteeringParticipant>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var editingParticipant by remember { mutableStateOf<OrienteeringParticipant?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deletingParticipant by remember { mutableStateOf<OrienteeringParticipant?>(null) }

    LaunchedEffect(competitionId) {
        when (val r = groupRepo.getGroups(competitionId)) {
            is ApiResult.Success -> groups = r.data
            is ApiResult.Error -> error = r.message
        }
        when (val r = resultRepo.getParticipants(competitionId)) {
            is ApiResult.Success -> participants = r.data
            is ApiResult.Error -> error = r.message
        }
        loading = false
    }

    val hasAllTab = groups.size > 1
    val currentGroup = if (hasAllTab) groups.getOrNull(selectedTab - 1) else groups.getOrNull(selectedTab)
    val visibleParticipants = if (hasAllTab && selectedTab == 0) {
        participants
    } else {
        participants.filter { it.groupId == currentGroup?.groupId }
    }.sortedWith(compareBy({ it.startTime ?: Long.MAX_VALUE }, { it.startNumber?.toIntOrNull() ?: Int.MAX_VALUE }))

    if (showEditor) {
        ParticipantEditorDialog(
            competition = competition,
            groups = groups,
            defaultGroup = currentGroup ?: groups.firstOrNull(),
            editingParticipant = editingParticipant,
            onDismiss = { showEditor = false; editingParticipant = null },
            onSaved = { saved ->
                participants = if (editingParticipant == null) {
                    participants + saved
                } else {
                    participants.map { if (it.id == saved.id) saved else it }
                }
                showEditor = false
                editingParticipant = null
            },
            onError = { error = it },
        )
    }

    deletingParticipant?.let { participant ->
        AlertDialog(
            onDismissRequest = { deletingParticipant = null },
            title = { Text("Удалить участника?") },
            text = { Text("${participant.lastName} ${participant.firstName}".trim()) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (resultRepo.deleteParticipant(participant.id)) {
                            is ApiResult.Success -> participants = participants.filter { it.id != participant.id }
                            is ApiResult.Error -> error = "Не удалось удалить участника"
                        }
                        deletingParticipant = null
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { deletingParticipant = null }) { Text("Отмена") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Участники", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(
                onClick = { editingParticipant = null; showEditor = true },
                enabled = groups.isNotEmpty(),
            ) {
                Text("+ Добавить")
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Сначала добавьте группы участников на вкладке «Группы»",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        ScrollableTabRow(selectedTabIndex = selectedTab, edgePadding = 16.dp) {
            if (hasAllTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Все") })
            }
            groups.forEachIndexed { index, group ->
                val tabIndex = if (hasAllTab) index + 1 else index
                Tab(selected = selectedTab == tabIndex, onClick = { selectedTab = tabIndex }, text = { Text(group.title) })
            }
        }

        if (visibleParticipants.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("В этой группе пока нет участников", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visibleParticipants, key = { it.id }) { participant ->
                    ParticipantManageCard(
                        participant = participant,
                        showGroupName = hasAllTab && selectedTab == 0,
                        onEdit = { editingParticipant = participant; showEditor = true },
                        onDelete = { deletingParticipant = participant },
                    )
                }
            }
        }
    }
}

@Composable
private fun ParticipantManageCard(
    participant: OrienteeringParticipant,
    showGroupName: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "№${participant.startNumber ?: "—"}  ${participant.lastName} ${participant.firstName}".trim(),
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (showGroupName) {
                    participant.groupName?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                participant.commandName?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Редактировать")
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Удалить",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParticipantEditorDialog(
    competition: OrienteeringCompetition,
    groups: List<ParticipantGroupDetail>,
    defaultGroup: ParticipantGroupDetail?,
    editingParticipant: OrienteeringParticipant?,
    onDismiss: () -> Unit,
    onSaved: (OrienteeringParticipant) -> Unit,
    onError: (String) -> Unit,
) {
    val resultRepo: ResultRepository = koinInject()
    val scope = rememberCoroutineScope()
    val zoneId = competition.competition.timeZoneId.ifBlank { "Europe/Moscow" }
    val baseDateMillis = competition.competition.startDate

    var lastName by remember { mutableStateOf(editingParticipant?.lastName ?: "") }
    var firstName by remember { mutableStateOf(editingParticipant?.firstName ?: "") }
    var commandName by remember { mutableStateOf(editingParticipant?.commandName ?: "") }
    var startNumber by remember { mutableStateOf(editingParticipant?.startNumber ?: "") }
    var selectedGroupId by remember {
        mutableStateOf(editingParticipant?.groupId ?: defaultGroup?.groupId ?: groups.firstOrNull()?.groupId ?: 0L)
    }
    var startTimeStr by remember {
        mutableStateOf(
            editingParticipant?.startTime?.let { utcMillisToZonedTime(it, zoneId) } ?: "10:00"
        )
    }
    var saving by remember { mutableStateOf(false) }

    fun save() {
        val group = groups.firstOrNull { it.groupId == selectedGroupId } ?: return
        val numberInt = startNumber.toIntOrNull()
        if (lastName.isBlank() || firstName.isBlank() || numberInt == null) {
            onError("Заполните фамилию, имя и номер старта (числом)")
            return
        }
        val startTimeMillis = zonedDateTimeToUtcMillis(baseDateMillis, startTimeStr, zoneId)
        val request = SaveParticipantRequest(
            id = editingParticipant?.id ?: generateUUID(),
            userId = editingParticipant?.userId,
            firstName = firstName.trim(),
            lastName = lastName.trim(),
            groupId = group.groupId,
            groupName = group.title,
            competitionId = competition.competitionId,
            commandName = commandName.trim().ifEmpty { null },
            startNumber = numberInt,
            startTime = startTimeMillis,
            chipNumber = editingParticipant?.chipNumber ?: 0,
            comment = editingParticipant?.comment,
            isChipGiven = editingParticipant?.isChipGiven ?: false,
        )
        saving = true
        scope.launch {
            when (val r = resultRepo.saveParticipant(request)) {
                is ApiResult.Success -> onSaved(r.data)
                is ApiResult.Error -> onError(r.message)
            }
            saving = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingParticipant == null) "Новый участник" else "Редактирование участника") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = lastName,
                    onValueChange = { lastName = it },
                    label = { Text("Фамилия") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = firstName,
                    onValueChange = { firstName = it },
                    label = { Text("Имя") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                LabeledDropdown(
                    label = "Группа",
                    selectedKey = selectedGroupId,
                    options = groups.map { it.groupId to it.title },
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { selectedGroupId = it },
                )
                OutlinedTextField(
                    value = startNumber,
                    onValueChange = { startNumber = it.filter { c -> c.isDigit() } },
                    label = { Text("Номер старта") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                TimeField(
                    label = "Время старта",
                    value = startTimeStr,
                    onChange = { startTimeStr = it },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = commandName,
                    onValueChange = { commandName = it },
                    label = { Text("Команда (опционально)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { save() }, enabled = !saving) {
                Text(if (editingParticipant == null) "Добавить" else "Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}
