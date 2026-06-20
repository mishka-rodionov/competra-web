package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.data.api.safeApiCallUnit
import com.competra.domain.models.Competition
import com.competra.domain.models.CompetitionDetail
import com.competra.domain.models.CreateCompetitionRequest
import com.competra.domain.models.OrienteeringCompetition
import com.competra.domain.models.RegisterEventRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class CompetitionRepository(
    private val publicClient: HttpClient,
    private val authClient: HttpClient,
) {
    suspend fun getPublicCompetitions(
        kindOfSports: List<String> = emptyList(),
        statuses: List<String> = emptyList(),
        dateFrom: Long? = null,
        dateTo: Long? = null,
    ): ApiResult<List<Competition>> = safeApiCall {
        publicClient.get("$BASE_URL/event/orienteering/competitions/public") {
            kindOfSports.forEach { parameter("kind_of_sports", it) }
            statuses.forEach { parameter("statuses", it) }
            dateFrom?.let { parameter("date_from", it) }
            dateTo?.let { parameter("date_to", it) }
        }.body<CommonModel<List<Competition>>>()
    }

    suspend fun getCompetitionDetail(id: String, userId: String? = null): ApiResult<CompetitionDetail> = safeApiCall {
        publicClient.get("$BASE_URL/event/orienteering/competitions/public/$id") {
            userId?.let { parameter("userId", it) }
        }.body<CommonModel<CompetitionDetail>>()
    }

    suspend fun getMyCompetitions(): ApiResult<List<OrienteeringCompetition>> = safeApiCall {
        authClient.get("$BASE_URL/event/orienteering/competitions")
            .body<CommonModel<List<OrienteeringCompetition>>>()
    }

    suspend fun getRegisteredCompetitions(): ApiResult<List<OrienteeringCompetition>> = safeApiCall {
        authClient.get("$BASE_URL/event/orienteering/competitions/registered")
            .body<CommonModel<List<OrienteeringCompetition>>>()
    }

    suspend fun register(request: RegisterEventRequest): ApiResult<Unit> = safeApiCallUnit {
        authClient.post("$BASE_URL/event/orienteering/register") {
            setBody(request)
        }.body<CommonModel<Unit?>>()
    }

    suspend fun cancelRegistration(competitionId: String): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/event/orienteering/register/$competitionId")
            .body<CommonModel<Unit?>>()
    }

    suspend fun createCompetition(request: CreateCompetitionRequest): ApiResult<OrienteeringCompetition> = safeApiCall {
        authClient.post("$BASE_URL/event/orienteering/save/competitions") {
            setBody(request)
        }.body<CommonModel<OrienteeringCompetition>>()
    }
}
