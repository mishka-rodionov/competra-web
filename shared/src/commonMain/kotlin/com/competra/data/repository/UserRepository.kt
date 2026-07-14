package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.domain.models.UserProfile
import com.competra.domain.models.UserProfileUpdateRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.setBody

class UserRepository(private val authClient: HttpClient) {

    suspend fun getUserProfile(): ApiResult<UserProfile> = safeApiCall {
        authClient.get("$BASE_URL/user/profile")
            .body<CommonModel<UserProfile>>()
    }

    suspend fun updateProfile(request: UserProfileUpdateRequest): ApiResult<UserProfile> = safeApiCall {
        authClient.patch("$BASE_URL/user/profile") {
            setBody(request)
        }.body<CommonModel<UserProfile>>()
    }
}
