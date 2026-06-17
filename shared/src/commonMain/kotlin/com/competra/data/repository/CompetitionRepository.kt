package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.domain.models.OrienteeringCompetition
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter

class CompetitionRepository(private val client: HttpClient) {

    suspend fun getPublicCompetitions(
        kindOfSports: List<String> = emptyList(),
        statuses: List<String> = emptyList(),
    ): ApiResult<List<OrienteeringCompetition>> = safeApiCall {
        client.get("$BASE_URL/event/orienteering/competitions/public") {
            kindOfSports.forEach { parameter("kind_of_sports", it) }
            statuses.forEach { parameter("statuses", it) }
        }.body<CommonModel<List<OrienteeringCompetition>>>()
    }

    suspend fun getMyCompetitions(): ApiResult<List<OrienteeringCompetition>> = safeApiCall {
        client.get("$BASE_URL/event/orienteering/competitions")
            .body<CommonModel<List<OrienteeringCompetition>>>()
    }

    suspend fun getById(id: Long): ApiResult<OrienteeringCompetition> = safeApiCall {
        client.get("$BASE_URL/event/orienteering/competitions/public/$id")
            .body<CommonModel<OrienteeringCompetition>>()
    }
}
