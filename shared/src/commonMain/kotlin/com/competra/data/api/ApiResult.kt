package com.competra.data.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CommonModel<T>(
    @SerialName("status")  val status: Int = 0,
    @SerialName("result")  val result: T? = null,
    @SerialName("errors")  val errors: List<ApiError>? = null,
)

@Serializable
data class ApiError(
    @SerialName("code")    val code: Int,
    @SerialName("message") val message: String,
)

sealed class ApiResult<out T> {
    data class Success<T>(val data: T) : ApiResult<T>()
    data class Error(val message: String, val code: Int = 0) : ApiResult<Nothing>()
}

/** HTTP-код для ответа 401 — по нему UI понимает, что нужно перелогиниться. */
const val HTTP_UNAUTHORIZED = 401

suspend fun <T> safeApiCall(call: suspend () -> CommonModel<T>): ApiResult<T> =
    try {
        val response = call()
        if (response.status == 1 && response.result != null)
            ApiResult.Success(response.result)
        else
            ApiResult.Error(response.errors?.firstOrNull()?.message ?: "Unknown error")
    } catch (e: UnauthorizedException) {
        ApiResult.Error(e.message ?: "Сессия истекла", code = HTTP_UNAUTHORIZED)
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Network error")
    }

/** For endpoints that return {"status":1} with no result body. */
suspend fun safeApiCallUnit(call: suspend () -> CommonModel<Unit?>): ApiResult<Unit> =
    try {
        val response = call()
        if (response.status == 1) ApiResult.Success(Unit)
        else ApiResult.Error(response.errors?.firstOrNull()?.message ?: "Unknown error")
    } catch (e: UnauthorizedException) {
        ApiResult.Error(e.message ?: "Сессия истекла", code = HTTP_UNAUTHORIZED)
    } catch (e: Exception) {
        ApiResult.Error(e.message ?: "Network error")
    }
