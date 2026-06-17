package com.competra.web

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.competra.web.pages.CompetitionDetailPage
import com.competra.web.pages.CompetitionsPage
import com.competra.web.pages.ProfilePage
import com.competra.web.theme.CompetiraTheme

sealed class Page {
    data object Competitions : Page()
    data class CompetitionDetail(val competitionId: String) : Page()
    data object Profile : Page()
}

@Composable
fun App() {
    var page by remember { mutableStateOf<Page>(Page.Competitions) }

    CompetiraTheme {
        when (val current = page) {
            is Page.CompetitionDetail -> CompetitionDetailPage(
                competitionId = current.competitionId,
                onBack = { page = Page.Competitions },
            )
            else -> MainScaffold(
                currentPage = current,
                onNavigate = { page = it },
            )
        }
    }
}

@Composable
private fun NavIcon(selected: Boolean, label: String) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Box(
        modifier = androidx.compose.ui.Modifier.size(24.dp).background(color, CircleShape),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(label, color = MaterialTheme.colorScheme.surface, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MainScaffold(currentPage: Page, onNavigate: (Page) -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentPage is Page.Competitions,
                    onClick = { onNavigate(Page.Competitions) },
                    icon = { NavIcon(selected = currentPage is Page.Competitions, label = "С") },
                    label = { Text("Соревнования") },
                )
                NavigationBarItem(
                    selected = currentPage is Page.Profile,
                    onClick = { onNavigate(Page.Profile) },
                    icon = { NavIcon(selected = currentPage is Page.Profile, label = "П") },
                    label = { Text("Профиль") },
                )
            }
        }
    ) { padding ->
        when (currentPage) {
            is Page.Competitions -> CompetitionsPage(
                modifier = androidx.compose.ui.Modifier.padding(padding),
                onCompetitionClick = { id -> onNavigate(Page.CompetitionDetail(id)) },
            )
            is Page.Profile -> ProfilePage(
                onLoginSuccess = { onNavigate(Page.Competitions) },
            )
            else -> {}
        }
    }
}
