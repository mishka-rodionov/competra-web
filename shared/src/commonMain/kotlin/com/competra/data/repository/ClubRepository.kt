package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.PagedResponse
import com.competra.data.api.safeApiCall
import com.competra.data.api.safeApiCallUnit
import com.competra.domain.models.ChangeRoleRequest
import com.competra.domain.models.Club
import com.competra.domain.models.ClubJoinRequest
import com.competra.domain.models.ClubMember
import com.competra.domain.models.CreateClubRequest
import com.competra.domain.models.ReviewJoinRequestRequest
import com.competra.domain.models.UpdateClubRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class ClubRepository(private val publicClient: HttpClient, private val authClient: HttpClient) {

    suspend fun searchClubs(query: String? = null, page: Int = 0, limit: Int = 20): ApiResult<PagedResponse<Club>> =
        safeApiCall {
            publicClient.get("$BASE_URL/clubs") {
                query?.takeIf { it.isNotBlank() }?.let { parameter("query", it) }
                parameter("page", page)
                parameter("limit", limit)
            }.body<CommonModel<PagedResponse<Club>>>()
        }

    suspend fun getClub(id: String): ApiResult<Club> = safeApiCall {
        publicClient.get("$BASE_URL/clubs/$id").body<CommonModel<Club>>()
    }

    suspend fun getClubMembers(id: String): ApiResult<List<ClubMember>> = safeApiCall {
        publicClient.get("$BASE_URL/clubs/$id/members").body<CommonModel<List<ClubMember>>>()
    }

    suspend fun getMyClubs(): ApiResult<List<Club>> = safeApiCall {
        authClient.get("$BASE_URL/clubs/mine").body<CommonModel<List<Club>>>()
    }

    suspend fun createClub(request: CreateClubRequest): ApiResult<Club> = safeApiCall {
        authClient.post("$BASE_URL/clubs") { setBody(request) }.body<CommonModel<Club>>()
    }

    suspend fun updateClub(id: String, request: UpdateClubRequest): ApiResult<Club> = safeApiCall {
        authClient.put("$BASE_URL/clubs/$id") { setBody(request) }.body<CommonModel<Club>>()
    }

    suspend fun deleteClub(id: String): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/clubs/$id").body<CommonModel<Unit?>>()
    }

    /** requesterUserId == targetUserId — выход из клуба; иначе — удаление участника (нужны права FOUNDER/ADMIN). */
    suspend fun removeMember(clubId: String, targetUserId: String): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/clubs/$clubId/members/$targetUserId").body<CommonModel<Unit?>>()
    }

    suspend fun changeMemberRole(clubId: String, targetUserId: String, request: ChangeRoleRequest): ApiResult<ClubMember> =
        safeApiCall {
            authClient.put("$BASE_URL/clubs/$clubId/members/$targetUserId/role") {
                setBody(request)
            }.body<CommonModel<ClubMember>>()
        }

    suspend fun createJoinRequest(clubId: String): ApiResult<ClubJoinRequest> = safeApiCall {
        authClient.post("$BASE_URL/clubs/$clubId/join-requests").body<CommonModel<ClubJoinRequest>>()
    }

    suspend fun getJoinRequestsForClub(clubId: String): ApiResult<List<ClubJoinRequest>> = safeApiCall {
        authClient.get("$BASE_URL/clubs/$clubId/join-requests").body<CommonModel<List<ClubJoinRequest>>>()
    }

    suspend fun getMyJoinRequests(): ApiResult<List<ClubJoinRequest>> = safeApiCall {
        authClient.get("$BASE_URL/clubs/join-requests/mine").body<CommonModel<List<ClubJoinRequest>>>()
    }

    suspend fun reviewJoinRequest(clubId: String, requestId: String, approve: Boolean): ApiResult<ClubJoinRequest> =
        safeApiCall {
            authClient.put("$BASE_URL/clubs/$clubId/join-requests/$requestId") {
                setBody(ReviewJoinRequestRequest(approve))
            }.body<CommonModel<ClubJoinRequest>>()
        }
}
