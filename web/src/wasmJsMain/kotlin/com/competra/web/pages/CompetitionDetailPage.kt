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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.competra.data.repository.CompetitionRepository
import com.competra.domain.models.CompetitionDetail
import com.competra.domain.models.ParticipantGroupDetail
import com.competra.domain.models.RegisterEventRequest
import com.competra.web.utils.toLocaleDateString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionDetailPage(competitionId: String, onBack: () -> Unit) {
    val repo: CompetitionRepository = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val isLoggedIn = tokenStorage.isLoggedIn()
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<CompetitionDetail?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var registeredGroupId by remember { mutableStateOf<Long?>(null) }
    var registerError by remember { mutableStateOf<String?>(null) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var dialogGroup by remember { mutableStateOf<ParticipantGroupDetail?>(null) }

    LaunchedEffect(competitionId) {
        when (val r = repo.getCompetitionDetail(competitionId)) {
            is ApiResult.Success -> detail = r.data
            is ApiResult.Error -> {}
        }
        loading = false
    }

    if (showRegisterDialog && dialogGroup != null) {
        RegistrationDialog(
            group = dialogGroup!!,
            competitionId = competitionId.toLongOrNull() ?: 0L,
            onDismiss = { showRegisterDialog = false },
            onConfirm = { request ->
                scope.launch {
                    when (val r = repo.register(request)) {
                        is ApiResult.Success -> {
                            registeredGroupId = request.groupId
                            showRegisterDialog = false
                            registerError = null
                        }
                        is ApiResult.Error -> registerError = r.message
                    }
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.title ?: "Соревнование") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Назад") }
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

        val d = detail ?: run {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Соревнование не найдено")
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("О соревновании") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Группы") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Дистанции") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Результаты") })
            }
            when (selectedTab) {
                0 -> InfoTab(detail = d)
                1 -> GroupsTab(
                    detail = d,
                    isLoggedIn = isLoggedIn,
                    registeredGroupId = registeredGroupId,
                    registerError = registerError,
                    onRegister = { group ->
                        dialogGroup = group
                        showRegisterDialog = true
                    },
                    onCancelRegistration = {
                        scope.launch {
                            d.remoteId?.let { id ->
                                when (repo.cancelRegistration(id)) {
                                    is ApiResult.Success -> registeredGroupId = null
                                    is ApiResult.Error -> {}
                                }
                            }
                        }
                    },
                )
                2 -> DistancesTab(remoteId = competitionId.toLongOrNull())
                3 -> ResultsTab(competitionId = competitionId, groups = d.participantGroups)
            }
        }
    }
}

@Composable
private fun InfoTab(detail: CompetitionDetail) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            InfoRow("Дата начала", detail.startDate.toLocaleDateString())
        }
        detail.endDate?.let { item { InfoRow("Дата окончания", it.toLocaleDateString()) } }
        detail.address?.let { item { InfoRow("Место проведения", it) } }
        item {
            InfoRow("Вид спорта", sportLabel(detail.kindOfSport))
        }
        item {
            InfoRow("Статус", statusLabel(detail.status))
        }
        detail.registrationStart?.let { item { InfoRow("Регистрация с", it.toLocaleDateString()) } }
        detail.registrationEnd?.let { item { InfoRow("Регистрация до", it.toLocaleDateString()) } }
        detail.maxParticipants?.let { item { InfoRow("Макс. участников", it.toString()) } }
        detail.feeAmount?.takeIf { it > 0 }?.let { fee ->
            item {
                InfoRow("Стартовый взнос", "${fee.toInt()} ${detail.feeCurrency ?: "руб."}")
            }
        }
        detail.contactEmail?.let { item { InfoRow("Email", it) } }
        detail.contactPhone?.let { item { InfoRow("Телефон", it) } }
        detail.description?.let { desc ->
            item {
                Column {
                    Text("Описание", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(desc, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun GroupsTab(
    detail: CompetitionDetail,
    isLoggedIn: Boolean,
    registeredGroupId: Long?,
    registerError: String?,
    onRegister: (ParticipantGroupDetail) -> Unit,
    onCancelRegistration: () -> Unit,
) {
    val registrationOpen = detail.status == "REGISTRATION_OPEN"

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (registeredGroupId != null) {
            item {
                Text(
                    "Вы зарегистрированы",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
                OutlinedButton(onClick = onCancelRegistration) {
                    Text("Отменить регистрацию")
                }
            }
        }
        registerError?.let { err ->
            item {
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (detail.participantGroups.isEmpty()) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Группы ещё не добавлены", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(detail.participantGroups) { group ->
                GroupCard(
                    group = group,
                    isLoggedIn = isLoggedIn,
                    registrationOpen = registrationOpen,
                    isRegistered = registeredGroupId == group.groupId,
                    anyRegistered = registeredGroupId != null,
                    onRegister = { onRegister(group) },
                )
            }
        }
    }
}

@Composable
private fun GroupCard(
    group: ParticipantGroupDetail,
    isLoggedIn: Boolean,
    registrationOpen: Boolean,
    isRegistered: Boolean,
    anyRegistered: Boolean,
    onRegister: () -> Unit,
) {
    val spotsLeft = group.maxParticipants?.minus(group.registeredCount)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(group.title, style = MaterialTheme.typography.titleSmall)
                if (spotsLeft != null) {
                    Text(
                        "Мест: $spotsLeft/${group.maxParticipants}",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (spotsLeft <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            group.gender?.let { Text(genderLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (group.minAge != null || group.maxAge != null) {
                val ageRange = when {
                    group.minAge != null && group.maxAge != null -> "${group.minAge}–${group.maxAge} лет"
                    group.minAge != null -> "от ${group.minAge} лет"
                    else -> "до ${group.maxAge} лет"
                }
                Text(ageRange, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            group.distanceName?.let {
                val distInfo = buildString {
                    append(it)
                    group.distanceLengthMeters?.let { l -> append(" · $l м") }
                    group.distanceClimbMeters?.let { c -> if (c > 0) append(" · набор $c м") }
                    group.distanceControlsCount?.let { n -> append(" · $n КП") }
                }
                Text(distInfo, style = MaterialTheme.typography.bodySmall)
            }
            if (isLoggedIn && registrationOpen && !anyRegistered) {
                val isFull = spotsLeft != null && spotsLeft <= 0
                Button(
                    onClick = onRegister,
                    enabled = !isFull,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(if (isFull) "Мест нет" else "Зарегистрироваться")
                }
            } else if (isRegistered) {
                Text(
                    "✓ Вы в этой группе",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun RegistrationDialog(
    group: ParticipantGroupDetail,
    competitionId: Long,
    onDismiss: () -> Unit,
    onConfirm: (RegisterEventRequest) -> Unit,
) {
    var firstName by remember { mutableStateOf("") }
    var lastName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Регистрация: ${group.title}") },
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
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (firstName.isBlank() || lastName.isBlank()) {
                    error = "Заполните имя и фамилию"
                } else {
                    onConfirm(
                        RegisterEventRequest(
                            competitionId = competitionId,
                            groupId = group.groupId,
                            firstName = firstName.trim(),
                            lastName = lastName.trim(),
                        )
                    )
                }
            }) {
                Text("Зарегистрироваться")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

private fun sportLabel(kind: String) = when (kind) {
    "Orienteering" -> "Ориентирование"
    "CrossCountrySki" -> "Лыжное ориентирование"
    "TrailRunning" -> "Трейловый бег"
    else -> kind
}

private fun genderLabel(gender: String) = when (gender) {
    "MALE" -> "Мужчины"
    "FEMALE" -> "Женщины"
    "MIXED" -> "Смешанные"
    else -> gender
}
