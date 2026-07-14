package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import com.competra.domain.models.ClubJoinRequest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClubJoinRequestsPage(clubId: String, onBack: () -> Unit) {
    val repo: ClubRepository = koinInject()
    val scope = rememberCoroutineScope()

    var requests by remember { mutableStateOf<List<ClubJoinRequest>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        when (val r = repo.getJoinRequestsForClub(clubId)) {
            is ApiResult.Success -> requests = r.data
            is ApiResult.Error -> error = r.message
        }
        loading = false
    }

    LaunchedEffect(clubId) { reload() }

    val pending = requests.filter { it.status == "PENDING" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Заявки на вступление") },
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

        if (pending.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(error ?: "Нет входящих заявок", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(pending, key = { it.id }) { request ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${request.lastName} ${request.firstName}".trim(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = {
                            scope.launch {
                                when (repo.reviewJoinRequest(clubId, request.id, approve = false)) {
                                    is ApiResult.Success -> requests = requests.filter { it.id != request.id }
                                    is ApiResult.Error -> error = "Не удалось отклонить заявку"
                                }
                            }
                        }) { Text("Отклонить") }
                        Button(
                            onClick = {
                                scope.launch {
                                    when (repo.reviewJoinRequest(clubId, request.id, approve = true)) {
                                        is ApiResult.Success -> requests = requests.filter { it.id != request.id }
                                        is ApiResult.Error -> error = "Не удалось одобрить заявку"
                                    }
                                }
                            },
                            modifier = Modifier.padding(start = 8.dp),
                        ) { Text("Одобрить") }
                    }
                }
            }
        }
    }
}
