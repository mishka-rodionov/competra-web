package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.data.api.safeApiCallUnit
import com.competra.domain.models.AddTeamMemberRequest
import com.competra.domain.models.ChangeTeamMemberRoleRequest
import com.competra.domain.models.CreateTeamRequest
import com.competra.domain.models.Team
import com.competra.domain.models.TeamMember
import com.competra.domain.models.UpdateTeamRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class TeamRepository(private val publicClient: HttpClient, private val authClient: HttpClient) {

    suspend fun getTeamsByClub(clubId: String): ApiResult<List<Team>> = safeApiCall {
        publicClient.get("$BASE_URL/clubs/$clubId/teams").body<CommonModel<List<Team>>>()
    }

    suspend fun getTeam(id: String): ApiResult<Team> = safeApiCall {
        publicClient.get("$BASE_URL/teams/$id").body<CommonModel<Team>>()
    }

    suspend fun getTeamMembers(id: String): ApiResult<List<TeamMember>> = safeApiCall {
        publicClient.get("$BASE_URL/teams/$id/members").body<CommonModel<List<TeamMember>>>()
    }

    suspend fun createTeam(clubId: String, request: CreateTeamRequest): ApiResult<Team> = safeApiCall {
        authClient.post("$BASE_URL/clubs/$clubId/teams") { setBody(request) }.body<CommonModel<Team>>()
    }

    suspend fun updateTeam(id: String, request: UpdateTeamRequest): ApiResult<Team> = safeApiCall {
        authClient.put("$BASE_URL/teams/$id") { setBody(request) }.body<CommonModel<Team>>()
    }

    suspend fun deleteTeam(id: String): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/teams/$id").body<CommonModel<Unit?>>()
    }

    suspend fun addTeamMember(teamId: String, request: AddTeamMemberRequest): ApiResult<TeamMember> = safeApiCall {
        authClient.post("$BASE_URL/teams/$teamId/members") { setBody(request) }.body<CommonModel<TeamMember>>()
    }

    suspend fun removeTeamMember(teamId: String, clubMemberId: String): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/teams/$teamId/members/$clubMemberId").body<CommonModel<Unit?>>()
    }

    suspend fun changeTeamMemberRole(teamId: String, clubMemberId: String, request: ChangeTeamMemberRoleRequest): ApiResult<TeamMember> =
        safeApiCall {
            authClient.put("$BASE_URL/teams/$teamId/members/$clubMemberId/role") {
                setBody(request)
            }.body<CommonModel<TeamMember>>()
        }
}
