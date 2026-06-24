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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import com.competra.data.api.HTTP_UNAUTHORIZED
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.CompetitionRepository
import com.competra.domain.models.OrienteeringCompetition
import com.competra.web.utils.toLocaleDateString
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManagementPage(
    modifier: Modifier = Modifier,
    onCreateClick: () -> Unit,
    onManageClick: (OrienteeringCompetition) -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val tokenStorage: TokenStorage = koinInject()
    val repo: CompetitionRepository = koinInject()
    val scope = rememberCoroutineScope()
    val isLoggedIn = tokenStorage.isLoggedIn()

    var competitions by remember { mutableStateOf<List<OrienteeringCompetition>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var showLogin by remember { mutableStateOf(false) }

    // Соревнование, для которого открыт диалог подтверждения удаления (null — диалог скрыт).
    var deletingCompetition by remember { mutableStateOf<OrienteeringCompetition?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) { loading = false; return@LaunchedEffect }
        when (val r = repo.getMyCompetitions()) {
            is ApiResult.Success -> competitions = r.data.sortedByDescending { it.competition.startDate }
            is ApiResult.Error ->
                // 401: токен уже сброшен в auth-клиенте — отправляем пользователя на вход.
                if (r.code == HTTP_UNAUTHORIZED) showLogin = true else error = r.message
        }
        loading = false
    }

    if (showLogin) {
        LoginPage(onLoginSuccess = { showLogin = false; onLoginSuccess() })
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Управление") },
                actions = {
                    if (isLoggedIn) {
                        Button(
                            onClick = onCreateClick,
                            modifier = Modifier.padding(end = 8.dp),
                        ) {
                            Text("+ Создать")
                        }
                    }
                },
            )
        }
    ) { padding ->
        if (!isLoggedIn) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Только для организаторов", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Войдите, чтобы создавать соревнования и управлять ими",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = { showLogin = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Войти")
                    }
                }
            }
            return@Scaffold
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        error?.let {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp), contentAlignment = Alignment.Center) {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            return@Scaffold
        }

        if (competitions.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("Нет соревнований", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Создайте первое соревнование",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = onCreateClick) { Text("Создать") }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(competitions) { comp ->
                ManagedCompetitionCard(
                    competition = comp,
                    onManage = { onManageClick(comp) },
                    onDelete = { deleteError = null; deletingCompetition = comp },
                )
            }
        }
    }

    deletingCompetition?.let { target ->
        AlertDialog(
            onDismissRequest = { if (!deleting) deletingCompetition = null },
            title = { Text("Удалить соревнование?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("«${target.competition.title}» будет удалено безвозвратно.")
                    deleteError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !deleting,
                    onClick = {
                        deleting = true
                        deleteError = null
                        scope.launch {
                            when (val r = repo.deleteCompetition(target.competitionId)) {
                                is ApiResult.Success -> {
                                    competitions = competitions.filter { it.competitionId != target.competitionId }
                                    deletingCompetition = null
                                }
                                is ApiResult.Error ->
                                    if (r.code == HTTP_UNAUTHORIZED) {
                                        deletingCompetition = null
                                        showLogin = true
                                    } else {
                                        deleteError = r.message
                                    }
                            }
                            deleting = false
                        }
                    },
                ) {
                    Text(if (deleting) "Удаление…" else "Удалить")
                }
            },
            dismissButton = {
                TextButton(enabled = !deleting, onClick = { deletingCompetition = null }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun ManagedCompetitionCard(
    competition: OrienteeringCompetition,
    onManage: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = competition.competition
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(c.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    statusLabel(c.status),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor(c.status),
                )
            }
            Text(
                c.startDate.toLocaleDateString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            c.address?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onManage) {
                    Text("Управлять")
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Удалить соревнование",
                        tint = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}
