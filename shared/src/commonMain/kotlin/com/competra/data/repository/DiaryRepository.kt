package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.data.api.safeApiCallUnit
import com.competra.domain.models.Workout
import com.competra.domain.models.WorkoutRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class DiaryRepository(private val authClient: HttpClient) {

    suspend fun getWorkouts(): ApiResult<List<Workout>> = safeApiCall {
        authClient.get("$BASE_URL/diary/workouts").body<CommonModel<List<Workout>>>()
    }

    suspend fun saveWorkout(request: WorkoutRequest): ApiResult<List<Workout>> = safeApiCall {
        authClient.post("$BASE_URL/diary/workouts") { setBody(listOf(request)) }.body<CommonModel<List<Workout>>>()
    }

    suspend fun deleteWorkout(id: Long): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/diary/workouts/$id").body<CommonModel<Unit?>>()
    }
}
