package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.competra.data.repository.DiaryRepository
import com.competra.domain.models.Workout
import com.competra.web.utils.formatTime
import com.competra.web.utils.toLocaleDateString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutDetailPage(
    workoutId: Long,
    onBack: () -> Unit,
    onEditClick: (Long) -> Unit,
    onTrackClick: (Long) -> Unit,
) {
    val repo: DiaryRepository = koinInject()
    val scope = rememberCoroutineScope()

    var workout by remember { mutableStateOf<Workout?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(workoutId) {
        loading = true
        when (val r = repo.getWorkouts()) {
            is ApiResult.Success -> {
                workout = r.data.firstOrNull { it.id == workoutId }
                if (workout == null) error = "Тренировка не найдена"
            }
            is ApiResult.Error -> error = r.message
        }
        loading = false
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить тренировку?") },
            text = { Text("Действие необратимо.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (repo.deleteWorkout(workoutId)) {
                            is ApiResult.Success -> onBack()
                            is ApiResult.Error -> error = "Не удалось удалить тренировку"
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
                title = { Text(workout?.let { sportTypeLabel(it.sportType) } ?: "Тренировка") },
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

        val w = workout
        if (w == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Тренировка не найдена")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val isPlanned = w.status == "PLANNED"
            val dateMillis = w.startedAt ?: w.scheduledDate
            DetailRow(if (isPlanned) "Запланирована на" else "Дата", dateMillis?.toLocaleDateString() ?: "—")

            if (!isPlanned) {
                w.durationSeconds?.let { DetailRow("Длительность", formatTime(it.toLong())) }
                w.distanceMeters?.let { DetailRow("Дистанция", formatDistanceKm(it)) }
                w.elevationGainMeters?.let { DetailRow("Набор высоты", "$it м") }
                w.runDetails?.cadenceSpm?.let { DetailRow("Каденс", "$it шаг/мин") }
                w.bikeDetails?.cadenceRpm?.let { DetailRow("Каденс", "$it об/мин") }
                w.bikeDetails?.powerWatts?.let { DetailRow("Мощность", "$it Вт") }
                w.skiDetails?.style?.let { DetailRow("Стиль", skiStyleLabel(it)) }
            }

            w.notes?.takeIf { it.isNotBlank() }?.let {
                Text("Заметка", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(it, style = MaterialTheme.typography.bodyMedium)
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (w.trackEncoded != null) {
                    OutlinedButton(onClick = { onTrackClick(w.id) }) { Text("Посмотреть трек") }
                }
                OutlinedButton(onClick = { onEditClick(w.id) }) { Text("Редактировать") }
                OutlinedButton(onClick = { showDeleteConfirm = true }) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
