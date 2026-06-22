package com.competra.data.api

import com.competra.data.auth.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpResponseValidator
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val BASE_URL = "https://competra.ru/api"

/** Бросается, когда сервер вернул 401 — токен истёк или невалиден. */
class UnauthorizedException : Exception("Сессия истекла, войдите снова")

private val jsonConfig = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

/** Клиент без авторизации — для публичных эндпоинтов. */
fun createPublicHttpClient(): HttpClient = HttpClient {
    install(ContentNegotiation) { json(jsonConfig) }
    install(Logging) { level = LogLevel.INFO }
    defaultRequest { contentType(ContentType.Application.Json) }
}

/** Клиент с Bearer-токеном — для авторизованных эндпоинтов. */
fun createHttpClient(tokenStorage: TokenStorage): HttpClient = HttpClient {
    install(ContentNegotiation) { json(jsonConfig) }
    install(Logging) { level = LogLevel.INFO }
    defaultRequest {
        contentType(ContentType.Application.Json)
        val token = tokenStorage.getToken()
        if (token != null) bearerAuth(token)
    }
    // Тело ответа 401 — не JSON, поэтому перехватываем до попытки распарсить как CommonModel:
    // чистим протухший токен и бросаем типизированное исключение.
    expectSuccess = false
    HttpResponseValidator {
        validateResponse { response ->
            if (response.status == HttpStatusCode.Unauthorized) {
                tokenStorage.clearToken()
                throw UnauthorizedException()
            }
        }
    }
}
