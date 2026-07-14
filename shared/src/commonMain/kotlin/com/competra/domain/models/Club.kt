package com.competra.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Club(
    @SerialName("id")                 val id: String,
    @SerialName("name")               val name: String,
    @SerialName("description")        val description: String? = null,
    @SerialName("allowJoinRequests")  val allowJoinRequests: Boolean = true,
    @SerialName("foundedAt")          val foundedAt: Long = 0L,
    @SerialName("membersCount")       val membersCount: Int = 0,
    @SerialName("updatedAt")          val updatedAt: Long = 0L,
)

@Serializable
data class ClubMember(
    @SerialName("id")        val id: String,
    @SerialName("clubId")    val clubId: String,
    @SerialName("userId")    val userId: String,
    @SerialName("firstName") val firstName: String,
    @SerialName("lastName")  val lastName: String,
    @SerialName("role")      val role: String,
    @SerialName("joinedAt")  val joinedAt: Long = 0L,
)

@Serializable
data class ClubJoinRequest(
    @SerialName("id")        val id: String,
    @SerialName("clubId")    val clubId: String,
    @SerialName("userId")    val userId: String,
    @SerialName("firstName") val firstName: String,
    @SerialName("lastName")  val lastName: String,
    @SerialName("status")    val status: String,
    @SerialName("createdAt") val createdAt: Long = 0L,
)

@Serializable
data class Team(
    @SerialName("id")           val id: String,
    @SerialName("clubId")       val clubId: String,
    @SerialName("name")         val name: String,
    @SerialName("sportType")    val sportType: String,
    @SerialName("membersCount") val membersCount: Int = 0,
    @SerialName("updatedAt")    val updatedAt: Long = 0L,
)

@Serializable
data class TeamMember(
    @SerialName("id")           val id: String,
    @SerialName("teamId")       val teamId: String,
    @SerialName("clubMemberId") val clubMemberId: String,
    @SerialName("userId")       val userId: String,
    @SerialName("firstName")    val firstName: String,
    @SerialName("lastName")     val lastName: String,
    @SerialName("role")         val role: String,
    @SerialName("joinedAt")     val joinedAt: Long = 0L,
)

@Serializable
data class CreateClubRequest(
    @SerialName("name")              val name: String,
    @SerialName("description")       val description: String? = null,
    @SerialName("allowJoinRequests") val allowJoinRequests: Boolean = true,
)

@Serializable
data class UpdateClubRequest(
    @SerialName("name")              val name: String,
    @SerialName("description")       val description: String? = null,
    @SerialName("allowJoinRequests") val allowJoinRequests: Boolean,
)

/** role: "ADMIN" | "MEMBER" | "FOUNDER" (передача роли основателя). */
@Serializable
data class ChangeRoleRequest(
    @SerialName("role") val role: String,
)

@Serializable
data class ReviewJoinRequestRequest(
    @SerialName("approve") val approve: Boolean,
)

@Serializable
data class CreateTeamRequest(
    @SerialName("name")       val name: String,
    @SerialName("sportType")  val sportType: String,
)

@Serializable
data class UpdateTeamRequest(
    @SerialName("name")      val name: String,
    @SerialName("sportType") val sportType: String,
)

@Serializable
data class AddTeamMemberRequest(
    @SerialName("clubMemberId") val clubMemberId: String,
    @SerialName("role")         val role: String = "MEMBER",
)

/** role: "CAPTAIN" | "MEMBER" */
@Serializable
data class ChangeTeamMemberRoleRequest(
    @SerialName("role") val role: String,
)
