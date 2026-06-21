package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.CompetitionRepository
import com.competra.data.repository.DistanceRepository
import com.competra.data.repository.GroupRepository
import com.competra.data.repository.UserRepository
import com.competra.domain.models.CompetitionFields
import com.competra.domain.models.ControlPoint
import com.competra.domain.models.CreateCompetitionRequest
import com.competra.domain.models.CreateGroupRequest
import com.competra.domain.models.Distance
import com.competra.domain.models.OrienteeringCompetition
import com.competra.domain.models.SaveDistanceRequest
import com.competra.web.components.DateField
import com.competra.web.components.LabeledDropdown
import com.competra.web.components.TimeField
import com.competra.web.components.TimeZoneField
import com.competra.web.utils.DEFAULT_TIME_ZONE
import com.competra.web.utils.generateUUID
import com.competra.web.utils.pickXmlFile
import com.competra.web.utils.utcMillisToZonedDate
import com.competra.web.utils.zonedDateTimeToUtcMillis
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val DAY_MS = 24L * 60 * 60 * 1000

/** Локальная дистанция, накопленная в мастере до публикации. */
private data class PendingDistance(
    val name: String?,
    val lengthMeters: Int,
    val climbMeters: Int,
    val controlPoints: List<ControlPoint>,
    val finishControlPoint: Int?,
    val description: String?,
)

/** Локальная группа, ссылается на дистанцию по индексу в списке шага «Дистанции». */
private data class PendingGroup(
    val title: String,
    val minAge: Int?,
    val maxAge: Int?,
    val maxParticipants: Int?,
    val distanceIndex: Int,
)

private val REG_END_MODE_OPTIONS = listOf(
    "AT_COMPETITION_START" to "В момент старта",
    "DAY_BEFORE_START" to "За день до старта",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCompetitionPage(
    onBack: () -> Unit,
    onCreated: (OrienteeringCompetition) -> Unit,
) {
    val repo: CompetitionRepository = koinInject()
    val distanceRepo: DistanceRepository = koinInject()
    val groupRepo: GroupRepository = koinInject()
    val userRepo: UserRepository = koinInject()
    val scope = rememberCoroutineScope()

    var currentUserId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (currentUserId == null) {
            when (val r = userRepo.getUserProfile()) {
                is ApiResult.Success -> currentUserId = r.data.id
                is ApiResult.Error -> {}
            }
        }
    }

    var step by remember { mutableIntStateOf(0) }

    // --- Шаг 1: Основное ---
    var title by remember { mutableStateOf("") }
    var startDateMillis by remember { mutableStateOf<Long?>(null) }
    var startTime by remember { mutableStateOf("10:00") }
    var zoneId by remember { mutableStateOf(DEFAULT_TIME_ZONE) }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var direction by remember { mutableStateOf("FORWARD") }
    var punchingSystem by remember { mutableStateOf("SPORTIDENT") }
    var startTimeMode by remember { mutableStateOf("USER_SET") }
    var startInterval by remember { mutableIntStateOf(60) }

    // --- Шаг 2: Регистрация ---
    var registrationOpenImmediately by remember { mutableStateOf(true) }
    var regStartDateMillis by remember { mutableStateOf<Long?>(null) }
    var regStartTime by remember { mutableStateOf("10:00") }
    var registrationEndMode by remember { mutableStateOf("AT_COMPETITION_START") }
    var maxParticipants by remember { mutableStateOf("") }
    var feeAmount by remember { mutableStateOf("") }

    // --- Шаг 3: Организатор ---
    var contactPhone by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var website by remember { mutableStateOf("") }
    var regulationUrl by remember { mutableStateOf("") }
    var mapUrl by remember { mutableStateOf("") }

    // --- Шаг 4/5: Дистанции и группы ---
    var distances by remember { mutableStateOf<List<PendingDistance>>(emptyList()) }
    var groups by remember { mutableStateOf<List<PendingGroup>>(emptyList()) }
    var showDistanceDialog by remember { mutableStateOf(false) }
    var showGroupDialog by remember { mutableStateOf(false) }
    var importXmlBytes by remember { mutableStateOf<ByteArray?>(null) }
    var importXmlName by remember { mutableStateOf<String?>(null) }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val stepTitles = listOf("Основное", "Регистрация", "Организатор", "Дистанции", "Группы")

    fun goBack() {
        error = null
        if (step > 0) step-- else onBack()
    }

    val nextEnabled = when (step) {
        0 -> title.isNotBlank() && startDateMillis != null && zoneId.isNotBlank()
        3 -> distances.isNotEmpty() || importXmlBytes != null
        4 -> groups.isNotEmpty()
        else -> true
    }

    fun publish() {
        val startDay = startDateMillis ?: return
        scope.launch {
            saving = true
            error = null
            val startMs = zonedDateTimeToUtcMillis(startDay, startTime, zoneId)
            val regStart = if (registrationOpenImmediately) null
            else regStartDateMillis?.let { zonedDateTimeToUtcMillis(it, regStartTime, zoneId) }
            val regEnd = if (registrationEndMode == "DAY_BEFORE_START") startMs - DAY_MS else startMs

            val request = CreateCompetitionRequest(
                competitionId = generateUUID(),
                competition = CompetitionFields(
                    title = title.trim(),
                    startDate = startMs,
                    endDate = null,
                    kindOfSport = "Orienteering",
                    description = description.trimOrNull(),
                    address = address.trimOrNull(),
                    status = if (regStart == null) "REGISTRATION_OPEN" else "CREATED",
                    registrationStart = regStart,
                    registrationEnd = regEnd,
                    maxParticipants = maxParticipants.toIntOrNull(),
                    feeAmount = feeAmount.toDoubleOrNull(),
                    feeCurrency = if (feeAmount.isNotBlank()) "RUB" else null,
                    mainOrganizerId = currentUserId,
                    contactEmail = contactEmail.trimOrNull(),
                    contactPhone = contactPhone.trimOrNull(),
                    website = website.trimOrNull(),
                    regulationUrl = regulationUrl.trimOrNull(),
                    mapUrl = mapUrl.trimOrNull(),
                    timeZoneId = zoneId,
                ),
                direction = direction,
                punchingSystem = punchingSystem,
                startTimeMode = startTimeMode,
                startIntervalSeconds = startInterval,
            )

            val created = when (val r = repo.createCompetition(request)) {
                is ApiResult.Success -> r.data
                is ApiResult.Error -> { error = r.message; saving = false; return@launch }
            }
            val competitionId = created.competitionId
            if (competitionId.isEmpty()) {
                error = "Сервер не вернул идентификатор соревнования"
                saving = false
                return@launch
            }

            // Дистанции: ответ приходит в порядке запроса -> сопоставляем по индексу.
            var savedDistances: List<Distance> = emptyList()
            if (distances.isNotEmpty()) {
                val distRequests = distances.map { d ->
                    SaveDistanceRequest(
                        distanceId = null,
                        competitionId = competitionId,
                        name = d.name,
                        lengthMeters = d.lengthMeters,
                        climbMeters = d.climbMeters,
                        controlsCount = d.controlPoints.size,
                        description = d.description,
                        controlPoints = d.controlPoints,
                        finishControlPoint = d.finishControlPoint,
                    )
                }
                when (val dr = distanceRepo.saveDistances(distRequests)) {
                    is ApiResult.Success -> savedDistances = dr.data
                    is ApiResult.Error -> { error = "Дистанции: ${dr.message}"; saving = false; return@launch }
                }
            }

            // Импорт дистанций из IOF XML файла (сервер парсит файл сам).
            importXmlBytes?.let { xmlBytes ->
                when (val ir = distanceRepo.importFromXml(competitionId, xmlBytes)) {
                    is ApiResult.Success -> {}
                    is ApiResult.Error -> { error = "Импорт XML: ${ir.message}"; saving = false; return@launch }
                }
            }

            if (groups.isNotEmpty()) {
                val groupRequests = groups.map { g ->
                    CreateGroupRequest(
                        groupId = 0,
                        competitionId = competitionId,
                        title = g.title,
                        minAge = g.minAge,
                        maxAge = g.maxAge,
                        distanceId = savedDistances.getOrNull(g.distanceIndex)?.id,
                        maxParticipants = g.maxParticipants,
                    )
                }
                when (val gr = groupRepo.saveGroups(groupRequests)) {
                    is ApiResult.Success -> {}
                    is ApiResult.Error -> { error = "Группы: ${gr.message}"; saving = false; return@launch }
                }
            }

            onCreated(created)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Создать · ${stepTitles[step]} (${step + 1}/5)") },
                navigationIcon = { TextButton(onClick = { goBack() }) { Text("← Назад") } },
            )
        },
        bottomBar = {
            if (!saving) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(onClick = { goBack() }) { Text("Назад") }
                    Button(
                        enabled = nextEnabled,
                        onClick = { if (step < 4) { error = null; step++ } else publish() },
                    ) {
                        Text(if (step < 4) "Далее" else "Завершить")
                    }
                }
            }
        },
    ) { padding ->
        if (saving) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (step) {
                0 -> basicStep(
                    title = title, onTitle = { title = it },
                    startDateMillis = startDateMillis, onStartDate = { startDateMillis = it },
                    startTime = startTime, onStartTime = { startTime = it },
                    zoneId = zoneId, onZone = { zoneId = it },
                    address = address, onAddress = { address = it },
                    description = description, onDescription = { description = it },
                    direction = direction, onDirection = { direction = it },
                    punchingSystem = punchingSystem, onPunching = { punchingSystem = it },
                    startTimeMode = startTimeMode, onStartMode = { startTimeMode = it },
                    startInterval = startInterval, onInterval = { startInterval = it },
                )
                1 -> registrationStep(
                    openImmediately = registrationOpenImmediately, onOpenImmediately = { registrationOpenImmediately = it },
                    regStartDateMillis = regStartDateMillis, onRegStartDate = { regStartDateMillis = it },
                    regStartTime = regStartTime, onRegStartTime = { regStartTime = it },
                    zoneId = zoneId,
                    regEndMode = registrationEndMode, onRegEndMode = { registrationEndMode = it },
                    maxParticipants = maxParticipants, onMaxParticipants = { maxParticipants = it },
                    feeAmount = feeAmount, onFee = { feeAmount = it },
                )
                2 -> organizerStep(
                    contactPhone = contactPhone, onPhone = { contactPhone = it },
                    contactEmail = contactEmail, onEmail = { contactEmail = it },
                    website = website, onWebsite = { website = it },
                    regulationUrl = regulationUrl, onRegulation = { regulationUrl = it },
                    mapUrl = mapUrl, onMap = { mapUrl = it },
                )
                3 -> distancesStep(
                    distances = distances,
                    importXmlName = importXmlName,
                    onAdd = { showDistanceDialog = true },
                    onRemove = { idx -> distances = distances.filterIndexed { i, _ -> i != idx } },
                    onPickXml = {
                        pickXmlFile { name, content ->
                            importXmlName = name
                            importXmlBytes = content.encodeToByteArray()
                        }
                    },
                    onClearXml = { importXmlName = null; importXmlBytes = null },
                )
                4 -> groupsStep(groups = groups, distances = distances, onAdd = { showGroupDialog = true }, onRemove = { idx -> groups = groups.filterIndexed { i, _ -> i != idx } })
            }

            error?.let { err ->
                item { Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }

    if (showDistanceDialog) {
        DistanceDialog(
            onDismiss = { showDistanceDialog = false },
            onSave = { distances = distances + it; showDistanceDialog = false },
        )
    }
    if (showGroupDialog) {
        GroupDialog(
            distances = distances,
            onDismiss = { showGroupDialog = false },
            onSave = { groups = groups + it; showGroupDialog = false },
        )
    }
}

// ----------------------- Шаги -----------------------

private fun androidx.compose.foundation.lazy.LazyListScope.sectionTitle(text: String) {
    item { Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
}

private fun androidx.compose.foundation.lazy.LazyListScope.basicStep(
    title: String, onTitle: (String) -> Unit,
    startDateMillis: Long?, onStartDate: (Long) -> Unit,
    startTime: String, onStartTime: (String) -> Unit,
    zoneId: String, onZone: (String) -> Unit,
    address: String, onAddress: (String) -> Unit,
    description: String, onDescription: (String) -> Unit,
    direction: String, onDirection: (String) -> Unit,
    punchingSystem: String, onPunching: (String) -> Unit,
    startTimeMode: String, onStartMode: (String) -> Unit,
    startInterval: Int, onInterval: (Int) -> Unit,
) {
    sectionTitle("Основная информация")
    item {
        OutlinedTextField(
            value = title, onValueChange = onTitle,
            label = { Text("Название *") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
    }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField(
                label = "Дата старта *",
                displayValue = startDateMillis?.let { utcMillisToZonedDate(it, "UTC") } ?: "",
                initialUtcMillis = startDateMillis,
                modifier = Modifier.weight(1f),
                onPick = onStartDate,
            )
            TimeField(label = "Время старта", value = startTime, modifier = Modifier.weight(1f), onChange = onStartTime)
        }
    }
    item { TimeZoneField(zoneId = zoneId, modifier = Modifier.fillMaxWidth(), onSelect = onZone) }
    item {
        OutlinedTextField(
            value = address, onValueChange = onAddress,
            label = { Text("Место проведения") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
    }
    item {
        OutlinedTextField(
            value = description, onValueChange = onDescription,
            label = { Text("Описание") },
            modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 6,
        )
    }
    sectionTitle("Параметры ориентирования")
    item { LabeledDropdown("Направление", direction, DIRECTION_OPTIONS, Modifier.fillMaxWidth(), onDirection) }
    item { LabeledDropdown("Система отметки", punchingSystem, PUNCHING_SYSTEM_OPTIONS, Modifier.fillMaxWidth(), onPunching) }
    item { LabeledDropdown("Режим старта", startTimeMode, START_TIME_MODE_OPTIONS, Modifier.fillMaxWidth(), onStartMode) }
    item {
        LabeledDropdown(
            "Интервал старта", startInterval,
            START_INTERVAL_OPTIONS.map { it to formatIntervalSeconds(it) },
            Modifier.fillMaxWidth(), onInterval,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.registrationStep(
    openImmediately: Boolean, onOpenImmediately: (Boolean) -> Unit,
    regStartDateMillis: Long?, onRegStartDate: (Long) -> Unit,
    regStartTime: String, onRegStartTime: (String) -> Unit,
    zoneId: String,
    regEndMode: String, onRegEndMode: (String) -> Unit,
    maxParticipants: String, onMaxParticipants: (String) -> Unit,
    feeAmount: String, onFee: (String) -> Unit,
) {
    sectionTitle("Регистрация")
    item {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Открыть регистрацию сразу", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Иначе укажите дату и время начала регистрации",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = openImmediately, onCheckedChange = onOpenImmediately)
        }
    }
    if (!openImmediately) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField(
                    label = "Начало регистрации",
                    displayValue = regStartDateMillis?.let { utcMillisToZonedDate(it, "UTC") } ?: "",
                    initialUtcMillis = regStartDateMillis,
                    modifier = Modifier.weight(1f),
                    onPick = onRegStartDate,
                )
                TimeField(label = "Время", value = regStartTime, modifier = Modifier.weight(1f), onChange = onRegStartTime)
            }
        }
    }
    item { LabeledDropdown("Окончание регистрации", regEndMode, REG_END_MODE_OPTIONS, Modifier.fillMaxWidth(), onRegEndMode) }
    item {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = maxParticipants,
                onValueChange = { onMaxParticipants(it.filter { c -> c.isDigit() }) },
                label = { Text("Макс. участников") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            OutlinedTextField(
                value = feeAmount,
                onValueChange = { onFee(it.filter { c -> c.isDigit() || c == '.' }) },
                label = { Text("Взнос (руб.)") },
                modifier = Modifier.weight(1f), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.organizerStep(
    contactPhone: String, onPhone: (String) -> Unit,
    contactEmail: String, onEmail: (String) -> Unit,
    website: String, onWebsite: (String) -> Unit,
    regulationUrl: String, onRegulation: (String) -> Unit,
    mapUrl: String, onMap: (String) -> Unit,
) {
    sectionTitle("Контакты организатора")
    item {
        OutlinedTextField(
            value = contactPhone, onValueChange = onPhone,
            label = { Text("Телефон *") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
        )
    }
    item {
        OutlinedTextField(
            value = contactEmail, onValueChange = onEmail,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        )
    }
    sectionTitle("Ссылки")
    item {
        OutlinedTextField(
            value = website, onValueChange = onWebsite,
            label = { Text("Сайт соревнования") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
    }
    item {
        OutlinedTextField(
            value = regulationUrl, onValueChange = onRegulation,
            label = { Text("Ссылка на положение") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
    }
    item {
        OutlinedTextField(
            value = mapUrl, onValueChange = onMap,
            label = { Text("Ссылка на карту") },
            modifier = Modifier.fillMaxWidth(), singleLine = true,
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.distancesStep(
    distances: List<PendingDistance>,
    importXmlName: String?,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
    onPickXml: () -> Unit,
    onClearXml: () -> Unit,
) {
    sectionTitle("Дистанции")
    item {
        Text(
            "Добавьте хотя бы одну дистанцию — её можно будет выбрать для групп на следующем шаге.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    item {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Импорт из Mapper (IOF XML)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                Text(
                    "Выберите готовый XML-файл с дистанциями — он будет импортирован при создании соревнования.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (importXmlName == null) {
                    OutlinedButton(onClick = onPickXml, modifier = Modifier.fillMaxWidth()) {
                        Text("Загрузить IOF XML файл")
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Файл: $importXmlName", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClearXml) {
                            Text("Убрать", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    if (distances.isEmpty()) {
        item { Text("Пока нет дистанций", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        distances.forEachIndexed { index, d ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(d.name ?: "Без названия", fontWeight = FontWeight.Bold)
                            Text(
                                "Длина: ${d.lengthMeters} м  •  Набор: ${d.climbMeters} м  •  КП: ${d.controlPoints.size}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        TextButton(onClick = { onRemove(index) }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    item {
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("+ Добавить дистанцию")
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.groupsStep(
    groups: List<PendingGroup>,
    distances: List<PendingDistance>,
    onAdd: () -> Unit,
    onRemove: (Int) -> Unit,
) {
    sectionTitle("Группы участников")
    if (groups.isEmpty()) {
        item { Text("Пока нет групп", color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        groups.forEachIndexed { index, g ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(g.title, fontWeight = FontWeight.Bold)
                            val distName = distances.getOrNull(g.distanceIndex)?.name ?: "—"
                            val ageStr = if (g.minAge != null || g.maxAge != null) "${g.minAge ?: ""}–${g.maxAge ?: ""} лет  •  " else ""
                            Text("${ageStr}Дистанция: $distName", style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = { onRemove(index) }) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
    item {
        OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text("+ Добавить группу")
        }
    }
}

// ----------------------- Диалоги -----------------------

@Composable
private fun DistanceDialog(
    onDismiss: () -> Unit,
    onSave: (PendingDistance) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var lengthMeters by remember { mutableStateOf("") }
    var climbMeters by remember { mutableStateOf("") }
    var controlPointsInput by remember { mutableStateOf("") }
    var finishCp by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая дистанция") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Название") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lengthMeters, onValueChange = { lengthMeters = it.filter { c -> c.isDigit() } },
                        label = { Text("Длина (м)") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = climbMeters, onValueChange = { climbMeters = it.filter { c -> c.isDigit() } },
                        label = { Text("Набор (м)") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = controlPointsInput, onValueChange = { controlPointsInput = it },
                    label = { Text("КП через пробел") }, placeholder = { Text("31 32 33 34") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = finishCp, onValueChange = { finishCp = it.filter { c -> c.isDigit() } },
                    label = { Text("Финишное КП") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Описание") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val controlPoints = controlPointsInput.trim()
                    .split(Regex("\\s+"))
                    .mapNotNull { it.toIntOrNull() }
                    .map { ControlPoint(number = it) }
                onSave(
                    PendingDistance(
                        name = name.trimOrNull(),
                        lengthMeters = lengthMeters.toIntOrNull() ?: 0,
                        climbMeters = climbMeters.toIntOrNull() ?: 0,
                        controlPoints = controlPoints,
                        finishControlPoint = finishCp.toIntOrNull(),
                        description = description.trimOrNull(),
                    )
                )
            }) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GroupDialog(
    distances: List<PendingDistance>,
    onDismiss: () -> Unit,
    onSave: (PendingGroup) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var minAge by remember { mutableStateOf("") }
    var maxAge by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") }
    var distanceIndex by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая группа") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Название * (М21, Ж18, Open)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minAge, onValueChange = { minAge = it.filter { c -> c.isDigit() } },
                        label = { Text("Мин. возраст") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = maxAge, onValueChange = { maxAge = it.filter { c -> c.isDigit() } },
                        label = { Text("Макс. возраст") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = maxParticipants, onValueChange = { maxParticipants = it.filter { c -> c.isDigit() } },
                    label = { Text("Лимит участников") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                if (distances.isEmpty()) {
                    Text("Сначала добавьте дистанцию", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                } else {
                    LabeledDropdown(
                        "Дистанция *", distanceIndex,
                        distances.mapIndexed { i, d -> i to (d.name ?: "Дистанция ${i + 1}") },
                        Modifier.fillMaxWidth(),
                    ) { distanceIndex = it }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                enabled = distances.isNotEmpty(),
                onClick = {
                    if (title.isBlank()) { error = "Укажите название"; return@Button }
                    onSave(
                        PendingGroup(
                            title = title.trim(),
                            minAge = minAge.toIntOrNull(),
                            maxAge = maxAge.toIntOrNull(),
                            maxParticipants = maxParticipants.toIntOrNull(),
                            distanceIndex = distanceIndex,
                        )
                    )
                },
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

private fun String.trimOrNull(): String? = trim().takeIf { it.isNotEmpty() }
