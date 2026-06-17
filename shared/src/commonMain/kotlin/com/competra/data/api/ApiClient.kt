package com.competra.data.api

import com.competra.data.auth.TokenStorage
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.bearerAuth
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

const val BASE_URL = "https://competra.ru/api"

private val jsonConfig = Json { ignoreUnknownKeys = true; isLenient = true }

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
}
