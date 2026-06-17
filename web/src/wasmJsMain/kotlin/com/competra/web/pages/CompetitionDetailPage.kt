package com.competra.web.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.competra.data.api.ApiResult
import com.competra.data.repository.CompetitionRepository
import com.competra.domain.models.OrienteeringCompetition
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompetitionDetailPage(competitionId: String, onBack: () -> Unit) {
    val repo: CompetitionRepository = koinInject()
    var competition by remember { mutableStateOf<OrienteeringCompetition?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(competitionId) {
        when (val r = repo.getById(competitionId)) {
            is ApiResult.Success -> competition = r.data
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(competition?.competition?.title ?: "Соревнование") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("← Назад") }
                },
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Дистанции") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Результаты") })
            }
            when (selectedTab) {
                0 -> DistancesTab(competitionId = competitionId)
                1 -> ResultsTab(competitionId = competitionId)
            }
        }
    }
}
