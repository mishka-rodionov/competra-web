package com.competra.web.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.ResultRepository
import com.competra.domain.models.OrienteeringCompetition
import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.OrienteeringResult
import com.competra.web.utils.ImportResultRow
import com.competra.web.utils.ImportResultsDiff
import com.competra.web.utils.formatTime
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Значения одной строки результата для отображения — общая форма для текущего результата и для изменения из HTML. */
private data class DisplayResult(
    val rank: Int?,
    val totalTime: Long?,
    val status: String,
    val totalScore: Int?,
)

private data class ReviewRow(
    val participant: OrienteeringParticipant,
    val display: DisplayResult?,
    val change: ImportResultRow?,
)

private fun buildRows(
    participants: List<OrienteeringParticipant>,
    currentResults: List<OrienteeringResult>,
    changed: List<ImportResultRow>,
    checkedById: Map<String, Boolean>,
    useImported: Boolean,
): List<ReviewRow> {
    val participantsById = participants.associateBy { it.id }
    val currentByParticipantId = currentResults.associateBy { it.participantId }
    val changeByParticipantId = changed.associateBy { it.participant.id }
    val participantIds = currentByParticipantId.keys + changeByParticipantId.keys

    return participantIds.mapNotNull { pid ->
        val participant = participantsById[pid] ?: changeByParticipantId[pid]?.participant ?: return@mapNotNull null
        val change = changeByParticipantId[pid]
        val current = currentByParticipantId[pid]
        val display = if (useImported && change != null && checkedById[change.request.id] == true) {
            DisplayResult(change.request.rank, change.request.totalTime, change.request.status, current?.totalScore)
        } else {
            current?.let { DisplayResult(it.rank, it.totalTime, it.status, it.totalScore) }
        }
        ReviewRow(participant, display, change)
    }
}

private fun groupRows(rows: List<ReviewRow>): List<Pair<String, List<ReviewRow>>> =
    rows.groupBy { it.participant.groupName?.takeIf { name -> name.isNotBlank() } ?: "Без группы" }
        .toList()
        .sortedBy { it.first }
        .map { (title, groupRows) ->
            title to groupRows.sortedWith(compareBy(nullsLast()) { it.display?.rank })
        }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportResultsReviewPage(
    competition: OrienteeringCompetition,
    participants: List<OrienteeringParticipant>,
    currentResults: List<OrienteeringResult>,
    diff: ImportResultsDiff,
    onBack: () -> Unit,
) {
    val repo: ResultRepository = koinInject()
    val scope = rememberCoroutineScope()
    val isByChoice = competition.direction == "BY_CHOICE"
    val resultsStatus = competition.competition.resultsStatus

    val checkedById = remember(diff) {
        mutableStateMapOf<String, Boolean>().apply { diff.changed.forEach { this[it.request.id] = true } }
    }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showUnmatched by remember { mutableStateOf(false) }
    var showConfirm by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val selectedCount = checkedById.values.count { it }
    val rows = groupRows(
        buildRows(participants, currentResults, diff.changed, checkedById, useImported = selectedTab == 1)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Импорт результатов: ${competition.competition.title}", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 4.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            enabled = diff.changed.isNotEmpty() && !importing,
                            onClick = { diff.changed.forEach { checkedById[it.request.id] = true } },
                        ) { Text("Выбрать все") }
                        TextButton(
                            enabled = diff.changed.isNotEmpty() && !importing,
                            onClick = { diff.changed.forEach { checkedById[it.request.id] = false } },
                        ) { Text("Снять все") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onBack, enabled = !importing) { Text("Отмена") }
                        Button(enabled = selectedCount > 0 && !importing, onClick = { showConfirm = true }) {
                            if (importing) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp))
                            } else {
                                Text("Импортировать ($selectedCount)")
                            }
                        }
                    }
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    "Изменений: ${diff.changed.size} • Не распознано: ${diff.unmatched.size}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (diff.unmatched.isNotEmpty()) {
                    TextButton(onClick = { showUnmatched = !showUnmatched }) {
                        Text(if (showUnmatched) "Скрыть нераспознанные" else "Показать нераспознанные (${diff.unmatched.size})")
                    }
                    if (showUnmatched) {
                        Text(
                            "Стартовый номер не найден среди участников — эти строки будут пропущены:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        diff.unmatched.forEach { row ->
                            Text(
                                "№${row.startNumber} ${row.fullName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
                if (resultsStatus == "OFFICIAL") {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text(
                            "Результаты уже опубликованы как официальные — импорт перезапишет их для всех участников.",
                            modifier = Modifier.padding(8.dp),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp))
                }
            }

            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("На сервере") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("После импорта") })
            }

            LazyColumn(modifier = Modifier.fillMaxSize().weight(1f).padding(horizontal = 16.dp)) {
                if (rows.isEmpty()) {
                    item {
                        Text(
                            "Нет данных для отображения",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                rows.forEach { (groupTitle, groupRows) ->
                    item(key = "header-$groupTitle") {
                        Text(
                            groupTitle,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                        HorizontalDivider()
                    }
                    items(groupRows, key = { it.participant.id }) { row ->
                        ReviewResultRow(
                            row = row,
                            isByChoice = isByChoice,
                            showCheckbox = selectedTab == 1,
                            checked = row.change?.let { checkedById[it.request.id] == true } ?: false,
                            onCheckedChange = { checked -> row.change?.let { checkedById[it.request.id] = checked } },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { if (!importing) showConfirm = false },
            title = { Text("Применить изменения?") },
            text = {
                Text("Будет применено $selectedCount изменений. Текущие результаты будут перезаписаны без проверки конфликтов.")
            },
            confirmButton = {
                Button(
                    enabled = !importing,
                    onClick = {
                        scope.launch {
                            importing = true
                            error = null
                            val toSave = diff.changed.filter { checkedById[it.request.id] == true }.map { it.request }
                            when (val r = repo.saveResults(toSave)) {
                                is ApiResult.Success -> {
                                    importing = false
                                    showConfirm = false
                                    onBack()
                                }
                                is ApiResult.Error -> {
                                    importing = false
                                    showConfirm = false
                                    error = r.message
                                }
                            }
                        }
                    },
                ) {
                    if (importing) CircularProgressIndicator(modifier = Modifier.size(18.dp)) else Text("Импортировать")
                }
            },
            dismissButton = {
                TextButton(enabled = !importing, onClick = { showConfirm = false }) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun ReviewResultRow(
    row: ReviewRow,
    isByChoice: Boolean,
    showCheckbox: Boolean,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val display = row.display
    val change = row.change
    val isGroupMismatch = change?.changeSummary?.startsWith("⚠") == true
    val highlighted = showCheckbox && checked && change != null
    val background = when {
        highlighted && isGroupMismatch -> MaterialTheme.colorScheme.errorContainer
        highlighted -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surface
    }

    Column(modifier = Modifier.fillMaxWidth().background(background).padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.width(40.dp)) {
                if (showCheckbox && change != null) {
                    Checkbox(checked = checked, onCheckedChange = onCheckedChange)
                }
            }
            Text(
                display?.rank?.toString() ?: "—",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(0.4f),
            )
            Column(modifier = Modifier.weight(if (isByChoice) 1.6f else 2f)) {
                Text("${row.participant.lastName} ${row.participant.firstName}", style = MaterialTheme.typography.bodyMedium)
                row.participant.startNumber?.let {
                    Text("№$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (isByChoice) {
                Text(
                    display?.totalScore?.toString() ?: "—",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(0.7f),
                )
            }
            Text(
                display?.totalTime?.let { formatTime(it) } ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Text(
                display?.status?.let { resultStatusLabel(it) } ?: "—",
                style = MaterialTheme.typography.labelSmall,
                color = display?.status?.let { resultStatusColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
        if (highlighted) {
            Text(
                change.changeSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 48.dp),
            )
        }
    }
}
