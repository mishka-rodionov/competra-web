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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.ClubRepository
import com.competra.data.repository.TeamRepository
import com.competra.data.repository.UserRepository
import com.competra.domain.models.AddTeamMemberRequest
import com.competra.domain.models.ChangeTeamMemberRoleRequest
import com.competra.domain.models.ClubMember
import com.competra.domain.models.Team
import com.competra.domain.models.TeamMember
import com.competra.domain.models.UpdateTeamRequest
import com.competra.web.components.LabeledDropdown
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private fun teamRoleLabel(role: String) = when (role) {
    "CAPTAIN" -> "Капитан"
    "MEMBER" -> "Участник"
    else -> role
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TeamDetailPage(teamId: String, onBack: () -> Unit) {
    val teamRepo: TeamRepository = koinInject()
    val clubRepo: ClubRepository = koinInject()
    val userRepo: UserRepository = koinInject()
    val scope = rememberCoroutineScope()

    var team by remember { mutableStateOf<Team?>(null) }
    var teamMembers by remember { mutableStateOf<List<TeamMember>>(emptyList()) }
    var clubMembers by remember { mutableStateOf<List<ClubMember>>(emptyList()) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    suspend fun reload() {
        val teamResult = teamRepo.getTeam(teamId)
        val loadedTeam = when (teamResult) {
            is ApiResult.Success -> teamResult.data.also { team = it }
            is ApiResult.Error -> { error = teamResult.message; null }
        }
        when (val r = teamRepo.getTeamMembers(teamId)) {
            is ApiResult.Success -> teamMembers = r.data
            is ApiResult.Error -> {}
        }
        if (loadedTeam != null) {
            when (val r = clubRepo.getClubMembers(loadedTeam.clubId)) {
                is ApiResult.Success -> clubMembers = r.data
                is ApiResult.Error -> {}
            }
        }
        when (val r = userRepo.getUserProfile()) {
            is ApiResult.Success -> currentUserId = r.data.id
            is ApiResult.Error -> {}
        }
        loading = false
    }

    LaunchedEffect(teamId, reloadKey) { reload() }

    val myClubRole = clubMembers.firstOrNull { it.userId == currentUserId }?.role
    val isClubAdmin = myClubRole in listOf("FOUNDER", "ADMIN")

    if (showEditDialog && team != null) {
        EditTeamDialog(
            team = team!!,
            onDismiss = { showEditDialog = false },
            onSaved = { updated -> team = updated; showEditDialog = false },
            onError = { actionError = it },
        )
    }

    if (showAddMemberDialog && team != null) {
        val availableClubMembers = clubMembers.filter { cm -> teamMembers.none { it.clubMemberId == cm.id } }
        AddTeamMemberDialog(
            candidates = availableClubMembers,
            onDismiss = { showAddMemberDialog = false },
            onAdd = { clubMemberId ->
                scope.launch {
                    when (teamRepo.addTeamMember(teamId, AddTeamMemberRequest(clubMemberId))) {
                        is ApiResult.Success -> { reloadKey++; showAddMemberDialog = false }
                        is ApiResult.Error -> actionError = "Не удалось добавить участника"
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(team?.name ?: "Команда", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        val t = team
        if (t == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Команда не найдена")
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(sportLabel(t.sportType), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                if (isClubAdmin) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showEditDialog = true }) { Text("Редактировать") }
                        OutlinedButton(onClick = { showAddMemberDialog = true }) { Text("+ Участник") }
                    }
                }
            }

            if (teamMembers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("В команде пока нет участников", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(teamMembers, key = { it.id }) { member ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${member.lastName} ${member.firstName}".trim(), style = MaterialTheme.typography.bodyMedium)
                                    Text(teamRoleLabel(member.role), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                }
                                val isSelf = member.userId == currentUserId
                                if (isClubAdmin || isSelf) {
                                    var menuExpanded by remember { mutableStateOf(false) }
                                    Box {
                                        TextButton(onClick = { menuExpanded = true }) { Text("Действия") }
                                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                            if (isClubAdmin && member.role == "MEMBER") {
                                                DropdownMenuItem(text = { Text("Назначить капитаном") }, onClick = {
                                                    scope.launch {
                                                        when (teamRepo.changeTeamMemberRole(teamId, member.clubMemberId, ChangeTeamMemberRoleRequest("CAPTAIN"))) {
                                                            is ApiResult.Success -> reloadKey++
                                                            is ApiResult.Error -> actionError = "Не удалось изменить роль"
                                                        }
                                                    }
                                                    menuExpanded = false
                                                })
                                            }
                                            if (isClubAdmin && member.role == "CAPTAIN") {
                                                DropdownMenuItem(text = { Text("Снять капитана") }, onClick = {
                                                    scope.launch {
                                                        when (teamRepo.changeTeamMemberRole(teamId, member.clubMemberId, ChangeTeamMemberRoleRequest("MEMBER"))) {
                                                            is ApiResult.Success -> reloadKey++
                                                            is ApiResult.Error -> actionError = "Не удалось изменить роль"
                                                        }
                                                    }
                                                    menuExpanded = false
                                                })
                                            }
                                            DropdownMenuItem(
                                                text = { Text(if (isSelf) "Покинуть команду" else "Удалить из команды") },
                                                onClick = {
                                                    scope.launch {
                                                        when (teamRepo.removeTeamMember(teamId, member.clubMemberId)) {
                                                            is ApiResult.Success -> reloadKey++
                                                            is ApiResult.Error -> actionError = "Не удалось удалить участника"
                                                        }
                                                    }
                                                    menuExpanded = false
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTeamDialog(
    team: Team,
    onDismiss: () -> Unit,
    onSaved: (Team) -> Unit,
    onError: (String) -> Unit,
) {
    val repo: TeamRepository = koinInject()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(team.name) }
    var sportType by remember { mutableStateOf(team.sportType) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактирование команды") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                LabeledDropdown(
                    label = "Вид спорта",
                    selectedKey = sportType,
                    options = SPORT_TYPES,
                    modifier = Modifier.fillMaxWidth(),
                    onSelect = { sportType = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { onError("Укажите название"); return@TextButton }
                saving = true
                scope.launch {
                    when (val r = repo.updateTeam(team.id, UpdateTeamRequest(name.trim(), sportType))) {
                        is ApiResult.Success -> onSaved(r.data)
                        is ApiResult.Error -> onError(r.message)
                    }
                    saving = false
                }
            }, enabled = !saving) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddTeamMemberDialog(
    candidates: List<ClubMember>,
    onDismiss: () -> Unit,
    onAdd: (clubMemberId: String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить участника") },
        text = {
            if (candidates.isEmpty()) {
                Text("Все участники клуба уже в команде")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    candidates.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(member.id) }
                                .padding(vertical = 10.dp),
                        ) {
                            Text("${member.lastName} ${member.firstName}".trim(), style = MaterialTheme.typography.bodyMedium)
                        }
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
