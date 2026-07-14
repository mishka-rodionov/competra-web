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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.ClubRepository
import com.competra.data.repository.TeamRepository
import com.competra.data.repository.UserRepository
import com.competra.domain.models.ChangeRoleRequest
import com.competra.domain.models.Club
import com.competra.domain.models.ClubMember
import com.competra.domain.models.CreateTeamRequest
import com.competra.domain.models.Team
import com.competra.domain.models.UpdateClubRequest
import com.competra.web.components.LabeledDropdown
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private fun clubRoleLabel(role: String) = when (role) {
    "FOUNDER" -> "Основатель"
    "ADMIN" -> "Администратор"
    "MEMBER" -> "Участник"
    else -> role
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubDetailPage(
    clubId: String,
    onBack: () -> Unit,
    onLoginSuccess: () -> Unit,
    onJoinRequestsClick: (String) -> Unit,
    onTeamClick: (String) -> Unit,
) {
    val clubRepo: ClubRepository = koinInject()
    val teamRepo: TeamRepository = koinInject()
    val userRepo: UserRepository = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val isLoggedIn = tokenStorage.isLoggedIn()
    val scope = rememberCoroutineScope()

    var showLogin by remember { mutableStateOf(false) }
    var club by remember { mutableStateOf<Club?>(null) }
    var members by remember { mutableStateOf<List<ClubMember>>(emptyList()) }
    var teams by remember { mutableStateOf<List<Team>>(emptyList()) }
    var currentUserId by remember { mutableStateOf<String?>(null) }
    var myPendingRequest by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateTeamDialog by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }

    suspend fun reload() {
        when (val r = clubRepo.getClub(clubId)) {
            is ApiResult.Success -> club = r.data
            is ApiResult.Error -> error = r.message
        }
        when (val r = clubRepo.getClubMembers(clubId)) {
            is ApiResult.Success -> members = r.data
            is ApiResult.Error -> {}
        }
        when (val r = teamRepo.getTeamsByClub(clubId)) {
            is ApiResult.Success -> teams = r.data
            is ApiResult.Error -> {}
        }
        if (isLoggedIn) {
            when (val r = userRepo.getUserProfile()) {
                is ApiResult.Success -> currentUserId = r.data.id
                is ApiResult.Error -> {}
            }
            when (val r = clubRepo.getMyJoinRequests()) {
                is ApiResult.Success -> myPendingRequest = r.data.any { it.clubId == clubId && it.status == "PENDING" }
                is ApiResult.Error -> {}
            }
        }
        loading = false
    }

    LaunchedEffect(clubId, reloadKey) { reload() }

    if (showLogin) {
        LoginPage(onLoginSuccess = { showLogin = false; onLoginSuccess() })
        return
    }

    val myMembership = members.firstOrNull { it.userId == currentUserId }
    val isAdmin = myMembership?.role in listOf("FOUNDER", "ADMIN")
    val isFounder = myMembership?.role == "FOUNDER"

    if (showEditDialog && club != null) {
        EditClubDialog(
            club = club!!,
            onDismiss = { showEditDialog = false },
            onSaved = { updated -> club = updated; showEditDialog = false },
            onError = { actionError = it },
        )
    }

    if (showCreateTeamDialog) {
        CreateTeamDialog(
            onDismiss = { showCreateTeamDialog = false },
            onCreate = { name, sportType ->
                scope.launch {
                    when (val r = teamRepo.createTeam(clubId, CreateTeamRequest(name, sportType))) {
                        is ApiResult.Success -> { teams = teams + r.data; showCreateTeamDialog = false }
                        is ApiResult.Error -> actionError = r.message
                    }
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить клуб?") },
            text = { Text("Соревнования, созданные от лица клуба, останутся, но потеряют владельца. Действие необратимо.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (clubRepo.deleteClub(clubId)) {
                            is ApiResult.Success -> onBack()
                            is ApiResult.Error -> actionError = "Не удалось удалить клуб"
                        }
                        showDeleteConfirm = false
                    }
                }) { Text("Удалить", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { showDeleteConfirm = false }) { Text("Отмена") } },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(club?.name ?: "Клуб", maxLines = 1) },
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

        val c = club
        if (c == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Клуб не найден")
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                c.description?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    "Участников: ${c.membersCount}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                actionError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (myMembership == null) {
                        if (c.allowJoinRequests) {
                            Button(onClick = {
                                if (!isLoggedIn) { showLogin = true; return@Button }
                                scope.launch {
                                    when (clubRepo.createJoinRequest(clubId)) {
                                        is ApiResult.Success -> myPendingRequest = true
                                        is ApiResult.Error -> actionError = "Не удалось подать заявку"
                                    }
                                }
                            }, enabled = !myPendingRequest) {
                                Text(if (myPendingRequest) "Заявка отправлена" else "Подать заявку")
                            }
                        } else {
                            Text("Клуб не принимает заявки", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        OutlinedButton(onClick = {
                            scope.launch {
                                when (clubRepo.removeMember(clubId, currentUserId!!)) {
                                    is ApiResult.Success -> reloadKey++
                                    is ApiResult.Error -> actionError = "Не удалось выйти из клуба (возможно, вы единственный основатель)"
                                }
                            }
                        }) { Text("Покинуть клуб") }
                    }
                    if (isAdmin) {
                        OutlinedButton(onClick = { showEditDialog = true }) { Text("Редактировать") }
                    }
                    if (isFounder) {
                        OutlinedButton(onClick = { showDeleteConfirm = true }) {
                            Text("Удалить клуб", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (isAdmin) {
                        OutlinedButton(onClick = { onJoinRequestsClick(clubId) }) { Text("Заявки") }
                    }
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Участники") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Команды") })
            }

            when (selectedTab) {
                0 -> MembersTab(
                    members = members,
                    isFounder = isFounder,
                    currentUserId = currentUserId,
                    onRemove = { userId ->
                        scope.launch {
                            when (clubRepo.removeMember(clubId, userId)) {
                                is ApiResult.Success -> reloadKey++
                                is ApiResult.Error -> actionError = "Не удалось удалить участника"
                            }
                        }
                    },
                    onChangeRole = { userId, role ->
                        scope.launch {
                            when (clubRepo.changeMemberRole(clubId, userId, ChangeRoleRequest(role))) {
                                is ApiResult.Success -> reloadKey++
                                is ApiResult.Error -> actionError = "Не удалось изменить роль"
                            }
                        }
                    },
                )
                1 -> TeamsTab(
                    teams = teams,
                    isAdmin = isAdmin,
                    onTeamClick = onTeamClick,
                    onAddTeam = { showCreateTeamDialog = true },
                )
            }
        }
    }
}

@Composable
private fun MembersTab(
    members: List<ClubMember>,
    isFounder: Boolean,
    currentUserId: String?,
    onRemove: (String) -> Unit,
    onChangeRole: (String, String) -> Unit,
) {
    if (members.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text("Нет участников", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(members, key = { it.id }) { member ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${member.lastName} ${member.firstName}".trim(), style = MaterialTheme.typography.bodyMedium)
                        Text(clubRoleLabel(member.role), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    if (isFounder && member.userId != currentUserId) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { menuExpanded = true }) { Text("Действия") }
                            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                if (member.role == "MEMBER") {
                                    DropdownMenuItem(text = { Text("Назначить админом") }, onClick = {
                                        onChangeRole(member.userId, "ADMIN"); menuExpanded = false
                                    })
                                }
                                if (member.role == "ADMIN") {
                                    DropdownMenuItem(text = { Text("Снять админа") }, onClick = {
                                        onChangeRole(member.userId, "MEMBER"); menuExpanded = false
                                    })
                                }
                                DropdownMenuItem(text = { Text("Передать роль основателя") }, onClick = {
                                    onChangeRole(member.userId, "FOUNDER"); menuExpanded = false
                                })
                                DropdownMenuItem(text = { Text("Удалить из клуба") }, onClick = {
                                    onRemove(member.userId); menuExpanded = false
                                })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamsTab(
    teams: List<Team>,
    isAdmin: Boolean,
    onTeamClick: (String) -> Unit,
    onAddTeam: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (isAdmin) {
            Row(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                OutlinedButton(onClick = onAddTeam) { Text("+ Добавить команду") }
            }
        }
        if (teams.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("Команд пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(teams, key = { it.id }) { team ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(team.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${sportLabel(team.sportType)} · ${team.membersCount} участников",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { onTeamClick(team.id) }, modifier = Modifier.padding(top = 4.dp)) {
                                Text("Открыть")
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
private fun EditClubDialog(
    club: Club,
    onDismiss: () -> Unit,
    onSaved: (Club) -> Unit,
    onError: (String) -> Unit,
) {
    val repo: ClubRepository = koinInject()
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf(club.name) }
    var description by remember { mutableStateOf(club.description ?: "") }
    var allowJoinRequests by remember { mutableStateOf(club.allowJoinRequests) }
    var saving by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редактирование клуба") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allowJoinRequests, onCheckedChange = { allowJoinRequests = it })
                    Text("Разрешить заявки на вступление")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) { onError("Укажите название"); return@TextButton }
                saving = true
                scope.launch {
                    val request = UpdateClubRequest(name.trim(), description.trim().ifEmpty { null }, allowJoinRequests)
                    when (val r = repo.updateClub(club.id, request)) {
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
private fun CreateTeamDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, sportType: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var sportType by remember { mutableStateOf(SPORT_TYPES.first().first) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая команда") },
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
            TextButton(onClick = { if (name.isNotBlank()) onCreate(name.trim(), sportType) }) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
