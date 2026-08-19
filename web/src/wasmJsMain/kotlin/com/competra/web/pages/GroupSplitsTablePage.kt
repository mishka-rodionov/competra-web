package com.competra.web.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.CompetitionRepository
import com.competra.data.repository.DistanceRepository
import com.competra.data.repository.ResultRepository
import com.competra.domain.models.SplitsTable
import com.competra.domain.models.SplitsTableRow
import com.competra.domain.models.buildSplitsTable
import com.competra.domain.models.sortedForResults
import com.competra.web.utils.formatTime
import org.koin.compose.koinInject

private val NAME_COLUMN_WIDTH = 140.dp
private val SPLIT_COLUMN_WIDTH = 76.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupSplitsTablePage(
    competitionId: String,
    groupId: Long,
    groupTitle: String,
    distanceId: Long?,
    onBack: () -> Unit,
) {
    val resultRepo: ResultRepository = koinInject()
    val distanceRepo: DistanceRepository = koinInject()
    val competitionRepo: CompetitionRepository = koinInject()

    var table by remember { mutableStateOf<SplitsTable?>(null) }
    var isByChoice by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(competitionId, groupId) {
        val rParticipants = resultRepo.getParticipants(competitionId)
        val rResults = resultRepo.getResults(competitionId)
        val rDistances = distanceRepo.getByCompetition(competitionId)
        val rDetail = competitionRepo.getCompetitionDetail(competitionId)

        val participants = (rParticipants as? ApiResult.Success)?.data?.filter { it.groupId == groupId } ?: emptyList()
        val results = (rResults as? ApiResult.Success)?.data ?: emptyList()
        val distance = (rDistances as? ApiResult.Success)?.data?.firstOrNull { it.id == distanceId }
        val direction = (rDetail as? ApiResult.Success)?.data?.direction ?: "FORWARD"

        if (rParticipants is ApiResult.Error) error = rParticipants.message
        else if (rResults is ApiResult.Error) error = rResults.message

        isByChoice = direction == "BY_CHOICE"
        val sortedParticipants = sortedForResults(participants, results, direction)
        table = buildSplitsTable(sortedParticipants, results, distance, direction)
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Сплиты: $groupTitle") },
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

        val t = table
        if (t == null || t.rows.isEmpty() || t.columns.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Сплиты по группе отсутствуют", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        val hScroll = rememberScrollState()

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Box(modifier = Modifier.width(NAME_COLUMN_WIDTH))
                Row(modifier = Modifier.horizontalScroll(hScroll)) {
                    t.columns.forEach { column ->
                        Box(modifier = Modifier.width(SPLIT_COLUMN_WIDTH), contentAlignment = Alignment.Center) {
                            Text(
                                if (isByChoice) "#${column.positionIndex}" else "#${column.positionIndex} (КП${column.controlPoint})",
                                style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
            HorizontalDivider()

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                itemsIndexed(t.rows) { index, row ->
                    val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surface
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)

                    Row(modifier = Modifier.fillMaxWidth().background(rowBg).padding(vertical = 6.dp)) {
                        Column(modifier = Modifier.width(NAME_COLUMN_WIDTH).padding(horizontal = 4.dp)) {
                            Text(
                                "${row.participant.lastName} ${row.participant.firstName}".trim(),
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                            )
                            if (isByChoice) {
                                Text(
                                    scoreLabel(row),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            row.result?.rank?.let {
                                Text("Место $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Row(modifier = Modifier.horizontalScroll(hScroll)) {
                            row.cells.forEach { cell ->
                                Column(
                                    modifier = Modifier.width(SPLIT_COLUMN_WIDTH).padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (isByChoice) {
                                        cell.controlPoint?.let {
                                            Text(
                                                "КП$it",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Text(
                                        cell.cumulativeSeconds?.let { formatTime(it) } ?: "—",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Text(
                                        cell.deltaSeconds?.let { formatTime(it) } ?: "—",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (cell.isBestLeg) FontWeight.Bold else FontWeight.Normal,
                                        color = if (cell.isBestLeg) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    )
                                    if (!isByChoice) {
                                        cell.paceMinPerKm?.let {
                                            Text(
                                                "${formatPace(it)}/км",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

private fun scoreLabel(row: SplitsTableRow): String {
    val netScore = row.result?.totalScore ?: 0
    val penalty = row.result?.scorePenalty ?: 0
    val rawScore = row.rawScore ?: (netScore + penalty)
    return if (penalty > 0) "$rawScore - $penalty (штраф) = $netScore" else "$netScore очков"
}

private fun formatPace(minPerKm: Double): String {
    val totalSeconds = (minPerKm * 60).toLong().coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
