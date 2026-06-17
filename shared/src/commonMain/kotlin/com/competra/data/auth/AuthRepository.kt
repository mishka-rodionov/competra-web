package com.competra.data.auth

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmailRequest(@SerialName("email") val email: String)

@Serializable
data class CodeVerificationRequest(
    @SerialName("email") val email: String,
    @SerialName("code")  val code: String,
)

@Serializable
data class AuthResponse(
    @SerialName("accessToken")  val accessToken: String,
    @SerialName("refreshToken") val refreshToken: String,
)

class AuthRepository(
    private val client: HttpClient,
    private val tokenStorage: TokenStorage,
) {
    suspend fun sendCode(email: String): ApiResult<Unit> =
        safeApiCall {
            client.post("$BASE_URL/user/login") {
                setBody(EmailRequest(email))
            }.body<CommonModel<Unit>>()
        }

    suspend fun verifyCode(email: String, code: String): ApiResult<AuthResponse> {
        val result = safeApiCall {
            client.post("$BASE_URL/user/verify_code") {
                setBody(CodeVerificationRequest(email, code))
            }.body<CommonModel<AuthResponse>>()
        }
        if (result is ApiResult.Success) {
            tokenStorage.saveToken(result.data.accessToken)
        }
        return result
    }

    fun logout() = tokenStorage.clearToken()
}
