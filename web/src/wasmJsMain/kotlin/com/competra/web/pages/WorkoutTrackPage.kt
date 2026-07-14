package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.DiaryRepository
import com.competra.domain.diary.TrackCodec
import com.competra.domain.models.Workout
import com.competra.web.components.TrackMapView
import com.competra.web.utils.formatTime
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTrackPage(
    workoutId: Long,
    onBack: () -> Unit,
) {
    val repo: DiaryRepository = koinInject()

    var workout by remember { mutableStateOf<Workout?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Трек тренировки") },
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

        val startedAtMs = w.startedAt ?: w.scheduledDate ?: 0L
        val points = remember(w.trackEncoded, startedAtMs) { TrackCodec.decode(startedAtMs, w.trackEncoded) }

        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            TrackMapView(points = points, modifier = Modifier.fillMaxSize())

            Card(modifier = Modifier.fillMaxWidth().padding(16.dp).align(Alignment.TopCenter)) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val parts = listOfNotNull(
                        w.distanceMeters?.let { formatDistanceKm(it) },
                        w.durationSeconds?.let { formatTime(it.toLong()) },
                        w.elevationGainMeters?.let { "+$it м" },
                    )
                    Text(
                        if (parts.isNotEmpty()) parts.joinToString(" · ") else "Нет данных",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
