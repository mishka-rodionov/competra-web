package com.competra.web.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.competra.data.auth.AuthRepository
import com.competra.data.auth.TokenStorage
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(onLoginSuccess: () -> Unit) {
    val tokenStorage: TokenStorage = koinInject()
    val authRepo: AuthRepository = koinInject()

    var showLogin by remember { mutableStateOf(false) }
    val isLoggedIn = tokenStorage.isLoggedIn()

    if (showLogin) {
        LoginPage(onLoginSuccess = {
            showLogin = false
            onLoginSuccess()
        })
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Профиль") }) }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoggedIn) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        Text("👤", style = MaterialTheme.typography.displayMedium)
                    }
                    Text("Профиль пользователя", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Статистика участий — в разработке",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        authRepo.logout()
                        onLoginSuccess()
                    }) {
                        Text("Выйти из аккаунта")
                    }
                }
            } else {
                Column(
                    modifier = Modifier.padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(modifier = Modifier.size(80.dp), contentAlignment = Alignment.Center) {
                        Text("👤", style = MaterialTheme.typography.displayMedium)
                    }
                    Text("Вы не вошли в аккаунт", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Войдите, чтобы видеть свои соревнования и управлять дистанциями",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = { showLogin = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Войти / Зарегистрироваться")
                    }
                }
            }
        }
    }
}
