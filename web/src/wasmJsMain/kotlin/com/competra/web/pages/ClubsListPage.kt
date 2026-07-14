package com.competra.web.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.ClubRepository
import com.competra.domain.models.Club
import kotlinx.coroutines.delay
import org.koin.compose.koinInject

private const val CLUBS_PAGE_SIZE = 20

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubsListPage(
    onClubClick: (String) -> Unit,
    onCreateClick: () -> Unit,
    onMyJoinRequestsClick: () -> Unit,
) {
    val repo: ClubRepository = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val isLoggedIn = tokenStorage.isLoggedIn()

    var query by remember { mutableStateOf("") }
    var clubs by remember { mutableStateOf<List<Club>>(emptyList()) }
    var page by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    suspend fun reload() {
        loading = true
        error = null
        page = 0
        hasMore = true
        when (val r = repo.searchClubs(query = query, page = 0, limit = CLUBS_PAGE_SIZE)) {
            is ApiResult.Success -> {
                clubs = r.data.items
                hasMore = r.data.hasMore
                page = 1
            }
            is ApiResult.Error -> error = r.message
        }
        loading = false
    }

    suspend fun loadMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        when (val r = repo.searchClubs(query = query, page = page, limit = CLUBS_PAGE_SIZE)) {
            is ApiResult.Success -> {
                clubs = clubs + r.data.items
                hasMore = r.data.hasMore
                page += 1
            }
            is ApiResult.Error -> {}
        }
        isLoadingMore = false
    }

    LaunchedEffect(query) {
        delay(300)
        reload()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = listState.layoutInfo
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            total > 0 && lastVisible >= total - 3
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, isLoadingMore, loading) {
        if (shouldLoadMore && hasMore && !isLoadingMore && !loading) loadMore()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Клубы") },
                actions = {
                    if (isLoggedIn) {
                        TextButton(onClick = onMyJoinRequestsClick) { Text("Мои заявки") }
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Поиск клуба") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                Button(onClick = onCreateClick) { Text("+ Создать") }
            }

            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp))
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            if (clubs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("Клубы не найдены", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(clubs, key = { it.id }) { club ->
                    ClubCard(club = club, onClick = { onClubClick(club.id) })
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

@Composable
private fun ClubCard(club: Club, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(club.name, style = MaterialTheme.typography.titleSmall)
            club.description?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Text(
                "Участников: ${club.membersCount}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
