package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.PagedResponse
import com.competra.data.api.safeApiCall
import com.competra.data.api.safeApiCallUnit
import com.competra.domain.models.AddCompetitionToRatingRequest
import com.competra.domain.models.AddCompetitionToRatingResult
import com.competra.domain.models.CreateRatingRequest
import com.competra.domain.models.GroupMappingEntry
import com.competra.domain.models.Rating
import com.competra.domain.models.RatingCompetition
import com.competra.domain.models.RatingGroupMappingSuggestion
import com.competra.domain.models.RatingStandingsResponse
import com.competra.domain.models.RatingSummary
import com.competra.domain.models.SetGroupMappingRequest
import com.competra.domain.models.UpdateRatingRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

class RatingRepository(private val publicClient: HttpClient, private val authClient: HttpClient) {

    suspend fun searchRatings(query: String? = null, page: Int = 0, limit: Int = 20): ApiResult<PagedResponse<RatingSummary>> =
        safeApiCall {
            publicClient.get("$BASE_URL/ratings") {
                query?.takeIf { it.isNotBlank() }?.let { parameter("query", it) }
                parameter("page", page)
                parameter("limit", limit)
            }.body<CommonModel<PagedResponse<RatingSummary>>>()
        }

    suspend fun getRatingsForClub(clubId: String): ApiResult<List<Rating>> = safeApiCall {
        publicClient.get("$BASE_URL/clubs/$clubId/ratings").body<CommonModel<List<Rating>>>()
    }

    suspend fun getRating(id: String): ApiResult<Rating> = safeApiCall {
        publicClient.get("$BASE_URL/ratings/$id").body<CommonModel<Rating>>()
    }

    suspend fun createRating(clubId: String, request: CreateRatingRequest): ApiResult<Rating> = safeApiCall {
        authClient.post("$BASE_URL/clubs/$clubId/ratings") { setBody(request) }.body<CommonModel<Rating>>()
    }

    suspend fun updateRating(id: String, request: UpdateRatingRequest): ApiResult<Rating> = safeApiCall {
        authClient.put("$BASE_URL/ratings/$id") { setBody(request) }.body<CommonModel<Rating>>()
    }

    suspend fun deleteRating(id: String): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/ratings/$id").body<CommonModel<Unit?>>()
    }

    suspend fun getRatingCompetitions(id: String): ApiResult<List<RatingCompetition>> = safeApiCall {
        publicClient.get("$BASE_URL/ratings/$id/competitions").body<CommonModel<List<RatingCompetition>>>()
    }

    suspend fun addCompetition(id: String, competitionId: String): ApiResult<AddCompetitionToRatingResult> = safeApiCall {
        authClient.post("$BASE_URL/ratings/$id/competitions") {
            setBody(AddCompetitionToRatingRequest(competitionId))
        }.body<CommonModel<AddCompetitionToRatingResult>>()
    }

    suspend fun removeCompetition(id: String, competitionId: String): ApiResult<Unit> = safeApiCallUnit {
        authClient.delete("$BASE_URL/ratings/$id/competitions/$competitionId").body<CommonModel<Unit?>>()
    }

    suspend fun getMappingSuggestions(id: String, competitionId: String): ApiResult<List<RatingGroupMappingSuggestion>> =
        safeApiCall {
            publicClient.get("$BASE_URL/ratings/$id/competitions/$competitionId/mapping-suggestions")
                .body<CommonModel<List<RatingGroupMappingSuggestion>>>()
        }

    suspend fun setGroupMapping(id: String, competitionId: String, mappings: List<GroupMappingEntry>): ApiResult<Unit> =
        safeApiCallUnit {
            authClient.put("$BASE_URL/ratings/$id/competitions/$competitionId/mapping") {
                setBody(SetGroupMappingRequest(mappings))
            }.body<CommonModel<Unit?>>()
        }

    suspend fun getStandings(id: String, groupId: Long): ApiResult<RatingStandingsResponse> = safeApiCall {
        publicClient.get("$BASE_URL/ratings/$id/standings") {
            parameter("groupId", groupId)
        }.body<CommonModel<RatingStandingsResponse>>()
    }
}
