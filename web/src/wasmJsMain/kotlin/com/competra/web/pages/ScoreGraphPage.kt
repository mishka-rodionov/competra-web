package com.competra.web.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.CompetitionRepository
import com.competra.data.repository.DistanceRepository
import com.competra.data.repository.ResultRepository
import com.competra.domain.models.ScoreGraphData
import com.competra.domain.models.buildScoreGraphData
import com.competra.domain.models.sortedForResults
import com.competra.web.components.ScoreGraphChart
import com.competra.web.components.raceGraphColor
import org.koin.compose.koinInject

private const val DEFAULT_VISIBLE_COUNT = 10

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScoreGraphPage(
    competitionId: String,
    groupId: Long,
    groupTitle: String,
    distanceId: Long?,
    onBack: () -> Unit,
) {
    val resultRepo: ResultRepository = koinInject()
    val distanceRepo: DistanceRepository = koinInject()
    val competitionRepo: CompetitionRepository = koinInject()

    var data by remember { mutableStateOf<ScoreGraphData?>(null) }
    var loading by remember { mutableStateOf(true) }
    var visibleIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var highlightedId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(competitionId, groupId) {
        val rParticipants = resultRepo.getParticipants(competitionId)
        val rResults = resultRepo.getResults(competitionId)
        val rDistances = distanceRepo.getByCompetition(competitionId)
        val rDetail = competitionRepo.getCompetitionDetail(competitionId)

        val participants = (rParticipants as? ApiResult.Success)?.data?.filter { it.groupId == groupId } ?: emptyList()
        val results = (rResults as? ApiResult.Success)?.data ?: emptyList()
        val distance = (rDistances as? ApiResult.Success)?.data?.firstOrNull { it.id == distanceId }
        val timeLimitMinutes = (rDetail as? ApiResult.Success)?.data?.participantGroups
            ?.firstOrNull { it.groupId == groupId }?.timeLimitMinutes

        val sortedParticipants = sortedForResults(participants, results, "BY_CHOICE")
        val graphData = buildScoreGraphData(sortedParticipants, results, distance, timeLimitMinutes)
        data = graphData
        visibleIds = graphData.series
            .sortedBy { it.result?.rank ?: Int.MAX_VALUE }
            .take(DEFAULT_VISIBLE_COUNT)
            .map { it.participant.id }
            .toSet()
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("График: $groupTitle") },
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

        val d = data
        if (d == null || d.series.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Нет финишировавших участников для графика", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                ScoreGraphChart(
                    data = d,
                    visibleParticipantIds = visibleIds,
                    highlightedParticipantId = highlightedId,
                    modifier = Modifier.padding(16.dp),
                )
            }
            item {
                Text(
                    "Участники",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(d.series) { series ->
                val originalIndex = d.series.indexOf(series)
                val isVisible = series.participant.id in visibleIds
                val isHighlighted = highlightedId == series.participant.id

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            if (isHighlighted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                            else MaterialTheme.colorScheme.surface
                        )
                        .clickable(enabled = isVisible) {
                            highlightedId = if (isHighlighted) null else series.participant.id
                        }
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Checkbox(
                        checked = isVisible,
                        onCheckedChange = { checked ->
                            visibleIds = if (checked) visibleIds + series.participant.id else visibleIds - series.participant.id
                            if (!checked && isHighlighted) highlightedId = null
                        },
                    )
                    Box(
                        modifier = Modifier.size(10.dp).clip(CircleShape).background(raceGraphColor(originalIndex)),
                    )
                    Text(
                        "${series.participant.lastName} ${series.participant.firstName}".trim() +
                            (series.result?.rank?.let { " · Место $it" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isHighlighted) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
