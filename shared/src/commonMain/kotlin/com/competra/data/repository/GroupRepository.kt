package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.data.api.safeApiCallUnit
import com.competra.domain.models.CreateGroupRequest
import com.competra.domain.models.ParticipantGroupDetail
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody

class GroupRepository(private val authClient: HttpClient) {

    suspend fun getGroups(competitionId: Long): ApiResult<List<ParticipantGroupDetail>> = safeApiCall {
        authClient.get("$BASE_URL/event/orienteering/participantGroups") {
            parameter("competitionId", competitionId)
        }.body<CommonModel<List<ParticipantGroupDetail>>>()
    }

    suspend fun saveGroup(request: CreateGroupRequest): ApiResult<List<ParticipantGroupDetail>> = safeApiCall {
        authClient.post("$BASE_URL/event/orienteering/save/participantGroup") {
            setBody(listOf(request))
        }.body<CommonModel<List<ParticipantGroupDetail>>>()
    }

    /** Сохраняет список групп одним запросом. */
    suspend fun saveGroups(requests: List<CreateGroupRequest>): ApiResult<List<ParticipantGroupDetail>> = safeApiCall {
        authClient.post("$BASE_URL/event/orienteering/save/participantGroup") {
            setBody(requests)
        }.body<CommonModel<List<ParticipantGroupDetail>>>()
    }

    suspend fun deleteGroup(groupId: Long): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/event/orienteering/participantGroups/$groupId")
            .body<CommonModel<Unit?>>()
    }
}
