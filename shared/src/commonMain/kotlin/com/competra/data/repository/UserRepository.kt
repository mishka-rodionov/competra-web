package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.domain.models.UserProfile
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get

class UserRepository(private val authClient: HttpClient) {

    suspend fun getUserProfile(): ApiResult<UserProfile> = safeApiCall {
        authClient.get("$BASE_URL/user/profile")
            .body<CommonModel<UserProfile>>()
    }
}
