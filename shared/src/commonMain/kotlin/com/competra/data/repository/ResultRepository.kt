package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.OrienteeringResult
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class ResultRepository(private val publicClient: HttpClient) {

    suspend fun getResults(competitionId: String): ApiResult<List<OrienteeringResult>> = safeApiCall {
        publicClient.get("$BASE_URL/event/orienteering/results/competition") {
            parameter("competitionId", competitionId)
        }.body<CommonModel<List<OrienteeringResult>>>()
    }

    suspend fun getParticipants(competitionId: String): ApiResult<List<OrienteeringParticipant>> = safeApiCall {
        publicClient.get("$BASE_URL/event/orienteering/participants/competition") {
            parameter("competitionId", competitionId)
        }.body<CommonModel<List<OrienteeringParticipant>>>()
    }
}
