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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.ClubRepository
import com.competra.domain.models.ClubJoinRequest
import org.koin.compose.koinInject

private fun joinRequestStatusLabel(status: String) = when (status) {
    "PENDING" -> "На рассмотрении"
    "APPROVED" -> "Одобрена"
    "REJECTED" -> "Отклонена"
    else -> status
}

@Composable
private fun joinRequestStatusColor(status: String) = when (status) {
    "APPROVED" -> MaterialTheme.colorScheme.primary
    "REJECTED" -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyJoinRequestsPage(onBack: () -> Unit, onClubClick: (String) -> Unit) {
    val repo: ClubRepository = koinInject()

    var requests by remember { mutableStateOf<List<ClubJoinRequest>>(emptyList()) }
    var clubNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        when (val r = repo.getMyJoinRequests()) {
            is ApiResult.Success -> {
                requests = r.data.sortedByDescending { it.createdAt }
                val names = mutableMapOf<String, String>()
                requests.map { it.clubId }.distinct().forEach { clubId ->
                    when (val cr = repo.getClub(clubId)) {
                        is ApiResult.Success -> names[clubId] = cr.data.name
                        is ApiResult.Error -> {}
                    }
                }
                clubNames = names
            }
            is ApiResult.Error -> error = r.message
        }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Мои заявки") },
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

        if (requests.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(error ?: "Вы ещё не подавали заявок", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(requests, key = { it.id }) { request ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onClubClick(request.clubId) },
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                clubNames[request.clubId] ?: "Клуб",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            joinRequestStatusLabel(request.status),
                            style = MaterialTheme.typography.labelMedium,
                            color = joinRequestStatusColor(request.status),
                        )
                    }
                }
            }
        }
    }
}
