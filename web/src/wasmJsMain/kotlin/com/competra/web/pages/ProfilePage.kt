package com.competra.web.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.competra.data.auth.AuthRepository
import com.competra.data.auth.TokenStorage
import com.competra.data.repository.CompetitionRepository
import com.competra.data.repository.UserRepository
import com.competra.domain.models.OrienteeringCompetition
import com.competra.domain.models.UserProfile
import com.competra.web.utils.toLocaleDateString
import org.koin.compose.koinInject

@JsFun("() => Date.now()")
private external fun jsDateNow(): Double

private fun currentTimeMs(): Long = jsDateNow().toLong()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(
    onLoginSuccess: () -> Unit,
    onCompetitionClick: (String) -> Unit,
) {
    val tokenStorage: TokenStorage = koinInject()
    val authRepo: AuthRepository = koinInject()
    val userRepo: UserRepository = koinInject()
    val competitionRepo: CompetitionRepository = koinInject()

    var showLogin by remember { mutableStateOf(false) }
    val isLoggedIn = tokenStorage.isLoggedIn()
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var upcomingCompetitions by remember { mutableStateOf<List<OrienteeringCompetition>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(isLoggedIn) {
        if (!isLoggedIn) {
            loading = false
            return@LaunchedEffect
        }
        when (val r = userRepo.getUserProfile()) {
            is ApiResult.Success -> profile = r.data
            is ApiResult.Error -> {}
        }
        when (val r = competitionRepo.getRegisteredCompetitions()) {
            is ApiResult.Success -> {
                val now = currentTimeMs()
                upcomingCompetitions = r.data
                    .filter { it.competition.startDate >= now }
                    .sortedBy { it.competition.startDate }
            }
            is ApiResult.Error -> {}
        }
        loading = false
    }

    if (showLogin) {
        LoginPage(onLoginSuccess = {
            showLogin = false
            onLoginSuccess()
        })
        return
    }

    if (!isLoggedIn) {
        UnauthenticatedProfile(onLoginClick = { showLogin = true })
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Профиль") }) }
    ) { padding ->
        if (loading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (profile != null) {
                            Text(
                                "${profile!!.lastName} ${profile!!.firstName}".trim(),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                profile!!.email,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            profile!!.birthDate?.let {
                                Text(
                                    "Дата рождения: ${it.toLocaleDateString()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            Text("Профиль пользователя", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }

            item {
                Text(
                    "Предстоящие старты",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (upcomingCompetitions.isEmpty()) {
                item {
                    Text(
                        "Нет предстоящих стартов",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(upcomingCompetitions) { comp ->
                    UpcomingCompetitionCard(
                        competition = comp,
                        onClick = { onCompetitionClick(comp.competitionId) },
                    )
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                OutlinedButton(
                    onClick = {
                        authRepo.logout()
                        profile = null
                        upcomingCompetitions = emptyList()
                        onLoginSuccess()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Выйти из аккаунта", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@Composable
private fun UpcomingCompetitionCard(competition: OrienteeringCompetition, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(competition.competition.title, style = MaterialTheme.typography.titleSmall)
            Text(
                competition.competition.startDate.toLocaleDateString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            competition.competition.address?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnauthenticatedProfile(onLoginClick: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("Профиль") }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("Вы не вошли в аккаунт", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Войдите, чтобы видеть свои регистрации и управлять соревнованиями",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onLoginClick,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Войти / Зарегистрироваться")
                }
            }
        }
    }
}
