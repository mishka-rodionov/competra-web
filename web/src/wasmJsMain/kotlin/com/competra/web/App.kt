package com.competra.web

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.competra.data.auth.TokenStorage
import com.competra.web.pages.CompetitionDetailPage
import com.competra.web.pages.CompetitionsPage
import com.competra.web.pages.LoginPage
import com.competra.web.theme.CompetiraTheme
import org.koin.compose.koinInject

sealed class Page {
    data object Login : Page()
    data object Competitions : Page()
    data class CompetitionDetail(val competitionId: Long) : Page()
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
            is Page.Competitions -> CompetitionsPage(
                onCompetitionClick = { id -> page = Page.CompetitionDetail(id) }
            )
            is Page.CompetitionDetail -> CompetitionDetailPage(
                competitionId = current.competitionId,
                onBack = { page = Page.Competitions }
            )
        }
    }
}
