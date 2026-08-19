package com.competra.web.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.ClubRepository
import com.competra.data.repository.CompetitionRepository
import com.competra.data.repository.UserRepository
import com.competra.domain.models.CompetitionDetail
import com.competra.domain.models.ParticipantGroupDetail
import com.competra.domain.models.RegisterEventRequest
import com.competra.domain.models.UserProfile
import com.competra.web.utils.DEFAULT_TIME_ZONE
import com.competra.web.utils.openExternalLink
import com.competra.web.utils.toLocaleDateString
import com.competra.web.utils.utcMillisToZonedTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionDetailPage(
    competitionId: String,
    onBack: () -> Unit,
    initialTab: Int = 0,
    onTabChange: (Int) -> Unit = {},
    onParticipantClick: (String) -> Unit = {},
    onGroupSplitsClick: (Long, String, Long?) -> Unit = { _, _, _ -> },
    onRaceGraphClick: (Long, String, Long?) -> Unit = { _, _, _ -> },
    onScoreGraphClick: (Long, String, Long?) -> Unit = { _, _, _ -> },
) {
    val repo: CompetitionRepository = koinInject()
    val userRepo: UserRepository = koinInject()
    val clubRepo: ClubRepository = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val isLoggedIn = tokenStorage.isLoggedIn()
    val scope = rememberCoroutineScope()

    var detail by remember { mutableStateOf<CompetitionDetail?>(null) }
    var organizerClubName by remember { mutableStateOf<String?>(null) }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(initialTab) }
    var registeredGroupId by remember { mutableStateOf<Long?>(null) }
    var registerError by remember { mutableStateOf<String?>(null) }
    var showRegisterDialog by remember { mutableStateOf(false) }
    var dialogGroup by remember { mutableStateOf<ParticipantGroupDetail?>(null) }

    LaunchedEffect(competitionId) {
        when (val r = repo.getCompetitionDetail(competitionId)) {
            is ApiResult.Success -> {
                detail = r.data
                r.data.organizingClubId?.let { clubId ->
                    when (val c = clubRepo.getClub(clubId)) {
                        is ApiResult.Success -> organizerClubName = c.data.name
                        is ApiResult.Error -> {}
                    }
                }
            }
            is ApiResult.Error -> {}
        }
        if (isLoggedIn) {
            when (val p = userRepo.getUserProfile()) {
                is ApiResult.Success -> profile = p.data
                is ApiResult.Error -> {}
            }
        }
        loading = false
    }

    if (showRegisterDialog && dialogGroup != null) {
        RegistrationDialog(
            group = dialogGroup!!,
            competitionId = competitionId,
            profile = profile,
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

        val d = detail ?: run {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Соревнование не найдено")
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0; onTabChange(0) }, text = { Text("О соревновании") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1; onTabChange(1) }, text = { Text("Группы") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2; onTabChange(2) }, text = { Text("Дистанции") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3; onTabChange(3) }, text = { Text("Результаты") })
            }
            when (selectedTab) {
                0 -> InfoTab(detail = d, organizerClubName = organizerClubName)
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
                            when (repo.cancelRegistration(competitionId)) {
                                is ApiResult.Success -> registeredGroupId = null
                                is ApiResult.Error -> {}
                            }
                        }
                    },
                )
                2 -> DistancesTab(competitionId = competitionId, isByChoice = d.direction == "BY_CHOICE")
                3 -> ResultsTab(
                    competitionId = competitionId,
                    groups = d.participantGroups,
                    competitionStatus = d.status,
                    resultsStatus = d.resultsStatus,
                    direction = d.direction,
                    onParticipantClick = onParticipantClick,
                    onGroupSplitsClick = onGroupSplitsClick,
                    onRaceGraphClick = onRaceGraphClick,
                    onScoreGraphClick = onScoreGraphClick,
                )
            }
        }
    }
}

@Composable
private fun InfoTab(detail: CompetitionDetail, organizerClubName: String? = null) {
    val registeredTotal = detail.participantGroups.sumOf { it.registeredCount }
    val zone = detail.timeZoneId.takeIf { it.isNotBlank() } ?: DEFAULT_TIME_ZONE
    val mapUrl = detail.mapUrl ?: detail.coordinates?.let { "https://maps.google.com/?q=${it.latitude},${it.longitude}" }
    val organizerLine = buildOrganizerLine(organizerClubName, detail)
    val hasRegistrationInfo = detail.registrationStart != null || detail.registrationEnd != null ||
        detail.maxParticipants != null || (detail.feeAmount?.let { it > 0 } == true)
    val hasOrganizerInfo = organizerLine != null || detail.contactEmail != null || detail.contactPhone != null
    val hasLinks = mapUrl != null || detail.regulationUrl != null || detail.website != null

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(detail.status)
                SportChip(detail.kindOfSport)
            }
        }

        item {
            InfoSectionCard(icon = Icons.Filled.CalendarMonth, title = "Когда и где") {
                Text(
                    detail.startDate.toLocaleDateString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                detail.startTime?.let {
                    Text(
                        "Старт в ${utcMillisToZonedTime(it, zone)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                detail.endDate?.takeIf { it != detail.startDate }?.let {
                    Text(
                        "Окончание: ${it.toLocaleDateString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                detail.address?.let { Field(icon = Icons.Filled.LocationOn, label = "Место проведения", value = it) }
            }
        }

        if (hasRegistrationInfo) {
            item {
                InfoSectionCard(icon = Icons.Filled.Groups, title = "Регистрация") {
                    registrationPeriodText(detail)?.let { Field(label = "Приём заявок", value = it) }
                    detail.maxParticipants?.let { max -> ParticipantsProgress(registered = registeredTotal, max = max) }
                    detail.feeAmount?.takeIf { it > 0 }?.let { fee ->
                        Field(
                            icon = Icons.Filled.Payments,
                            label = "Стартовый взнос",
                            value = "${fee.toInt()} ${detail.feeCurrency ?: "руб."}",
                        )
                    }
                }
            }
        }

        if (hasOrganizerInfo) {
            item {
                InfoSectionCard(icon = Icons.Filled.Person, title = "Организатор") {
                    organizerLine?.let { Field(label = "Организатор", value = it) }
                    detail.contactEmail?.let { LinkField(icon = Icons.Filled.Email, label = "Email", value = it, url = "mailto:$it") }
                    detail.contactPhone?.let { LinkField(icon = Icons.Filled.Phone, label = "Телефон", value = it, url = "tel:$it") }
                }
            }
        }

        if (hasLinks) {
            item {
                InfoSectionCard(icon = Icons.Filled.Link, title = "Документы и ссылки") {
                    mapUrl?.let { LinkField(icon = Icons.Filled.LocationOn, label = "Как добраться", value = "Открыть карту", url = it) }
                    detail.regulationUrl?.let { LinkField(icon = Icons.Filled.Description, label = "Регламент соревнования", value = "Открыть", url = it) }
                    detail.website?.let { LinkField(icon = Icons.Filled.Link, label = "Сайт организатора", value = it, url = it) }
                }
            }
        }

        detail.description?.takeIf { it.isNotBlank() }?.let { desc ->
            item {
                InfoSectionCard(icon = Icons.Filled.Description, title = "Описание") {
                    Text(desc, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

private fun registrationPeriodText(detail: CompetitionDetail): String? {
    val start = detail.registrationStart?.toLocaleDateString()
    val end = detail.registrationEnd?.toLocaleDateString()
    return when {
        start != null && end != null -> "$start – $end"
        start != null -> "с $start"
        end != null -> "до $end"
        else -> null
    }
}

@Composable
private fun StatusChip(status: String) {
    val color = statusColor(status)
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
            Text(statusLabel(status), style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun SportChip(kind: String) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Text(
            sportLabel(kind),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun InfoSectionCard(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
private fun Field(label: String, value: String, icon: ImageVector? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun LinkField(label: String, value: String, url: String, icon: ImageVector? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { openExternalLink(url) },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon?.let { Icon(it, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp)) }
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun ParticipantsProgress(registered: Int, max: Int) {
    val fraction = if (max > 0) (registered.toFloat() / max).coerceIn(0f, 1f) else 0f
    val isFull = registered >= max
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Участников", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "$registered из $max",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = if (isFull) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

/**
 * Строка организатора: название клуба и/или ФИО контактного лица.
 * Формат: "Клуб · Фамилия Имя Отчество" — части, которых нет, опускаются.
 */
private fun buildOrganizerLine(organizerClubName: String?, detail: CompetitionDetail): String? {
    val personalName = listOfNotNull(detail.organizerLastName, detail.organizerFirstName, detail.organizerMiddleName)
        .filter { it.isNotBlank() }
        .joinToString(" ")

    val parts = listOfNotNull(organizerClubName, personalName.takeIf { it.isNotBlank() })
    return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
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

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
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
            group.distanceDescription?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    competitionId: String,
    profile: UserProfile?,
    onDismiss: () -> Unit,
    onConfirm: (RegisterEventRequest) -> Unit,
) {
    // Данные из профиля подставляются автоматически. Поля показываем только как
    // запасной вариант, если в профиле не заполнены имя или фамилия.
    val hasProfileName = !profile?.firstName.isNullOrBlank() && !profile?.lastName.isNullOrBlank()
    var firstName by remember { mutableStateOf(profile?.firstName ?: "") }
    var lastName by remember { mutableStateOf(profile?.lastName ?: "") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Регистрация: ${group.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hasProfileName) {
                    Text("Регистрация от имени:", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("${profile!!.lastName} ${profile.firstName}", style = MaterialTheme.typography.bodyLarge)
                } else {
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
                }
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

internal fun sportLabel(kind: String) = when (kind) {
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
