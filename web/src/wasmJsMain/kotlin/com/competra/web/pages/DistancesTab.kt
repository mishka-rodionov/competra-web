package com.competra.web.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Route
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.DistanceRepository
import com.competra.domain.models.ControlPoint
import com.competra.domain.models.Distance
import com.competra.domain.models.SaveDistanceRequest
import com.competra.web.components.DistanceMapView
import com.competra.web.components.XmlImportField
import com.competra.web.utils.pickDistanceMapFile
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DistancesTab(competitionId: String?, showImport: Boolean = false, isByChoice: Boolean = false) {
    val repo: DistanceRepository = koinInject()
    val scope = rememberCoroutineScope()

    var distances by remember { mutableStateOf<List<Distance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var expandedMapDistance by remember { mutableStateOf<Distance?>(null) }

    LaunchedEffect(competitionId) {
        if (competitionId == null) { loading = false; return@LaunchedEffect }
        when (val r = repo.getByCompetition(competitionId)) {
            is ApiResult.Success -> distances = r.data
            is ApiResult.Error   -> error = r.message
        }
        loading = false
    }

    if (showCreateDialog && competitionId != null) {
        CreateDistanceDialog(
            competitionId = competitionId,
            isByChoice = isByChoice,
            onDismiss = { showCreateDialog = false },
            onSaved = { updated ->
                distances = updated
                showCreateDialog = false
            },
            onError = { error = it },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (showImport && competitionId != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Дистанции", style = MaterialTheme.typography.titleSmall)
                OutlinedButton(onClick = { showCreateDialog = true }) {
                    Text("+ Создать")
                }
            }

            Text(
                "Импорт из Mapper",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
            )
            XmlImportField(
                modifier = Modifier.fillMaxWidth(),
                onXmlReady = { bytes ->
                    scope.launch {
                        importing = true
                        error = null
                        when (val r = repo.importFromXml(competitionId, bytes)) {
                            is ApiResult.Success -> distances = r.data
                            is ApiResult.Error   -> error = r.message
                        }
                        importing = false
                    }
                },
            )
            if (importing) CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        } else if (distances.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (showImport) "Нет дистанций. Создайте или импортируйте из Mapper." else "Дистанции не добавлены",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(distances) { d ->
                    DistanceCard(
                        distance = d,
                        canEditMap = showImport,
                        onMapUpdated = { updated -> distances = distances.map { if (it.id == updated.id) updated else it } },
                        onExpandMap = { expandedMapDistance = it },
                    )
                }
            }
        }
    }

    expandedMapDistance?.let { d ->
        ExpandedDistanceMap(distance = d, onDismiss = { expandedMapDistance = null })
    }
    }
}

@Composable
private fun CreateDistanceDialog(
    competitionId: String,
    isByChoice: Boolean,
    onDismiss: () -> Unit,
    onSaved: (List<Distance>) -> Unit,
    onError: (String) -> Unit,
) {
    val repo: DistanceRepository = koinInject()
    val scope = rememberCoroutineScope()

    var name by remember { mutableStateOf("") }
    var lengthMeters by remember { mutableStateOf("") }
    var climbMeters by remember { mutableStateOf("") }
    var controlPointsInput by remember { mutableStateOf("") }
    var finishCp by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая дистанция") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = lengthMeters,
                        onValueChange = { lengthMeters = it.filter { c -> c.isDigit() } },
                        label = { Text("Длина (м)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = climbMeters,
                        onValueChange = { climbMeters = it.filter { c -> c.isDigit() } },
                        label = { Text("Набор (м)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                OutlinedTextField(
                    value = controlPointsInput,
                    onValueChange = { controlPointsInput = it },
                    label = { Text("КП через пробел") },
                    placeholder = { Text(if (isByChoice) "31:2 32:5 33:3 34" else "31 32 33 34") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (isByChoice) {
                    Text(
                        "Формат «по выбору»: номер:баллы (например 32:5). Без баллов — по умолчанию 2.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedTextField(
                    value = finishCp,
                    onValueChange = { finishCp = it.filter { c -> c.isDigit() } },
                    label = { Text("Финишное КП") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val controlPoints = parseControlPoints(controlPointsInput, isByChoice)
                    val request = SaveDistanceRequest(
                        distanceId = null,
                        competitionId = competitionId,
                        name = name.trim().takeIf { it.isNotEmpty() },
                        lengthMeters = lengthMeters.toIntOrNull() ?: 0,
                        climbMeters = climbMeters.toIntOrNull() ?: 0,
                        controlsCount = controlPoints.size,
                        description = description.trim(),
                        controlPoints = controlPoints,
                        finishControlPoint = finishCp.toIntOrNull(),
                    )
                    scope.launch {
                        saving = true
                        when (val r = repo.saveDistance(request)) {
                            is ApiResult.Success -> onSaved(r.data)
                            is ApiResult.Error -> { error = r.message; saving = false }
                        }
                    }
                },
                enabled = !saving,
            ) {
                if (saving) CircularProgressIndicator() else Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        },
    )
}

/**
 * Разбирает поле «КП через пробел». Для обычных дистанций — просто номера ("31 32 33").
 * Для «по выбору» (BY_CHOICE) каждый номер может нести баллы через двоеточие ("31:2 32:5"),
 * при отсутствии баллов — дефолт 2 (как на Android, DistanceEditor.kt).
 */
internal fun parseControlPoints(input: String, isByChoice: Boolean): List<ControlPoint> =
    input.trim()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .mapNotNull { token ->
            val parts = token.split(":")
            val number = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
            val score = parts.getOrNull(1)?.toIntOrNull() ?: if (isByChoice) 2 else 0
            ControlPoint(number = number, score = score)
        }

@Composable
internal fun DistanceCard(
    distance: Distance,
    canEditMap: Boolean = false,
    onMapUpdated: (Distance) -> Unit = {},
    onExpandMap: (Distance) -> Unit = {},
) {
    var showAttachMapDialog by remember { mutableStateOf(false) }

    if (showAttachMapDialog) {
        AttachMapDialog(
            distance = distance,
            onDismiss = { showAttachMapDialog = false },
            onSaved = {
                onMapUpdated(it)
                showAttachMapDialog = false
            },
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Route, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Text(distance.name ?: "Без названия", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                DistanceStat("Длина", "${distance.lengthMeters} м")
                DistanceStat("Набор", "${distance.climbMeters} м")
                DistanceStat("КП", "${distance.controlsCount}")
            }

            val mapUrl = distance.mapUrl
            val topLeftLat = distance.mapTopLeftLat
            val topLeftLng = distance.mapTopLeftLng
            val bottomRightLat = distance.mapBottomRightLat
            val bottomRightLng = distance.mapBottomRightLng
            if (mapUrl != null && topLeftLat != null && topLeftLng != null && bottomRightLat != null && bottomRightLng != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onExpandMap(distance) },
                ) {
                    DistanceMapView(
                        mapUrl = mapUrl,
                        topLeftLat = topLeftLat,
                        topLeftLng = topLeftLng,
                        bottomRightLat = bottomRightLat,
                        bottomRightLng = bottomRightLng,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    ) {
                        IconButton(onClick = { onExpandMap(distance) }) {
                            Icon(Icons.Filled.OpenInFull, contentDescription = "Развернуть карту")
                        }
                    }
                }
            }

            if (canEditMap) {
                OutlinedButton(onClick = { showAttachMapDialog = true }) {
                    Text(if (mapUrl != null) "Заменить карту" else "Прикрепить карту")
                }
            }
        }
    }
}

/**
 * Развёрнутая карта дистанции поверх остального содержимого вкладки: интерактивная версия
 * [DistanceMapView] (драг для перемещения, кнопки +/- для зума) на всю доступную область,
 * с кнопкой закрытия. В отличие от превью в [DistanceCard], здесь можно "полазить" по карте.
 */
@Composable
private fun ExpandedDistanceMap(distance: Distance, onDismiss: () -> Unit) {
    val mapUrl = distance.mapUrl ?: return onDismiss()
    val topLeftLat = distance.mapTopLeftLat ?: return onDismiss()
    val topLeftLng = distance.mapTopLeftLng ?: return onDismiss()
    val bottomRightLat = distance.mapBottomRightLat ?: return onDismiss()
    val bottomRightLng = distance.mapBottomRightLng ?: return onDismiss()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        DistanceMapView(
            mapUrl = mapUrl,
            topLeftLat = topLeftLat,
            topLeftLng = topLeftLng,
            bottomRightLat = bottomRightLat,
            bottomRightLng = bottomRightLng,
            interactive = true,
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Закрыть")
            }
            Text(
                distance.name ?: "Карта дистанции",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

/**
 * Диалог прикрепления карты дистанции: организатор выбирает растр/PDF, экспортированный
 * из mapper (кнопка «Copy WGS84 map corners for Competra» в диалоге экспорта копирует
 * 4 нужных числа в буфер обмена), и вводит координаты его углов вручную.
 */
@Composable
private fun AttachMapDialog(
    distance: Distance,
    onDismiss: () -> Unit,
    onSaved: (Distance) -> Unit,
) {
    val repo: DistanceRepository = koinInject()
    val scope = rememberCoroutineScope()

    var fileName by remember { mutableStateOf<String?>(null) }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileContentType by remember { mutableStateOf("application/octet-stream") }
    var topLeftLat by remember { mutableStateOf(distance.mapTopLeftLat?.toString() ?: "") }
    var topLeftLng by remember { mutableStateOf(distance.mapTopLeftLng?.toString() ?: "") }
    var bottomRightLat by remember { mutableStateOf(distance.mapBottomRightLat?.toString() ?: "") }
    var bottomRightLng by remember { mutableStateOf(distance.mapBottomRightLng?.toString() ?: "") }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val coordsValid = topLeftLat.toDoubleOrNull() != null && topLeftLng.toDoubleOrNull() != null &&
        bottomRightLat.toDoubleOrNull() != null && bottomRightLng.toDoubleOrNull() != null
    val canSave = coordsValid && (fileBytes != null || distance.mapUrl != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Карта дистанции «${distance.name ?: "Без названия"}»") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Загружайте карту после окончания соревнования — иначе участники смогут увидеть её до старта.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = {
                    pickDistanceMapFile { name, contentType, bytes ->
                        fileName = name
                        fileContentType = contentType
                        fileBytes = bytes
                    }
                }) {
                    Text("Выбрать файл карты (PNG/JPG)")
                }
                fileName?.let {
                    Text("Выбран файл: $it", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "Координаты углов (из диалога экспорта в mapper — «Copy WGS84 map corners for Competra»):",
                    style = MaterialTheme.typography.bodySmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = topLeftLat,
                        onValueChange = { topLeftLat = it },
                        label = { Text("Top-left lat") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = topLeftLng,
                        onValueChange = { topLeftLng = it },
                        label = { Text("Top-left lng") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = bottomRightLat,
                        onValueChange = { bottomRightLat = it },
                        label = { Text("Bottom-right lat") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = bottomRightLng,
                        onValueChange = { bottomRightLng = it },
                        label = { Text("Bottom-right lng") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                    )
                }
                if (!canSave) {
                    Text(
                        "Не хватает: " + listOfNotNull(
                            "файл карты".takeIf { fileBytes == null && distance.mapUrl == null },
                            "top-left lat".takeIf { topLeftLat.toDoubleOrNull() == null },
                            "top-left lng".takeIf { topLeftLng.toDoubleOrNull() == null },
                            "bottom-right lat".takeIf { bottomRightLat.toDoubleOrNull() == null },
                            "bottom-right lng".takeIf { bottomRightLng.toDoubleOrNull() == null },
                        ).joinToString(", "),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        saving = true
                        error = null

                        var mapUrl = distance.mapUrl
                        val bytes = fileBytes
                        if (bytes != null) {
                            when (val uploadResult = repo.uploadDistanceMap(bytes, fileName ?: "map", fileContentType)) {
                                is ApiResult.Success -> mapUrl = uploadResult.data
                                is ApiResult.Error -> {
                                    error = uploadResult.message
                                    saving = false
                                    return@launch
                                }
                            }
                        }

                        val request = SaveDistanceRequest(
                            distanceId = distance.id,
                            competitionId = distance.competitionId,
                            name = distance.name,
                            lengthMeters = distance.lengthMeters,
                            climbMeters = distance.climbMeters,
                            controlsCount = distance.controlsCount,
                            description = distance.description ?: "",
                            controlPoints = distance.controlPoints,
                            finishControlPoint = distance.finishControlPoint,
                            mapUrl = mapUrl,
                            mapTopLeftLat = topLeftLat.toDoubleOrNull(),
                            mapTopLeftLng = topLeftLng.toDoubleOrNull(),
                            mapBottomRightLat = bottomRightLat.toDoubleOrNull(),
                            mapBottomRightLng = bottomRightLng.toDoubleOrNull(),
                        )
                        when (val r = repo.saveDistance(request)) {
                            is ApiResult.Success -> r.data.firstOrNull()?.let(onSaved)
                            is ApiResult.Error -> { error = r.message; saving = false }
                        }
                    }
                },
                enabled = !saving && canSave,
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
private fun DistanceStat(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
