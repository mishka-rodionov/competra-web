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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
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
import com.competra.data.repository.CompetitionRepository
import com.competra.domain.models.CompetitionFields
import com.competra.domain.models.CreateCompetitionRequest
import com.competra.domain.models.OrienteeringCompetition
import com.competra.web.utils.generateUUID
import com.competra.web.utils.parseDateStringToMillis
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCompetitionPage(
    onBack: () -> Unit,
    onCreated: (OrienteeringCompetition) -> Unit,
) {
    val repo: CompetitionRepository = koinInject()
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf("") }
    var endDate by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var registrationStart by remember { mutableStateOf("") }
    var registrationEnd by remember { mutableStateOf("") }
    var maxParticipants by remember { mutableStateOf("") }
    var feeAmount by remember { mutableStateOf("") }
    var contactEmail by remember { mutableStateOf("") }
    var contactPhone by remember { mutableStateOf("") }

    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Создать соревнование") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Назад") }
                },
            )
        }
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
            item {
                Text("Основная информация", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
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
                    OutlinedTextField(
                        value = startDate,
                        onValueChange = { startDate = it },
                        label = { Text("Дата начала *") },
                        placeholder = { Text("ГГГГ-ММ-ДД") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = endDate,
                        onValueChange = { endDate = it },
                        label = { Text("Дата окончания") },
                        placeholder = { Text("ГГГГ-ММ-ДД") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
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
                Text("Регистрация", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = registrationStart,
                        onValueChange = { registrationStart = it },
                        label = { Text("Начало регистрации") },
                        placeholder = { Text("ГГГГ-ММ-ДД") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = registrationEnd,
                        onValueChange = { registrationEnd = it },
                        label = { Text("Конец регистрации") },
                        placeholder = { Text("ГГГГ-ММ-ДД") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxParticipants,
                        onValueChange = { maxParticipants = it.filter { c -> c.isDigit() } },
                        label = { Text("Макс. участников") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = feeAmount,
                        onValueChange = { feeAmount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text("Взнос (руб.)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }

            item {
                Text("Контакты", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
            }
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

            error?.let { err ->
                item {
                    Text(err, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }

            item {
                Button(
                    onClick = {
                        val startMs = parseDateStringToMillis(startDate)
                        if (title.isBlank()) { error = "Укажите название"; return@Button }
                        if (startMs == null) { error = "Укажите дату начала в формате ГГГГ-ММ-ДД"; return@Button }

                        scope.launch {
                            saving = true
                            error = null
                            val competitionId = generateUUID()
                            val request = CreateCompetitionRequest(
                                competitionId = competitionId,
                                competition = CompetitionFields(
                                    title = title.trim(),
                                    startDate = startMs,
                                    endDate = if (endDate.isNotBlank()) parseDateStringToMillis(endDate) else null,
                                    kindOfSport = "Orienteering",
                                    description = description.trimOrNull(),
                                    address = address.trimOrNull(),
                                    status = "REGISTRATION_OPEN",
                                    registrationStart = if (registrationStart.isNotBlank()) parseDateStringToMillis(registrationStart) else null,
                                    registrationEnd = if (registrationEnd.isNotBlank()) parseDateStringToMillis(registrationEnd) else null,
                                    maxParticipants = maxParticipants.toIntOrNull(),
                                    feeAmount = feeAmount.toDoubleOrNull(),
                                    feeCurrency = if (feeAmount.isNotBlank()) "RUB" else null,
                                    contactEmail = contactEmail.trimOrNull(),
                                    contactPhone = contactPhone.trimOrNull(),
                                ),
                            )
                            when (val r = repo.createCompetition(request)) {
                                is ApiResult.Success -> onCreated(r.data)
                                is ApiResult.Error -> {
                                    error = r.message
                                    saving = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    Text("Создать соревнование")
                }
            }
        }
    }
}

private fun String.trimOrNull(): String? = trim().takeIf { it.isNotEmpty() }
