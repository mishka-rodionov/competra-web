package com.competra.web.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.api.ApiResult
import com.competra.data.repository.CompetitionRepository
import com.competra.domain.models.OrienteeringCompetition
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionsPage(
    modifier: Modifier = Modifier,
    onCompetitionClick: (Long) -> Unit,
) {
    val repo: CompetitionRepository = koinInject()
    var selectedTab by remember { mutableIntStateOf(0) }
    var publicList by remember { mutableStateOf<List<OrienteeringCompetition>>(emptyList()) }
    var myList by remember { mutableStateOf<List<OrienteeringCompetition>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        when (val r = repo.getPublicCompetitions()) {
            is ApiResult.Success -> publicList = r.data
            else -> {}
        }
        when (val r = repo.getMyCompetitions()) {
            is ApiResult.Success -> myList = r.data
            else -> {}
        }
        loading = false
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text("Соревнования") }) }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Публичные") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Мои") })
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.padding(16.dp))
            } else {
                val items = if (selectedTab == 0) publicList else myList
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items) { competition ->
                        CompetitionCard(
                            competition = competition,
                            onClick = { onCompetitionClick(competition.competitionId) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompetitionCard(competition: OrienteeringCompetition, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(competition.competition.title, style = MaterialTheme.typography.titleMedium)
                competition.competition.address?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(
                competition.competition.status,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
