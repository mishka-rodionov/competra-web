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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.CompetitionRepository
import com.competra.data.repository.DistanceRepository
import com.competra.data.repository.GroupRepository
import com.competra.data.repository.ResultRepository
import com.competra.domain.models.CompetitionFields
import com.competra.domain.models.Coordinates
import com.competra.domain.models.CreateCompetitionRequest
import com.competra.domain.models.CreateGroupRequest
import com.competra.domain.models.Distance
import com.competra.domain.models.OrienteeringCompetition
import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.OrienteeringResult
import com.competra.domain.models.ParticipantGroupDetail
import com.competra.web.components.CoordinatesPickerField
import com.competra.web.components.DateField
import com.competra.web.components.ImportResultsPreviewDialog
import com.competra.web.components.LabeledDropdown
import com.competra.web.components.TimeField
import com.competra.web.components.TimeZoneField
import com.competra.web.utils.DEFAULT_TIME_ZONE
import com.competra.web.utils.ImportResultsDiff
import com.competra.web.utils.buildResultsDiff
import com.competra.web.utils.parseResultsHtml
import com.competra.web.utils.pickHtmlFile
import com.competra.web.utils.utcMillisToZonedDate
import com.competra.web.utils.utcMillisToZonedTime
import com.competra.web.utils.zonedDateTimeToUtcMillis
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val STATUS_OPTIONS = listOf(
    "REGISTRATION_OPEN" to "Регистрация открыта",
    "REGISTRATION_CLOSED" to "Регистрация закрыта",
    "CREATED" to "Черновик",
    "FINISHED" to "Завершено",
)

private val GENDER_OPTIONS = listOf(
    null to "Без ограничений",
    "M" to "Мужчины",
    "F" to "Женщины",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageCompetitionPage(
    competition: OrienteeringCompetition,
    onBack: () -> Unit,
) {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(competition.competition.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Общее") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Группы") })
                Tab(selected = selectedTab == 2, onClick = { selectedTab = 2 }, text = { Text("Участники") })
                Tab(selected = selectedTab == 3, onClick = { selectedTab = 3 }, text = { Text("Дистанции") })
                Tab(selected = selectedTab == 4, onClick = { selectedTab = 4 }, text = { Text("Результаты") })
            }
            when (selectedTab) {
                0 -> EditTab(competition)
                1 -> GroupsTab(competition)
                2 -> ParticipantsManageTab(competition)
                3 -> DistancesTab(
                    competitionId = competition.competitionId,
                    showImport = true,
                    isByChoice = competition.direction == "BY_CHOICE",
                )
                4 -> ResultsManageTab(competition = competition)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditTab(competition: OrienteeringCompetition) {
    val repo: CompetitionRepository = koinInject()
    val scope = rememberCoroutineScope()
    val c = competition.competition
    val zoneId = remember(c.timeZoneId) { c.timeZoneId.ifBlank { DEFAULT_TIME_ZONE } }

    var title by remember { mutableStateOf(c.title) }
    var startMillis by remember { mutableStateOf<Long?>(c.startDate.takeIf { it > 0 }) }
    var startTime by remember { mutableStateOf(c.startDate.takeIf { it > 0 }?.let { utcMillisToZonedTime(it, zoneId) } ?: "10:00") }
    var selectedZone by remember { mutableStateOf(zoneId) }
    var endMillis by remember { mutableStateOf(c.endDate) }
    var address by remember { mutableStateOf(c.address ?: "") }
    var latitude by remember { mutableStateOf(c.coordinates?.latitude) }
    var longitude by remember { mutableStateOf(c.coordinates?.longitude) }
    var description by remember { mutableStateOf(c.description ?: "") }
    var direction by remember { mutableStateOf(competition.direction.ifBlank { "FORWARD" }) }
    var punchingSystem by remember { mutableStateOf(competition.punchingSystem.ifBlank { "SPORTIDENT" }) }
    var startTimeMode by remember { mutableStateOf(competition.startTimeMode.ifBlank { "USER_SET" }) }
    var startInterval by remember { mutableIntStateOf(competition.startIntervalSeconds ?: 60) }
    var registrationStartMillis by remember { mutableStateOf(c.registrationStart) }
    var registrationStartTime by remember { mutableStateOf(c.registrationStart?.let { utcMillisToZonedTime(it, zoneId) } ?: "10:00") }
    var registrationEndMillis by remember { mutableStateOf(c.registrationEnd) }
    var registrationEndTime by remember { mutableStateOf(c.registrationEnd?.let { utcMillisToZonedTime(it, zoneId) } ?: "23:59") }
    var maxParticipants by remember { mutableStateOf(c.maxParticipants?.toString() ?: "") }
    var feeAmount by remember { mutableStateOf(c.feeAmount?.let { if (it > 0) it.toInt().toString() else "" } ?: "") }
    var contactEmail by remember { mutableStateOf(c.contactEmail ?: "") }
    var contactPhone by remember { mutableStateOf(c.contactPhone ?: "") }
    var website by remember { mutableStateOf(c.website ?: "") }
    var regulationUrl by remember { mutableStateOf(c.regulationUrl ?: "") }
    var mapUrl by remember { mutableStateOf(c.mapUrl ?: "") }
    var selectedStatus by remember { mutableStateOf(c.status) }
    var statusExpanded by remember { mutableStateOf(false) }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf(false) }

    if (saving) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Основная информация", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp)) }

        item {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(
                    label = "Дата начала *",
                    displayValue = startMillis?.let { utcMillisToZonedDate(it, "UTC") } ?: "",
                    initialUtcMillis = startMillis,
                    modifier = Modifier.weight(1f),
                    onPick = { startMillis = it },
                )
                TimeField(label = "Время старта", value = startTime, modifier = Modifier.weight(1f), onChange = { startTime = it })
            }
        }

        item { TimeZoneField(zoneId = selectedZone, modifier = Modifier.fillMaxWidth(), onSelect = { selectedZone = it }) }

        item {
            DateField(
                label = "Дата окончания",
                displayValue = endMillis?.let { utcMillisToZonedDate(it, "UTC") } ?: "",
                initialUtcMillis = endMillis,
                modifier = Modifier.fillMaxWidth(),
                onPick = { endMillis = it },
            )
        }

        item {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text("Место проведения") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        item { Text("Координаты старта на карте", style = MaterialTheme.typography.labelLarge) }
        item {
            CoordinatesPickerField(
                latitude = latitude,
                longitude = longitude,
                onPick = { lat, lon -> latitude = lat; longitude = lon },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )
        }

        item {
            ExposedDropdownMenuBox(
                expanded = statusExpanded,
                onExpandedChange = { statusExpanded = it },
            ) {
                OutlinedTextField(
                    value = STATUS_OPTIONS.firstOrNull { it.first == selectedStatus }?.second ?: selectedStatus,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Статус") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(statusExpanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                )
                ExposedDropdownMenu(
                    expanded = statusExpanded,
                    onDismissRequest = { statusExpanded = false },
                ) {
                    STATUS_OPTIONS.forEach { (key, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { selectedStatus = key; statusExpanded = false },
                        )
                    }
                }
            }
        }

        item { Text("Параметры ориентирования", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
        item { LabeledDropdown("Направление", direction, DIRECTION_OPTIONS, Modifier.fillMaxWidth()) { direction = it } }
        item { LabeledDropdown("Система отметки", punchingSystem, PUNCHING_SYSTEM_OPTIONS, Modifier.fillMaxWidth()) { punchingSystem = it } }
        item { LabeledDropdown("Режим старта", startTimeMode, START_TIME_MODE_OPTIONS, Modifier.fillMaxWidth()) { startTimeMode = it } }
        item {
            LabeledDropdown(
                "Интервал старта", startInterval,
                START_INTERVAL_OPTIONS.map { it to formatIntervalSeconds(it) },
                Modifier.fillMaxWidth(),
            ) { startInterval = it }
        }

        item { Text("Регистрация", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(
                    label = "Начало регистрации",
                    displayValue = registrationStartMillis?.let { utcMillisToZonedDate(it, "UTC") } ?: "",
                    initialUtcMillis = registrationStartMillis,
                    modifier = Modifier.weight(1f),
                    onPick = { registrationStartMillis = it },
                )
                TimeField(label = "Время", value = registrationStartTime, modifier = Modifier.weight(1f), onChange = { registrationStartTime = it })
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(
                    label = "Конец регистрации",
                    displayValue = registrationEndMillis?.let { utcMillisToZonedDate(it, "UTC") } ?: "",
                    initialUtcMillis = registrationEndMillis,
                    modifier = Modifier.weight(1f),
                    onPick = { registrationEndMillis = it },
                )
                TimeField(label = "Время", value = registrationEndTime, modifier = Modifier.weight(1f), onChange = { registrationEndTime = it })
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = maxParticipants,
                    onValueChange = { maxParticipants = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Макс. участников") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = feeAmount,
                    onValueChange = { feeAmount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("Взнос (руб.)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
        }

        item { Text("Контакты", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }

        item {
            OutlinedTextField(
                value = contactEmail,
                onValueChange = { contactEmail = it },
                label = { Text("Email организатора") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            )
        }
        item {
            OutlinedTextField(
                value = contactPhone,
                onValueChange = { contactPhone = it },
                label = { Text("Телефон") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )
        }
        item {
            OutlinedTextField(
                value = website,
                onValueChange = { website = it },
                label = { Text("Сайт соревнования") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = regulationUrl,
                onValueChange = { regulationUrl = it },
                label = { Text("Ссылка на положение") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
        item {
            OutlinedTextField(
                value = mapUrl,
                onValueChange = { mapUrl = it },
                label = { Text("Ссылка на карту") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }

        error?.let { err ->
            item {
                Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }

        if (success) {
            item {
                Text(
                    "Сохранено",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        item {
            Button(
                onClick = {
                    val startDay = startMillis
                    if (title.isBlank()) { error = "Укажите название"; return@Button }
                    if (startDay == null) { error = "Укажите дату начала"; return@Button }

                    scope.launch {
                        saving = true
                        error = null
                        success = false
                        val request = CreateCompetitionRequest(
                            competitionId = competition.competitionId,
                            competition = CompetitionFields(
                                title = title.trim(),
                                startDate = zonedDateTimeToUtcMillis(startDay, startTime, selectedZone),
                                endDate = endMillis,
                                kindOfSport = c.kindOfSport.ifBlank { "Orienteering" },
                                description = description.trimOrNull(),
                                address = address.trimOrNull(),
                                coordinates = if (latitude != null && longitude != null) Coordinates(latitude!!, longitude!!) else null,
                                status = selectedStatus,
                                registrationStart = registrationStartMillis?.let { zonedDateTimeToUtcMillis(it, registrationStartTime, selectedZone) },
                                registrationEnd = registrationEndMillis?.let { zonedDateTimeToUtcMillis(it, registrationEndTime, selectedZone) },
                                maxParticipants = maxParticipants.toIntOrNull(),
                                feeAmount = feeAmount.toDoubleOrNull(),
                                feeCurrency = if (feeAmount.isNotBlank()) "RUB" else c.feeCurrency,
                                mainOrganizerId = c.mainOrganizerId,
                                contactEmail = contactEmail.trimOrNull(),
                                contactPhone = contactPhone.trimOrNull(),
                                website = website.trimOrNull(),
                                regulationUrl = regulationUrl.trimOrNull(),
                                mapUrl = mapUrl.trimOrNull(),
                                imageUrl = c.imageUrl,
                                resultsStatus = c.resultsStatus,
                                timeZoneId = selectedZone,
                            ),
                            direction = direction,
                            punchingSystem = punchingSystem,
                            startTimeMode = startTimeMode,
                            startIntervalSeconds = startInterval,
                        )
                        when (val r = repo.createCompetition(request)) {
                            is ApiResult.Success -> success = true
                            is ApiResult.Error -> error = r.message
                        }
                        saving = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
            ) {
                Text("Сохранить")
            }
        }
    }
}

@Composable
private fun GroupsTab(competition: OrienteeringCompetition) {
    val groupRepo: GroupRepository = koinInject()
    val distanceRepo: DistanceRepository = koinInject()
    val scope = rememberCoroutineScope()
    val competitionId = competition.competitionId

    var groups by remember { mutableStateOf<List<ParticipantGroupDetail>>(emptyList()) }
    var distances by remember { mutableStateOf<List<Distance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(competitionId) {
        when (val r = groupRepo.getGroups(competitionId)) {
            is ApiResult.Success -> groups = r.data
            is ApiResult.Error -> error = r.message
        }
        when (val r = distanceRepo.getByCompetition(competitionId)) {
            is ApiResult.Success -> distances = r.data
            is ApiResult.Error -> {}
        }
        loading = false
    }

    if (showAddDialog) {
        AddGroupDialog(
            competitionRemoteId = competitionId,
            distances = distances,
            isByChoice = competition.direction == "BY_CHOICE",
            onDismiss = { showAddDialog = false },
            onSaved = { updated ->
                groups = updated
                showAddDialog = false
            },
            onError = { error = it },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Группы участников", style = MaterialTheme.typography.titleSmall)
            OutlinedButton(onClick = { showAddDialog = true }) {
                Text("+ Добавить")
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
        }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        } else if (groups.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нет групп. Добавьте первую группу.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.groupId }) { group ->
                    GroupCard(
                        group = group,
                        onDelete = {
                            scope.launch {
                                when (groupRepo.deleteGroup(group.groupId)) {
                                    is ApiResult.Success -> groups = groups.filter { it.groupId != group.groupId }
                                    is ApiResult.Error -> error = "Не удалось удалить группу"
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun GroupCard(group: ParticipantGroupDetail, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(group.title, style = MaterialTheme.typography.titleSmall)
                val details = buildList {
                    group.gender?.let { add(if (it == "M") "Мужчины" else "Женщины") }
                    if (group.minAge != null || group.maxAge != null) {
                        add("${group.minAge ?: ""}–${group.maxAge ?: ""} лет")
                    }
                    group.distanceName?.let { add("Дистанция: $it") }
                    group.maxParticipants?.let { add("Мест: ${group.registeredCount}/$it") }
                    group.timeLimitMinutes?.let { add("Лимит: $it мин") }
                    group.scorePenaltyPerMinute?.let { add("Штраф: $it очк/мин") }
                }
                if (details.isNotEmpty()) {
                    Text(
                        details.joinToString("  •  "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            TextButton(onClick = onDelete) {
                Text("Удалить", color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddGroupDialog(
    competitionRemoteId: String,
    distances: List<Distance>,
    isByChoice: Boolean,
    onDismiss: () -> Unit,
    onSaved: (List<ParticipantGroupDetail>) -> Unit,
    onError: (String) -> Unit,
) {
    val repo: GroupRepository = koinInject()
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var selectedGender by remember { mutableStateOf<String?>(null) }
    var genderExpanded by remember { mutableStateOf(false) }
    var minAge by remember { mutableStateOf("") }
    var maxAge by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") }
    var selectedDistance by remember { mutableStateOf<Distance?>(null) }
    var distanceExpanded by remember { mutableStateOf(false) }
    var timeLimitMinutes by remember { mutableStateOf("60") }
    var scorePenaltyPerMinute by remember { mutableStateOf("1") }
    var maxLatenessMinutes by remember { mutableStateOf("30") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Добавить группу") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )

                ExposedDropdownMenuBox(
                    expanded = genderExpanded,
                    onExpandedChange = { genderExpanded = it },
                ) {
                    OutlinedTextField(
                        value = GENDER_OPTIONS.firstOrNull { it.first == selectedGender }?.second ?: "Без ограничений",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Пол") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(genderExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(),
                    )
                    ExposedDropdownMenu(
                        expanded = genderExpanded,
                        onDismissRequest = { genderExpanded = false },
                    ) {
                        GENDER_OPTIONS.forEach { (key, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = { selectedGender = key; genderExpanded = false },
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minAge,
                        onValueChange = { minAge = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Мин. возраст") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = maxAge,
                        onValueChange = { maxAge = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Макс. возраст") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                OutlinedTextField(
                    value = maxParticipants,
                    onValueChange = { maxParticipants = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Макс. участников") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                if (isByChoice) {
                    Text("Параметры «по выбору»", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = timeLimitMinutes,
                            onValueChange = { timeLimitMinutes = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Лимит времени, мин") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            value = scorePenaltyPerMinute,
                            onValueChange = { scorePenaltyPerMinute = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Штраф, очк/мин") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    OutlinedTextField(
                        value = maxLatenessMinutes,
                        onValueChange = { maxLatenessMinutes = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Порог обнуления, мин") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                if (distances.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = distanceExpanded,
                        onExpandedChange = { distanceExpanded = it },
                    ) {
                        OutlinedTextField(
                            value = selectedDistance?.name ?: "Не выбрана",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Дистанция") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(distanceExpanded) },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                        )
                        ExposedDropdownMenu(
                            expanded = distanceExpanded,
                            onDismissRequest = { distanceExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Не выбрана") },
                                onClick = { selectedDistance = null; distanceExpanded = false },
                            )
                            distances.forEach { d ->
                                DropdownMenuItem(
                                    text = { Text(d.name ?: "Без названия") },
                                    onClick = { selectedDistance = d; distanceExpanded = false },
                                )
                            }
                        }
                    }
                }

                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) { error = "Укажите название"; return@Button }
                    scope.launch {
                        saving = true
                        val request = CreateGroupRequest(
                            groupId = null,
                            competitionId = competitionRemoteId,
                            title = title.trim(),
                            gender = selectedGender,
                            minAge = minAge.toIntOrNull(),
                            maxAge = maxAge.toIntOrNull(),
                            distanceId = selectedDistance?.id,
                            maxParticipants = maxParticipants.toIntOrNull(),
                            timeLimitMinutes = if (isByChoice) timeLimitMinutes.toIntOrNull() else null,
                            scorePenaltyPerMinute = if (isByChoice) scorePenaltyPerMinute.toIntOrNull() else null,
                            maxLatenessMinutes = if (isByChoice) maxLatenessMinutes.toIntOrNull() else null,
                        )
                        when (val r = repo.saveGroup(request)) {
                            is ApiResult.Success -> onSaved(r.data)
                            is ApiResult.Error -> { error = r.message; saving = false }
                        }
                    }
                },
                enabled = !saving,
            ) {
                if (saving) CircularProgressIndicator() else Text("Сохранить")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

@Composable
private fun ResultsManageTab(competition: OrienteeringCompetition) {
    val competitionId = competition.competitionId
    val competitionTitle = competition.competition.title
    val repo: ResultRepository = koinInject()
    val competitionRepo: CompetitionRepository = koinInject()
    val scope = rememberCoroutineScope()

    var results by remember { mutableStateOf<List<OrienteeringResult>>(emptyList()) }
    var participants by remember { mutableStateOf<List<OrienteeringParticipant>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var previewDiff by remember { mutableStateOf<ImportResultsDiff?>(null) }
    var resultsStatus by remember { mutableStateOf(competition.competition.resultsStatus) }
    var showPublishConfirm by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf(false) }

    LaunchedEffect(competitionId) {
        when (val r = repo.getResults(competitionId)) {
            is ApiResult.Success -> results = r.data
            is ApiResult.Error -> error = r.message
        }
        when (val r = repo.getParticipants(competitionId)) {
            is ApiResult.Success -> participants = r.data
            is ApiResult.Error -> {}
        }
        loading = false
    }

    previewDiff?.let { diff ->
        ImportResultsPreviewDialog(
            competitionTitle = competitionTitle,
            changed = diff.changed,
            unmatched = diff.unmatched,
            onDismiss = { previewDiff = null },
            onConfirm = { selectedRows ->
                scope.launch {
                    importing = true
                    error = null
                    when (val r = repo.saveResults(selectedRows.map { it.request })) {
                        is ApiResult.Success -> {
                            val savedIds = r.data.map { it.participantId }.toSet()
                            results = results.filter { it.participantId !in savedIds } + r.data
                        }
                        is ApiResult.Error -> error = r.message
                    }
                    importing = false
                    previewDiff = null
                }
            },
        )
    }

    if (showPublishConfirm) {
        AlertDialog(
            onDismissRequest = { if (!publishing) showPublishConfirm = false },
            title = { Text("Опубликовать результаты?") },
            text = { Text("Результаты станут официальными и видимыми участникам. Это действие необратимо.") },
            confirmButton = {
                Button(
                    enabled = !publishing,
                    onClick = {
                        scope.launch {
                            publishing = true
                            val request = CreateCompetitionRequest(
                                competitionId = competition.competitionId,
                                competition = competition.competition.toFields(resultsStatus = "OFFICIAL"),
                                direction = competition.direction,
                                punchingSystem = competition.punchingSystem,
                                startTimeMode = competition.startTimeMode,
                                startIntervalSeconds = competition.startIntervalSeconds,
                            )
                            when (val r = competitionRepo.createCompetition(request)) {
                                is ApiResult.Success -> resultsStatus = "OFFICIAL"
                                is ApiResult.Error -> error = r.message
                            }
                            publishing = false
                            showPublishConfirm = false
                        }
                    },
                ) {
                    if (publishing) CircularProgressIndicator() else Text("Опубликовать")
                }
            },
            dismissButton = {
                TextButton(enabled = !publishing, onClick = { showPublishConfirm = false }) { Text("Отмена") }
            },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Результаты", style = MaterialTheme.typography.titleSmall)
            Button(
                enabled = !loading && !importing,
                onClick = {
                    pickHtmlFile { _, content ->
                        error = null
                        val parsedRows = runCatching { parseResultsHtml(content) }.getOrNull()
                        if (parsedRows.isNullOrEmpty()) {
                            error = "Не удалось распознать файл — проверьте, что это HTML-протокол результатов."
                            return@pickHtmlFile
                        }
                        previewDiff = buildResultsDiff(parsedRows, participants, results, competitionId)
                    }
                },
            ) {
                Text("Импорт из HTML")
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (resultsStatus == "OFFICIAL") "Статус: результаты официальные" else "Статус: результаты не опубликованы",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (resultsStatus != "OFFICIAL") {
                OutlinedButton(
                    enabled = !loading && results.isNotEmpty(),
                    onClick = { showPublishConfirm = true },
                ) {
                    Text("Опубликовать результаты")
                }
            }
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

        if (loading || importing) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 16.dp))
        } else if (results.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Результаты ещё не опубликованы. Их можно восстановить из ранее экспортированного HTML.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val finished = results.count { it.status == "FINISHED" }
            Text(
                "Всего результатов: ${results.size} (финишировали: $finished)",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

private fun String.trimOrNull(): String? = trim().takeIf { it.isNotEmpty() }

private fun com.competra.domain.models.Competition.toFields(resultsStatus: String = this.resultsStatus): CompetitionFields = CompetitionFields(
    title = title,
    startDate = startDate,
    endDate = endDate,
    kindOfSport = kindOfSport,
    description = description,
    address = address,
    coordinates = coordinates,
    status = status,
    registrationStart = registrationStart,
    registrationEnd = registrationEnd,
    maxParticipants = maxParticipants,
    feeAmount = feeAmount,
    feeCurrency = feeCurrency,
    mainOrganizerId = mainOrganizerId,
    contactPhone = contactPhone,
    contactEmail = contactEmail,
    website = website,
    regulationUrl = regulationUrl,
    mapUrl = mapUrl,
    imageUrl = imageUrl,
    resultsStatus = resultsStatus,
    timeZoneId = timeZoneId.ifBlank { DEFAULT_TIME_ZONE },
    isTest = isTest,
)
