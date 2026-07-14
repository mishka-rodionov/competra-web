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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.CompetitionRepository
import com.competra.data.repository.RatingRepository
import com.competra.domain.models.Competition
import com.competra.domain.models.RatingGroupMappingSuggestion
import com.competra.web.utils.toLocaleDateString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private const val ADD_COMPETITION_PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCompetitionToRatingPage(
    ratingId: String,
    alreadyAddedCompetitionIds: Set<String>,
    onBack: () -> Unit,
    onAdded: (competitionId: String, suggestions: List<RatingGroupMappingSuggestion>) -> Unit,
) {
    val competitionRepo: CompetitionRepository = koinInject()
    val ratingRepo: RatingRepository = koinInject()
    val scope = rememberCoroutineScope()

    var competitions by remember { mutableStateOf<List<Competition>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var addingId by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    suspend fun loadMore(reset: Boolean) {
        if (!reset && (isLoadingMore || !hasMore)) return
        if (reset) { loading = true; page = 0; hasMore = true } else { isLoadingMore = true }
        when (val r = competitionRepo.getPublicCompetitions(page = if (reset) 0 else page, limit = ADD_COMPETITION_PAGE_SIZE)) {
            is ApiResult.Success -> {
                competitions = if (reset) r.data.items else competitions + r.data.items
                hasMore = r.data.hasMore
                page = (if (reset) 0 else page) + 1
            }
            is ApiResult.Error -> error = r.message
        }
        loading = false
        isLoadingMore = false
    }

    LaunchedEffect(Unit) { loadMore(reset = true) }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, isLoadingMore, loading) {
        if (shouldLoadMore && hasMore && !isLoadingMore && !loading) loadMore(reset = false)
    }

    fun add(competition: Competition) {
        if (addingId != null) return
        addingId = competition.id
        scope.launch {
            when (val r = ratingRepo.addCompetition(ratingId, competition.id)) {
                is ApiResult.Success -> onAdded(competition.id, r.data.groupMappingSuggestions)
                is ApiResult.Error -> { error = "Не удалось добавить соревнование"; addingId = null }
            }
        }
    }

    val availableCompetitions = competitions.filter { it.id !in alreadyAddedCompetitionIds }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Добавить соревнование") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp))
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (availableCompetitions.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Нет доступных соревнований", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(availableCompetitions, key = { it.id }) { competition ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = addingId == null) { add(competition) },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(competition.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    competition.startDate.toLocaleDateString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (addingId == competition.id) {
                                CircularProgressIndicator(modifier = Modifier.width(20.dp))
                            }
                        }
                    }
                }
                item {
                    if (isLoadingMore) {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}
