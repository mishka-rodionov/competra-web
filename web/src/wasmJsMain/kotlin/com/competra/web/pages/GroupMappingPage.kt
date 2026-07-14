package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import com.competra.data.repository.RatingRepository
import com.competra.domain.models.GroupMappingEntry
import com.competra.domain.models.RatingGroup
import com.competra.domain.models.RatingGroupMappingSuggestion
import com.competra.web.components.LabeledDropdown
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupMappingPage(
    ratingId: String,
    competitionId: String,
    ratingGroups: List<RatingGroup>,
    initialSuggestions: List<RatingGroupMappingSuggestion>?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val repo: RatingRepository = koinInject()
    val scope = rememberCoroutineScope()

    var suggestions by remember { mutableStateOf(initialSuggestions) }
    var loading by remember { mutableStateOf(initialSuggestions == null) }
    var mapping by remember { mutableStateOf<Map<Long, Long?>>(emptyMap()) }
    var error by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }

    LaunchedEffect(competitionId) {
        if (suggestions == null) {
            when (val r = repo.getMappingSuggestions(ratingId, competitionId)) {
                is ApiResult.Success -> suggestions = r.data
                is ApiResult.Error -> error = r.message
            }
            loading = false
        }
        mapping = suggestions?.associate { it.participantGroupId to it.suggestedRatingGroupId } ?: emptyMap()
    }

    val groupOptions: List<Pair<Long?, String>> = listOf(null to "Нет соответствия") +
        ratingGroups.map { it.id to it.title }

    fun save() {
        saving = true
        scope.launch {
            val entries = mapping.mapNotNull { (participantGroupId, ratingGroupId) ->
                ratingGroupId?.let { GroupMappingEntry(participantGroupId, it) }
            }
            when (repo.setGroupMapping(ratingId, competitionId, entries)) {
                is ApiResult.Success -> onSaved()
                is ApiResult.Error -> { error = "Не удалось сохранить маппинг"; saving = false }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Маппинг групп") },
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

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) }

            val items = suggestions.orEmpty()
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text("В соревновании нет групп участников", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(items, key = { it.participantGroupId }) { suggestion ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(suggestion.participantGroupTitle, style = MaterialTheme.typography.bodyMedium)
                            LabeledDropdown(
                                label = "Группа рейтинга",
                                selectedKey = mapping[suggestion.participantGroupId],
                                options = groupOptions,
                                modifier = Modifier.fillMaxWidth(),
                                onSelect = { selected ->
                                    mapping = mapping + (suggestion.participantGroupId to selected)
                                },
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = { save() },
                        enabled = !saving,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    ) { Text("Сохранить") }
                }
            }
        }
    }
}
