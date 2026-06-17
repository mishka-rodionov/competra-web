package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.DistanceRepository
import com.competra.domain.models.Distance
import com.competra.web.components.XmlImportField
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@Composable
fun DistancesTab(competitionId: Long) {
    val repo: DistanceRepository = koinInject()
    val scope = rememberCoroutineScope()

    var distances by remember { mutableStateOf<List<Distance>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(competitionId) {
        when (val r = repo.getByCompetition(competitionId)) {
            is ApiResult.Success -> distances = r.data
            is ApiResult.Error   -> error = r.message
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Дистанции", style = MaterialTheme.typography.titleMedium)

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
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }

        if (loading) {
            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
        } else if (distances.isEmpty()) {
            Text(
                "Нет дистанций. Импортируйте из Mapper или добавьте вручную.",
                modifier = Modifier.padding(top = 16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(distances) { DistanceCard(it) }
            }
        }
    }
}

@Composable
private fun DistanceCard(distance: Distance) {
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
