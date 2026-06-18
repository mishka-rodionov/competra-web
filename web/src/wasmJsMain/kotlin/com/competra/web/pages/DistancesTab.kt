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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.DistanceRepository
import com.competra.domain.models.ControlPoint
import com.competra.domain.models.Distance
import com.competra.domain.models.SaveDistanceRequest
import com.competra.web.components.XmlImportField
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DistancesTab(remoteId: Long?, showImport: Boolean = false) {
    val repo: DistanceRepository = koinInject()
    val scope = rememberCoroutineScope()

    var distances by remember { mutableStateOf<List<Distance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }

    LaunchedEffect(remoteId) {
        if (remoteId == null) { loading = false; return@LaunchedEffect }
        when (val r = repo.getByCompetition(remoteId)) {
            is ApiResult.Success -> distances = r.data
            is ApiResult.Error   -> error = r.message
        }
        loading = false
    }

    if (showCreateDialog && remoteId != null) {
        CreateDistanceDialog(
            remoteId = remoteId,
            onDismiss = { showCreateDialog = false },
            onSaved = { updated ->
                distances = updated
                showCreateDialog = false
            },
            onError = { error = it },
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        if (showImport && remoteId != null) {
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
                        when (val r = repo.importFromXml(remoteId, bytes)) {
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
                items(distances) { DistanceCard(it) }
            }
        }
    }
}

@Composable
private fun CreateDistanceDialog(
    remoteId: Long,
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
                    placeholder = { Text("31 32 33 34") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
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
                    val controlPoints = controlPointsInput.trim()
                        .split(Regex("\\s+"))
                        .mapNotNull { it.toIntOrNull() }
                        .map { ControlPoint(number = it) }
                    val request = SaveDistanceRequest(
                        distanceId = null,
                        competitionId = remoteId,
                        name = name.trim().takeIf { it.isNotEmpty() },
                        lengthMeters = lengthMeters.toIntOrNull() ?: 0,
                        climbMeters = climbMeters.toIntOrNull() ?: 0,
                        controlsCount = controlPoints.size,
                        description = description.trim().takeIf { it.isNotEmpty() },
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

@Composable
internal fun DistanceCard(distance: Distance) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(distance.name ?: "Без названия", style = MaterialTheme.typography.titleSmall)
            Text(
                "Длина: ${distance.lengthMeters} м  •  Набор: ${distance.climbMeters} м  •  КП: ${distance.controlsCount}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
