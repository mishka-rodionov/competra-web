package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.DiaryRepository
import com.competra.domain.diary.TrackCodec
import com.competra.domain.models.BikeDetails
import com.competra.domain.models.RunDetails
import com.competra.domain.models.SkiDetails
import com.competra.domain.models.Workout
import com.competra.domain.models.WorkoutRequest
import com.competra.web.components.LabeledDropdown
import com.competra.web.components.DateField
import com.competra.web.utils.nowMillis
import com.competra.web.utils.parseGpxTrackPoints
import com.competra.web.utils.pickGpxFile
import com.competra.web.utils.toLocaleDateString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutEditorPage(
    workoutId: Long?,
    onBack: () -> Unit,
    onSaved: (Workout) -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val repo: DiaryRepository = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val scope = rememberCoroutineScope()

    if (!tokenStorage.isLoggedIn()) {
        LoginPage(onLoginSuccess = onLoginSuccess)
        return
    }

    var loading by remember { mutableStateOf(workoutId != null) }
    var sportType by remember { mutableStateOf("RUNNING") }
    var status by remember { mutableStateOf("COMPLETED") }
    var dateMillis by remember { mutableStateOf<Long?>(null) }
    var durationH by remember { mutableStateOf("") }
    var durationM by remember { mutableStateOf("") }
    var durationS by remember { mutableStateOf("") }
    var distanceKm by remember { mutableStateOf("") }
    var elevationGain by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var cadenceSpm by remember { mutableStateOf("") }
    var cadenceRpm by remember { mutableStateOf("") }
    var powerWatts by remember { mutableStateOf("") }
    var skiStyle by remember { mutableStateOf("CLASSIC") }
    var trackEncoded by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(workoutId) {
        if (workoutId == null) return@LaunchedEffect
        when (val r = repo.getWorkouts()) {
            is ApiResult.Success -> {
                val w = r.data.firstOrNull { it.id == workoutId }
                if (w == null) {
                    error = "Тренировка не найдена"
                } else {
                    sportType = w.sportType
                    status = w.status
                    dateMillis = w.startedAt ?: w.scheduledDate
                    w.durationSeconds?.let {
                        durationH = (it / 3600).toString()
                        durationM = ((it % 3600) / 60).toString()
                        durationS = (it % 60).toString()
                    }
                    w.distanceMeters?.let { distanceKm = (it / 1000.0).toString() }
                    w.elevationGainMeters?.let { elevationGain = it.toString() }
                    notes = w.notes ?: ""
                    w.runDetails?.cadenceSpm?.let { cadenceSpm = it.toString() }
                    w.bikeDetails?.cadenceRpm?.let { cadenceRpm = it.toString() }
                    w.bikeDetails?.powerWatts?.let { powerWatts = it.toString() }
                    w.skiDetails?.style?.let { skiStyle = it }
                    trackEncoded = w.trackEncoded
                }
            }
            is ApiResult.Error -> error = r.message
        }
        loading = false
    }

    fun totalDurationSeconds(): Int? {
        if (durationH.isBlank() && durationM.isBlank() && durationS.isBlank()) return null
        val h = durationH.toIntOrNull() ?: 0
        val m = durationM.toIntOrNull() ?: 0
        val s = durationS.toIntOrNull() ?: 0
        return h * 3600 + m * 60 + s
    }

    fun save() {
        val date = dateMillis
        if (date == null) {
            error = "Укажите дату"
            return
        }
        val isCompleted = status == "COMPLETED"
        error = null
        saving = true
        val request = WorkoutRequest(
            workoutId = workoutId,
            sportType = sportType,
            status = status,
            scheduledDate = if (isCompleted) null else date,
            startedAt = if (isCompleted) date else null,
            durationSeconds = if (isCompleted) totalDurationSeconds() else null,
            distanceMeters = if (isCompleted) distanceKm.toDoubleOrNull()?.let { (it * 1000).toInt() } else null,
            elevationGainMeters = if (isCompleted) elevationGain.toIntOrNull() else null,
            notes = notes.trim().ifEmpty { null },
            trackEncoded = if (isCompleted) trackEncoded else null,
            runDetails = if (isCompleted && sportType == "RUNNING") RunDetails(cadenceSpm.toIntOrNull()) else null,
            bikeDetails = if (isCompleted && sportType == "CYCLING") BikeDetails(cadenceRpm.toIntOrNull(), powerWatts.toIntOrNull()) else null,
            skiDetails = if (isCompleted && sportType == "SKIING") SkiDetails(skiStyle) else null,
        )
        scope.launch {
            when (val r = repo.saveWorkout(request)) {
                is ApiResult.Success -> {
                    val saved = r.data.firstOrNull()
                    if (saved != null) onSaved(saved) else error = "Пустой ответ сервера"
                    saving = false
                }
                is ApiResult.Error -> {
                    error = r.message
                    saving = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (workoutId == null) "Новая тренировка" else "Редактирование тренировки") },
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

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LabeledDropdown(
                label = "Вид спорта",
                selectedKey = sportType,
                options = SPORT_TYPE_OPTIONS,
                modifier = Modifier.fillMaxWidth(),
                onSelect = { sportType = it },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WORKOUT_STATUS_OPTIONS.forEach { (key, label) ->
                    FilterChip(selected = status == key, onClick = { status = key }, label = { Text(label) })
                }
            }

            DateField(
                label = "Дата",
                displayValue = dateMillis?.toLocaleDateString() ?: "",
                initialUtcMillis = dateMillis,
                modifier = Modifier.fillMaxWidth(),
                onPick = { dateMillis = it },
            )

            if (status == "COMPLETED") {
                Text("Длительность", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = durationH, onValueChange = { v -> durationH = v.filter { it.isDigit() } },
                        label = { Text("Ч") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = durationM, onValueChange = { v -> durationM = v.filter { it.isDigit() } },
                        label = { Text("М") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    OutlinedTextField(
                        value = durationS, onValueChange = { v -> durationS = v.filter { it.isDigit() } },
                        label = { Text("С") }, modifier = Modifier.weight(1f), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }

                OutlinedTextField(
                    value = distanceKm, onValueChange = { v -> distanceKm = v.filter { it.isDigit() || it == '.' } },
                    label = { Text("Дистанция, км") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = elevationGain, onValueChange = { v -> elevationGain = v.filter { it.isDigit() } },
                    label = { Text("Набор высоты, м") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )

                when (sportType) {
                    "RUNNING" -> OutlinedTextField(
                        value = cadenceSpm, onValueChange = { v -> cadenceSpm = v.filter { it.isDigit() } },
                        label = { Text("Каденс, шаг/мин") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                    "CYCLING" -> {
                        OutlinedTextField(
                            value = cadenceRpm, onValueChange = { v -> cadenceRpm = v.filter { it.isDigit() } },
                            label = { Text("Каденс, об/мин") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedTextField(
                            value = powerWatts, onValueChange = { v -> powerWatts = v.filter { it.isDigit() } },
                            label = { Text("Мощность, Вт") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                    }
                    "SKIING" -> LabeledDropdown(
                        label = "Стиль",
                        selectedKey = skiStyle,
                        options = SKI_STYLE_OPTIONS,
                        modifier = Modifier.fillMaxWidth(),
                        onSelect = { skiStyle = it },
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Трек", style = MaterialTheme.typography.titleSmall)
                    val encoded = trackEncoded
                    if (encoded != null) {
                        Text(
                            "Трек загружен: ${encoded.count { it == ';' } + 1} точек",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            pickGpxFile { _, content ->
                                val points = parseGpxTrackPoints(content)
                                if (points.isNotEmpty()) {
                                    trackEncoded = TrackCodec.encode(dateMillis ?: nowMillis(), points)
                                }
                            }
                        }) { Text("Импортировать GPX") }
                        if (encoded != null) {
                            OutlinedButton(onClick = { trackEncoded = null }) { Text("Убрать трек") }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Заметка") }, modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5,
            )

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = { save() }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text("Сохранить")
            }
        }
    }
}
