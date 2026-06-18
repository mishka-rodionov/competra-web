package com.competra.web.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.CompetitionRepository
import com.competra.domain.models.Competition
import com.competra.domain.models.OrienteeringCompetition
import com.competra.web.utils.toLocaleDateString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private data class EventsFilter(
    val kindOfSports: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
) {
    val isEmpty: Boolean get() = kindOfSports.isEmpty() && statuses.isEmpty()
}

private val SPORT_TYPES = listOf(
    "Orienteering" to "Ориентирование",
    "CrossCountrySki" to "Лыжное ориентирование",
    "TrailRunning" to "Трейловый бег",
)

private val STATUSES = listOf(
    "REGISTRATION_OPEN" to "Регистрация открыта",
    "REGISTRATION_CLOSED" to "Регистрация закрыта",
    "IN_PROGRESS" to "Идёт",
    "FINISHED" to "Завершено",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionsPage(
    modifier: Modifier = Modifier,
    onCompetitionClick: (String) -> Unit,
    onCreateClick: () -> Unit,
) {
    val repo: CompetitionRepository = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val isLoggedIn = tokenStorage.isLoggedIn()
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var publicList by remember { mutableStateOf<List<Competition>>(emptyList()) }
    var myList by remember { mutableStateOf<List<OrienteeringCompetition>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableStateOf(EventsFilter()) }
    var showFilter by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    fun loadPublic(f: EventsFilter) {
        scope.launch {
            loading = true
            error = null
            when (val r = repo.getPublicCompetitions(
                kindOfSports = f.kindOfSports.toList(),
                statuses = f.statuses.toList(),
            )) {
                is ApiResult.Success -> publicList = r.data
                is ApiResult.Error -> error = r.message
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        loadPublic(filter)
        if (isLoggedIn) {
            when (val r = repo.getMyCompetitions()) {
                is ApiResult.Success -> myList = r.data
                is ApiResult.Error -> println("My competitions error: ${r.message}")
            }
        }
    }

    if (showFilter) {
        var draftFilter by remember { mutableStateOf(filter) }
        ModalBottomSheet(
            onDismissRequest = { showFilter = false },
            sheetState = sheetState,
        ) {
            Column(modifier = Modifier.padding(16.dp).padding(bottom = 32.dp)) {
                Text("Фильтры", style = MaterialTheme.typography.titleLarge)

                Text(
                    "Вид спорта",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                SPORT_TYPES.forEach { (key, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            draftFilter = if (key in draftFilter.kindOfSports)
                                draftFilter.copy(kindOfSports = draftFilter.kindOfSports - key)
                            else
                                draftFilter.copy(kindOfSports = draftFilter.kindOfSports + key)
                        },
                    ) {
                        Checkbox(
                            checked = key in draftFilter.kindOfSports,
                            onCheckedChange = { checked ->
                                draftFilter = if (checked)
                                    draftFilter.copy(kindOfSports = draftFilter.kindOfSports + key)
                                else
                                    draftFilter.copy(kindOfSports = draftFilter.kindOfSports - key)
                            },
                        )
                        Text(label)
                    }
                }

                Text(
                    "Статус",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                )
                STATUSES.forEach { (key, label) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            draftFilter = if (key in draftFilter.statuses)
                                draftFilter.copy(statuses = draftFilter.statuses - key)
                            else
                                draftFilter.copy(statuses = draftFilter.statuses + key)
                        },
                    ) {
                        Checkbox(
                            checked = key in draftFilter.statuses,
                            onCheckedChange = { checked ->
                                draftFilter = if (checked)
                                    draftFilter.copy(statuses = draftFilter.statuses + key)
                                else
                                    draftFilter.copy(statuses = draftFilter.statuses - key)
                            },
                        )
                        Text(label)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = {
                            draftFilter = EventsFilter()
                            filter = EventsFilter()
                            showFilter = false
                            loadPublic(EventsFilter())
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Сбросить")
                    }
                    Button(
                        onClick = {
                            filter = draftFilter
                            showFilter = false
                            loadPublic(draftFilter)
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Применить")
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Соревнования") },
                actions = {
                    if (selectedTab == 0) {
                        TextButton(onClick = { showFilter = true }) {
                            Text(if (filter.isEmpty) "Фильтр" else "Фильтр ●")
                        }
                    }
                    if (isLoggedIn) {
                        TextButton(onClick = onCreateClick) {
                            Text("+ Создать")
                        }
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Публичные") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Мои") })
            }
            error?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (loading && selectedTab == 0) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else if (selectedTab == 1 && !isLoggedIn) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Войдите в аккаунт, чтобы увидеть свои соревнования",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else if (selectedTab == 0) {
                if (publicList.isEmpty() && !loading) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Нет соревнований",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(publicList) { competition ->
                            PublicCompetitionCard(
                                competition = competition,
                                onClick = { competition.remoteId?.let { onCompetitionClick(it.toString()) } },
                            )
                        }
                    }
                }
            } else {
                if (myList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "Нет соревнований",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(myList) { competition ->
                            MyCompetitionCard(
                                competition = competition,
                                onClick = { onCompetitionClick(competition.competitionId) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PublicCompetitionCard(competition: Competition, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    competition.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    statusLabel(competition.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(competition.status),
                )
            }
            Text(
                competition.startDate.toLocaleDateString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            competition.address?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MyCompetitionCard(competition: OrienteeringCompetition, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    competition.competition.title,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    statusLabel(competition.competition.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(competition.competition.status),
                )
            }
            Text(
                competition.competition.startDate.toLocaleDateString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
            competition.competition.address?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
internal fun statusColor(status: String) = when (status) {
    "REGISTRATION_OPEN" -> MaterialTheme.colorScheme.primary
    "REGISTRATION_CLOSED" -> MaterialTheme.colorScheme.secondary
    "IN_PROGRESS", "STARTED" -> MaterialTheme.colorScheme.tertiary
    "FINISHED" -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

internal fun statusLabel(status: String) = when (status) {
    "REGISTRATION_OPEN" -> "Регистрация открыта"
    "REGISTRATION_CLOSED" -> "Регистрация закрыта"
    "IN_PROGRESS", "STARTED" -> "Идёт"
    "FINISHED" -> "Завершено"
    "CREATED", "DRAFT", "ANNOUNCED" -> "Черновик"
    else -> status
}
