package com.competra.data.api

import com.competra.data.auth.AuthResponse
import com.competra.data.auth.RefreshRequest
import com.competra.data.auth.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val BASE_URL = "https://competra.ru/api"

/** Бросается, когда сервер вернул 401 и обновить токен не удалось. */
class UnauthorizedException : Exception("Сессия истекла, войдите снова")

private val jsonConfig = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

/** Клиент без авторизации — для публичных эндпоинтов. */
fun createPublicHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(jsonConfig) }
    install(Logging) { level = LogLevel.INFO }
    defaultRequest { contentType(ContentType.Application.Json) }
}

/**
 * Клиент с Bearer-токеном — для авторизованных эндпоинтов.
 * accessToken живёт 1 час; при 401 плагин [Auth] сам вызывает `/refresh_token`
 * (refreshToken — 30 дней, одноразовый, ротируется на каждый рефреш) и повторяет запрос,
 * поэтому пользователю не приходится логиниться заново каждый час.
 */
fun createHttpClient(tokenStorage: TokenStorage): HttpClient {
    // Отдельный клиент без Auth-плагина — только для самого запроса рефреша,
    // чтобы 401 от него не пытался рекурсивно запустить ещё один рефреш.
    val refreshClient = HttpClient {
        install(ContentNegotiation) { json(jsonConfig) }
    }
    return HttpClient {
        install(ContentNegotiation) { json(jsonConfig) }
        install(Logging) { level = LogLevel.INFO }
        install(Auth) {
            bearer {
                loadTokens {
                    val access = tokenStorage.getToken() ?: return@loadTokens null
                    val refresh = tokenStorage.getRefreshToken() ?: ""
                    BearerTokens(access, refresh)
                }
                refreshTokens {
                    val refreshToken = tokenStorage.getRefreshToken()
                    if (refreshToken == null) {
                        tokenStorage.clearToken()
                        return@refreshTokens null
                    }
                    val response = refreshClient.post("$BASE_URL/refresh_token") {
                        contentType(ContentType.Application.Json)
                        setBody(RefreshRequest(refreshToken))
                    }
                    val body = if (response.status.isSuccess()) response.body<CommonModel<AuthResponse>>() else null
                    val newTokens = body?.takeIf { it.status == 1 }?.result?.token
                    if (newTokens == null) {
                        tokenStorage.clearToken()
                        null
                    } else {
                        tokenStorage.saveTokens(newTokens.accessToken, newTokens.refreshToken)
                        BearerTokens(newTokens.accessToken, newTokens.refreshToken)
                    }
                }
            }
        }
        defaultRequest { contentType(ContentType.Application.Json) }
        // Тело ответа 401 — не JSON, поэтому перехватываем до попытки распарсить как CommonModel.
        // Сюда попадает только 401, переживший попытку рефреша (см. Auth выше).
        expectSuccess = false
        HttpResponseValidator {
            validateResponse { response ->
                if (response.status == HttpStatusCode.Unauthorized) {
                    throw UnauthorizedException()
                }
            }
        }
    }
}
