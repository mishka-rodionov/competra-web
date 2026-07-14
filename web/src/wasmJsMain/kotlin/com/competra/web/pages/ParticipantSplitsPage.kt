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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import com.competra.data.repository.ResultRepository
import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.OrienteeringResult
import com.competra.web.utils.formatTime
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParticipantSplitsPage(
    competitionId: String,
    participantId: String,
    onBack: () -> Unit,
) {
    val repo: ResultRepository = koinInject()

    var participant by remember { mutableStateOf<OrienteeringParticipant?>(null) }
    var result by remember { mutableStateOf<OrienteeringResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(competitionId, participantId) {
        val rParticipants = repo.getParticipants(competitionId)
        val rResults = repo.getResults(competitionId)
        when (rParticipants) {
            is ApiResult.Success -> participant = rParticipants.data.firstOrNull { it.id == participantId }
            is ApiResult.Error -> error = rParticipants.message
        }
        when (rResults) {
            is ApiResult.Success -> result = rResults.data.firstOrNull { it.participantId == participantId }
            is ApiResult.Error -> {}
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сплиты участника") },
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

        val p = participant
        if (p == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Участник не найден")
            }
            return@Scaffold
        }

        val r = result
        val splits = r?.splits ?: emptyList()
        val startTs = r?.startTime ?: p.startTime

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${p.lastName} ${p.firstName}".trim(), style = MaterialTheme.typography.titleMedium)
                        p.startNumber?.let {
                            Text("№$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        p.groupName?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            if (r != null) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            SummaryColumn("Место", r.rank?.toString() ?: "—")
                            SummaryColumn("Общее время", r.totalTime?.let { formatTime(it) } ?: "—")
                            SummaryColumn("Статус", resultStatusLabel(r.status))
                        }
                    }
                }
            }

            item {
                Text(
                    "Сплиты по пунктам",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                HorizontalDivider()
            }

            if (splits.isEmpty() || startTs == null) {
                item {
                    Text(
                        "Сплиты отсутствуют",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            } else {
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text("КП", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.6f))
                        Text("Круг", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Text("Время", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider()
                }
                items(splits.indices.toList()) { i ->
                    val split = splits[i]
                    val prevTs = if (i == 0) startTs else splits[i - 1].timestamp
                    val legSeconds = (split.timestamp - prevTs) / 1000L
                    val cumulSeconds = (split.timestamp - startTs) / 1000L

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(split.controlPoint.toString(), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.6f))
                        Text(formatTime(legSeconds), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text(formatTime(cumulSeconds), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun SummaryColumn(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}
