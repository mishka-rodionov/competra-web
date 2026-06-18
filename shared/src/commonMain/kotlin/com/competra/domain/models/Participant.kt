package com.competra.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupRequest(
    @SerialName("groupId")         val groupId: Long = 0,
    @SerialName("competitionId")   val competitionId: Long,
    @SerialName("title")           val title: String,
    @SerialName("gender")          val gender: String? = null,
    @SerialName("minAge")          val minAge: Int? = null,
    @SerialName("maxAge")          val maxAge: Int? = null,
    @SerialName("distanceId")      val distanceId: Long? = null,
    @SerialName("maxParticipants") val maxParticipants: Int? = null,
)

@Serializable
data class OrienteeringParticipant(
    @SerialName("id")            val id: String,
    @SerialName("userId")        val userId: String? = null,
    @SerialName("firstName")     val firstName: String = "",
    @SerialName("lastName")      val lastName: String = "",
    @SerialName("groupId")       val groupId: Long = 0,
    @SerialName("groupName")     val groupName: String? = null,
    @SerialName("competitionId") val competitionId: Long = 0,
    @SerialName("startNumber")   val startNumber: String? = null,
    @SerialName("startTime")     val startTime: Long? = null,
)

@Serializable
data class OrienteeringResult(
    @SerialName("id")            val id: String,
    @SerialName("competitionId") val competitionId: Long = 0,
    @SerialName("groupId")       val groupId: Long = 0,
    @SerialName("participantId") val participantId: String = "",
    @SerialName("startTime")     val startTime: Long? = null,
    @SerialName("finishTime")    val finishTime: Long? = null,
    @SerialName("totalTime")     val totalTime: Long? = null,
    @SerialName("rank")          val rank: Int? = null,
    @SerialName("status")        val status: String = "FINISHED",
    @SerialName("penaltyTime")   val penaltyTime: Long = 0,
)
