package com.competra.web

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.competra.data.auth.TokenStorage
import com.competra.web.pages.CompetitionDetailPage
import com.competra.web.pages.CompetitionsPage
import com.competra.web.pages.LoginPage
import com.competra.web.pages.ProfilePage
import com.competra.web.theme.CompetiraTheme
import org.koin.compose.koinInject

sealed class Page {
    data object Login : Page()
    data object Competitions : Page()
    data class CompetitionDetail(val competitionId: Long) : Page()
    data object Profile : Page()
}

@Composable
fun App() {
    val tokenStorage: TokenStorage = koinInject()
    var page by remember {
        mutableStateOf<Page>(
            if (tokenStorage.isLoggedIn()) Page.Competitions else Page.Login
        )
    }

    CompetiraTheme {
        when (val current = page) {
            is Page.Login -> LoginPage(
                onLoginSuccess = { page = Page.Competitions }
            )
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
private fun MainScaffold(currentPage: Page, onNavigate: (Page) -> Unit) {
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentPage is Page.Competitions,
                    onClick = { onNavigate(Page.Competitions) },
                    icon = { Text("🏆") },
                    label = { Text("Соревнования") },
                )
                NavigationBarItem(
                    selected = currentPage is Page.Profile,
                    onClick = { onNavigate(Page.Profile) },
                    icon = { Text("👤") },
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
                onLogout = { onNavigate(Page.Login) },
            )
            else -> {}
        }
    }
}
