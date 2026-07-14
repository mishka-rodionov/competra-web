package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.RatingRepository
import com.competra.domain.models.CreateRatingRequest
import com.competra.domain.models.Rating
import com.competra.domain.models.RatingGroupRequest
import com.competra.domain.models.UpdateRatingRequest
import com.competra.web.components.LabeledDropdown
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val GENDER_OPTIONS: List<Pair<String?, String>> = listOf(
    null to "Любой",
    "MALE" to "Мужчины",
    "FEMALE" to "Женщины",
    "MIXED" to "Смешанная",
)

private data class RatingGroupDraft(
    val localId: Int,
    val id: Long? = null,
    val title: String = "",
    val gender: String? = null,
    val minAge: String = "",
    val maxAge: String = "",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RatingFormPage(
    clubId: String,
    existingRating: Rating?,
    onBack: () -> Unit,
    onSaved: (Rating) -> Unit,
    onLoginSuccess: () -> Unit,
) {
    val repo: RatingRepository = koinInject()
    val tokenStorage: TokenStorage = koinInject()
    val scope = rememberCoroutineScope()

    if (!tokenStorage.isLoggedIn()) {
        LoginPage(onLoginSuccess = onLoginSuccess)
        return
    }

    var name by remember { mutableStateOf(existingRating?.name ?: "") }
    var nextLocalId by remember { mutableStateOf(existingRating?.groups?.size ?: 0) }
    var groups by remember {
        mutableStateOf<List<RatingGroupDraft>>(
            existingRating?.groups?.mapIndexed { index, g ->
                RatingGroupDraft(
                    localId = index,
                    id = g.id,
                    title = g.title,
                    gender = g.gender,
                    minAge = g.minAge?.toString() ?: "",
                    maxAge = g.maxAge?.toString() ?: "",
                )
            } ?: emptyList()
        )
    }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    fun save() {
        if (name.isBlank()) { error = "Укажите название рейтинга"; return }
        if (groups.isEmpty() || groups.any { it.title.isBlank() }) {
            error = "Добавьте хотя бы одну группу и заполните её название"
            return
        }
        error = null
        saving = true
        scope.launch {
            val request = CreateRatingRequest(
                name = name.trim(),
                groups = groups.mapIndexed { index, g ->
                    RatingGroupRequest(
                        id = g.id,
                        title = g.title.trim(),
                        gender = g.gender,
                        minAge = g.minAge.toIntOrNull(),
                        maxAge = g.maxAge.toIntOrNull(),
                        orderIndex = index,
                    )
                },
            )
            val result = if (existingRating == null) {
                repo.createRating(clubId, request)
            } else {
                repo.updateRating(existingRating.id, UpdateRatingRequest(request.name, request.groups))
            }
            when (result) {
                is ApiResult.Success -> onSaved(result.data)
                is ApiResult.Error -> { error = result.message; saving = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (existingRating == null) "Новый рейтинг" else "Редактирование рейтинга") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название *") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                Text("Группы зачёта", style = MaterialTheme.typography.titleSmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(groups, key = { it.localId }) { draft ->
                    RatingGroupEditor(
                        draft = draft,
                        onChange = { updated -> groups = groups.map { if (it.localId == draft.localId) updated else it } },
                        onRemove = { groups = groups.filter { it.localId != draft.localId } },
                    )
                }
                item {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        OutlinedButton(onClick = {
                            groups = groups + RatingGroupDraft(localId = nextLocalId)
                            nextLocalId += 1
                        }) { Text("+ Добавить группу") }
                    }
                }
                item {
                    Button(onClick = { save() }, enabled = !saving, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RatingGroupEditor(
    draft: RatingGroupDraft,
    onChange: (RatingGroupDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = draft.title,
                    onValueChange = { onChange(draft.copy(title = it)) },
                    label = { Text("Название группы") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                TextButton(onClick = onRemove) { Text("Удалить") }
            }
            LabeledDropdown(
                label = "Пол",
                selectedKey = draft.gender,
                options = GENDER_OPTIONS,
                modifier = Modifier.fillMaxWidth(),
                onSelect = { onChange(draft.copy(gender = it)) },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = draft.minAge,
                    onValueChange = { v -> onChange(draft.copy(minAge = v.filter { it.isDigit() })) },
                    label = { Text("Мин. возраст") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = draft.maxAge,
                    onValueChange = { v -> onChange(draft.copy(maxAge = v.filter { it.isDigit() })) },
                    label = { Text("Макс. возраст") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        }
    }
}
