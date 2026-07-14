package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.data.api.safeApiCallUnit
import com.competra.domain.models.OrienteeringParticipant
import com.competra.domain.models.OrienteeringResult
import com.competra.domain.models.SaveParticipantRequest
import com.competra.domain.models.SaveResultRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class ResultRepository(private val publicClient: HttpClient, private val authClient: HttpClient) {

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

    suspend fun saveResults(requests: List<SaveResultRequest>): ApiResult<List<OrienteeringResult>> = safeApiCall {
        authClient.post("$BASE_URL/event/orienteering/save/results") {
            setBody(requests)
        }.body<CommonModel<List<OrienteeringResult>>>()
    }

    suspend fun saveParticipant(request: SaveParticipantRequest): ApiResult<OrienteeringParticipant> = safeApiCall {
        authClient.post("$BASE_URL/event/orienteering/save/participant") {
            setBody(request)
        }.body<CommonModel<OrienteeringParticipant>>()
    }

    suspend fun deleteParticipant(id: String): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/event/orienteering/participants/$id")
            .body<CommonModel<Unit?>>()
    }
}
