package com.competra.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Rating(
    @SerialName("id")          val id: String,
    @SerialName("name")        val name: String,
    @SerialName("ownerClubId") val ownerClubId: String,
    @SerialName("groups")      val groups: List<RatingGroup> = emptyList(),
    @SerialName("createdAt")   val createdAt: Long = 0L,
    @SerialName("updatedAt")   val updatedAt: Long = 0L,
)

/** gender: "MALE" | "FEMALE" | "MIXED" | null (любой). */
@Serializable
data class RatingGroup(
    @SerialName("id")         val id: Long = 0L,
    @SerialName("ratingId")   val ratingId: String = "",
    @SerialName("title")      val title: String,
    @SerialName("gender")     val gender: String? = null,
    @SerialName("minAge")     val minAge: Int? = null,
    @SerialName("maxAge")     val maxAge: Int? = null,
    @SerialName("orderIndex") val orderIndex: Int = 0,
)

/** Облегчённая карточка рейтинга для глобального поиска. */
@Serializable
data class RatingSummary(
    @SerialName("id")            val id: String,
    @SerialName("name")          val name: String,
    @SerialName("ownerClubId")   val ownerClubId: String,
    @SerialName("ownerClubName") val ownerClubName: String,
    @SerialName("createdAt")     val createdAt: Long = 0L,
)

@Serializable
data class RatingCompetition(
    @SerialName("id")                   val id: String,
    @SerialName("ratingId")             val ratingId: String,
    @SerialName("competitionId")        val competitionId: String,
    @SerialName("competitionTitle")     val competitionTitle: String,
    @SerialName("competitionStartDate") val competitionStartDate: Long = 0L,
    @SerialName("addedAt")              val addedAt: Long = 0L,
)

@Serializable
data class RatingGroupMappingSuggestion(
    @SerialName("participantGroupId")      val participantGroupId: Long,
    @SerialName("participantGroupTitle")   val participantGroupTitle: String,
    @SerialName("suggestedRatingGroupId")  val suggestedRatingGroupId: Long? = null,
    @SerialName("confidence")              val confidence: Float = 0f,
)

@Serializable
data class AddCompetitionToRatingResult(
    @SerialName("ratingCompetition")      val ratingCompetition: RatingCompetition,
    @SerialName("groupMappingSuggestions") val groupMappingSuggestions: List<RatingGroupMappingSuggestion> = emptyList(),
)

@Serializable
data class RatingStandingBreakdownEntry(
    @SerialName("competitionId") val competitionId: String,
    @SerialName("place")         val place: Int? = null,
    @SerialName("points")        val points: Int = 0,
)

@Serializable
data class RatingStanding(
    @SerialName("participantKey") val participantKey: String,
    @SerialName("displayName")    val displayName: String,
    @SerialName("totalPoints")    val totalPoints: Int = 0,
    @SerialName("rank")           val rank: Int = 0,
    @SerialName("breakdown")      val breakdown: List<RatingStandingBreakdownEntry> = emptyList(),
)

@Serializable
data class RatingStandingsResponse(
    @SerialName("ratingGroupId") val ratingGroupId: Long,
    @SerialName("standings")     val standings: List<RatingStanding> = emptyList(),
)

@Serializable
data class RatingGroupRequest(
    @SerialName("id")         val id: Long? = null,
    @SerialName("title")      val title: String,
    @SerialName("gender")     val gender: String? = null,
    @SerialName("minAge")     val minAge: Int? = null,
    @SerialName("maxAge")     val maxAge: Int? = null,
    @SerialName("orderIndex") val orderIndex: Int,
)

@Serializable
data class CreateRatingRequest(
    @SerialName("name")   val name: String,
    @SerialName("groups") val groups: List<RatingGroupRequest>,
)

@Serializable
data class UpdateRatingRequest(
    @SerialName("name")   val name: String,
    @SerialName("groups") val groups: List<RatingGroupRequest>,
)

@Serializable
data class AddCompetitionToRatingRequest(
    @SerialName("competitionId") val competitionId: String,
)

@Serializable
data class GroupMappingEntry(
    @SerialName("participantGroupId") val participantGroupId: Long,
    @SerialName("ratingGroupId")      val ratingGroupId: Long,
)

@Serializable
data class SetGroupMappingRequest(
    @SerialName("mappings") val mappings: List<GroupMappingEntry>,
)
