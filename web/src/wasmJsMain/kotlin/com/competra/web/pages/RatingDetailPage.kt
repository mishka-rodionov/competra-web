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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
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
import com.competra.data.repository.ClubRepository
import com.competra.data.repository.RatingRepository
import com.competra.data.repository.UserRepository
import com.competra.domain.models.Rating
import com.competra.domain.models.RatingCompetition
import com.competra.domain.models.RatingGroup
import com.competra.domain.models.RatingStanding
import com.competra.web.utils.toLocaleDateString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingDetailPage(
    ratingId: String,
    onBack: () -> Unit,
    onAddCompetitionClick: (ratingId: String, alreadyAddedIds: Set<String>, ratingGroups: List<RatingGroup>) -> Unit,
    onEditClick: (clubId: String, rating: Rating) -> Unit,
    onMappingClick: (String, String, List<RatingGroup>) -> Unit,
) {
    val ratingRepo: RatingRepository = koinInject()
    val clubRepo: ClubRepository = koinInject()
    val userRepo: UserRepository = koinInject()
    val scope = rememberCoroutineScope()

    var rating by remember { mutableStateOf<Rating?>(null) }
    var competitions by remember { mutableStateOf<List<RatingCompetition>>(emptyList()) }
    var isAdmin by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedGroupId by remember { mutableStateOf<Long?>(null) }
    var standingsByGroup by remember { mutableStateOf<Map<Long, List<RatingStanding>>>(emptyMap()) }
    var standingsLoading by remember { mutableStateOf(false) }
    var actionError by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var reloadKey by remember { mutableStateOf(0) }

    suspend fun reload() {
        loading = true
        when (val r = ratingRepo.getRating(ratingId)) {
            is ApiResult.Success -> {
                rating = r.data
                if (selectedGroupId == null) selectedGroupId = r.data.groups.firstOrNull()?.id
                when (val members = clubRepo.getClubMembers(r.data.ownerClubId)) {
                    is ApiResult.Success -> {
                        when (val profile = userRepo.getUserProfile()) {
                            is ApiResult.Success -> {
                                val myRole = members.data.firstOrNull { it.userId == profile.data.id }?.role
                                isAdmin = myRole in listOf("FOUNDER", "ADMIN")
                            }
                            is ApiResult.Error -> isAdmin = false
                        }
                    }
                    is ApiResult.Error -> isAdmin = false
                }
            }
            is ApiResult.Error -> error = r.message
        }
        when (val r = ratingRepo.getRatingCompetitions(ratingId)) {
            is ApiResult.Success -> competitions = r.data
            is ApiResult.Error -> {}
        }
        standingsByGroup = emptyMap()
        loading = false
    }

    suspend fun loadStandings(groupId: Long) {
        if (standingsByGroup.containsKey(groupId)) return
        standingsLoading = true
        when (val r = ratingRepo.getStandings(ratingId, groupId)) {
            is ApiResult.Success -> standingsByGroup = standingsByGroup + (groupId to r.data.standings)
            is ApiResult.Error -> {}
        }
        standingsLoading = false
    }

    LaunchedEffect(ratingId, reloadKey) { reload() }
    LaunchedEffect(selectedGroupId) { selectedGroupId?.let { loadStandings(it) } }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить рейтинг?") },
            text = { Text("Действие необратимо.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        when (ratingRepo.deleteRating(ratingId)) {
                            is ApiResult.Success -> onBack()
                            is ApiResult.Error -> actionError = "Не удалось удалить рейтинг"
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
                title = { Text(rating?.name ?: "Рейтинг", maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (isAdmin) {
                        val r = rating
                        if (r != null) {
                            TextButton(onClick = {
                                onAddCompetitionClick(r.id, competitions.map { it.competitionId }.toSet(), r.groups)
                            }) { Text("Добавить старт") }
                            TextButton(onClick = { onEditClick(r.ownerClubId, r) }) { Text("Редактировать") }
                            TextButton(onClick = { showDeleteConfirm = true }) {
                                Text("Удалить", color = MaterialTheme.colorScheme.error)
                            }
                        }
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

        val r = rating
        if (r == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(error ?: "Рейтинг не найден")
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            actionError?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            if (r.groups.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("В рейтинге нет групп зачёта", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                ScrollableTabRow(selectedTabIndex = r.groups.indexOfFirst { it.id == selectedGroupId }.coerceAtLeast(0)) {
                    r.groups.forEach { group ->
                        Tab(
                            selected = group.id == selectedGroupId,
                            onClick = { selectedGroupId = group.id },
                            text = { Text(group.title) },
                        )
                    }
                }

                if (standingsLoading && standingsByGroup[selectedGroupId] == null) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    val standings = selectedGroupId?.let { standingsByGroup[it] } ?: emptyList()
                    if (standings.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                            Text("Пока нет данных", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            standings.forEach { standing ->
                                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${standing.rank}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.padding(end = 12.dp),
                                    )
                                    Text(standing.displayName, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Text("${standing.totalPoints}", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }

            Text(
                "Соревнования рейтинга",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (competitions.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Соревнований пока нет", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(competitions, key = { it.id }) { rc ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(rc.competitionTitle, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        rc.competitionStartDate.toLocaleDateString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                                if (isAdmin) {
                                    IconButton(onClick = { onMappingClick(ratingId, rc.competitionId, r.groups) }) {
                                        Icon(Icons.Filled.Edit, contentDescription = "Маппинг групп")
                                    }
                                    IconButton(onClick = {
                                        scope.launch {
                                            when (ratingRepo.removeCompetition(ratingId, rc.competitionId)) {
                                                is ApiResult.Success -> reloadKey++
                                                is ApiResult.Error -> actionError = "Не удалось удалить соревнование"
                                            }
                                        }
                                    }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Удалить")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
