package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.ClubRepository
import com.competra.domain.models.CreateClubRequest
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateClubPage(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val repo: ClubRepository = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val scope = rememberCoroutineScope()

    if (!tokenStorage.isLoggedIn()) {
        LoginPage(onLoginSuccess = onLoginSuccess)
        return
    }

    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var allowJoinRequests by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (name.isBlank()) {
            error = "Укажите название клуба"
            return
        }
        error = null
        saving = true
        scope.launch {
            val request = CreateClubRequest(
                name = name.trim(),
                description = description.trim().ifEmpty { null },
                allowJoinRequests = allowJoinRequests,
            )
            when (val r = repo.createClub(request)) {
                is ApiResult.Success -> onCreated(r.data.id)
                is ApiResult.Error -> { error = r.message; saving = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Новый клуб") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Название *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Описание") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = allowJoinRequests, onCheckedChange = { allowJoinRequests = it })
                Text("Разрешить заявки на вступление")
            }

            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

            Button(onClick = { save() }, enabled = !saving, modifier = Modifier.fillMaxWidth()) {
                Text("Создать")
            }
        }
    }
}
