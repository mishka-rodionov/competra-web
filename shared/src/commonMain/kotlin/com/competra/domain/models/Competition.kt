package com.competra.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Competition(
    @SerialName("remoteId")        val remoteId: Long? = null,
    @SerialName("title")           val title: String,
    @SerialName("startDate")       val startDate: Long = 0,
    @SerialName("endDate")         val endDate: Long? = null,
    @SerialName("kindOfSport")     val kindOfSport: String = "",
    @SerialName("description")     val description: String? = null,
    @SerialName("address")         val address: String? = null,
    @SerialName("status")          val status: String = "",
    @SerialName("imageUrl")        val imageUrl: String? = null,
    @SerialName("contactPhone")    val contactPhone: String? = null,
    @SerialName("contactEmail")    val contactEmail: String? = null,
    @SerialName("maxParticipants") val maxParticipants: Int? = null,
    @SerialName("feeAmount")       val feeAmount: Double? = null,
    @SerialName("feeCurrency")     val feeCurrency: String? = null,
    @SerialName("timeZoneId")      val timeZoneId: String = "",
    @SerialName("registrationStart") val registrationStart: Long? = null,
    @SerialName("registrationEnd")   val registrationEnd: Long? = null,
)

@Serializable
data class OrienteeringCompetition(
    @SerialName("competitionId")        val competitionId: String,
    @SerialName("competition")          val competition: Competition,
    @SerialName("direction")            val direction: String = "FORWARD",
    @SerialName("punchingSystem")       val punchingSystem: String = "",
    @SerialName("startTimeMode")        val startTimeMode: String = "USER_SET",
    @SerialName("isDrawConducted")      val isDrawConducted: Boolean = false,
    @SerialName("startTime")            val startTime: Long? = null,
    @SerialName("startIntervalSeconds") val startIntervalSeconds: Int? = null,
    @SerialName("countdownTimer")       val countdownTimer: Int? = null,
)

@Serializable
data class CompetitionDetail(
    @SerialName("remoteId")            val remoteId: Long? = null,
    @SerialName("title")               val title: String = "",
    @SerialName("startDate")           val startDate: Long = 0,
    @SerialName("endDate")             val endDate: Long? = null,
    @SerialName("kindOfSport")         val kindOfSport: String = "",
    @SerialName("description")         val description: String? = null,
    @SerialName("address")             val address: String? = null,
    @SerialName("status")              val status: String = "",
    @SerialName("imageUrl")            val imageUrl: String? = null,
    @SerialName("contactPhone")        val contactPhone: String? = null,
    @SerialName("contactEmail")        val contactEmail: String? = null,
    @SerialName("maxParticipants")     val maxParticipants: Int? = null,
    @SerialName("feeAmount")           val feeAmount: Double? = null,
    @SerialName("feeCurrency")         val feeCurrency: String? = null,
    @SerialName("timeZoneId")          val timeZoneId: String = "",
    @SerialName("registrationStart")   val registrationStart: Long? = null,
    @SerialName("registrationEnd")     val registrationEnd: Long? = null,
    @SerialName("resultsStatus")       val resultsStatus: String = "NOT_PUBLISHED",
    @SerialName("mainOrganizerId")     val mainOrganizerId: String? = null,
    @SerialName("participantGroups")   val participantGroups: List<ParticipantGroupDetail> = emptyList(),
    @SerialName("isUserRegistered")    val isUserRegistered: Boolean = false,
)

@Serializable
data class ParticipantGroupDetail(
    @SerialName("groupId")                val groupId: Long = 0,
    @SerialName("title")                  val title: String = "",
    @SerialName("gender")                 val gender: String? = null,
    @SerialName("minAge")                 val minAge: Int? = null,
    @SerialName("maxAge")                 val maxAge: Int? = null,
    @SerialName("distanceId")             val distanceId: Long? = null,
    @SerialName("distanceName")           val distanceName: String? = null,
    @SerialName("distanceLengthMeters")   val distanceLengthMeters: Int? = null,
    @SerialName("distanceClimbMeters")    val distanceClimbMeters: Int? = null,
    @SerialName("distanceControlsCount")  val distanceControlsCount: Int? = null,
    @SerialName("maxParticipants")        val maxParticipants: Int? = null,
    @SerialName("registeredParticipant")  val registeredCount: Int = 0,
)

@Serializable
data class RegisterEventRequest(
    @SerialName("competitionId") val competitionId: Long,
    @SerialName("groupId")       val groupId: Long,
    @SerialName("firstName")     val firstName: String,
    @SerialName("lastName")      val lastName: String,
)

@Serializable
data class CompetitionFields(
    @SerialName("title")              val title: String,
    @SerialName("startDate")          val startDate: Long,
    @SerialName("endDate")            val endDate: Long? = null,
    @SerialName("kindOfSport")        val kindOfSport: String = "Orienteering",
    @SerialName("description")        val description: String? = null,
    @SerialName("address")            val address: String? = null,
    @SerialName("status")             val status: String = "REGISTRATION_OPEN",
    @SerialName("registrationStart")  val registrationStart: Long? = null,
    @SerialName("registrationEnd")    val registrationEnd: Long? = null,
    @SerialName("maxParticipants")    val maxParticipants: Int? = null,
    @SerialName("feeAmount")          val feeAmount: Double? = null,
    @SerialName("feeCurrency")        val feeCurrency: String? = null,
    @SerialName("contactPhone")       val contactPhone: String? = null,
    @SerialName("contactEmail")       val contactEmail: String? = null,
    @SerialName("website")            val website: String? = null,
    @SerialName("timeZoneId")         val timeZoneId: String = "Europe/Moscow",
)

@Serializable
data class CreateCompetitionRequest(
    @SerialName("competitionId")  val competitionId: String,
    @SerialName("competition")    val competition: CompetitionFields,
    @SerialName("direction")      val direction: String = "FORWARD",
    @SerialName("punchingSystem") val punchingSystem: String = "SPORTIDENT",
    @SerialName("startTimeMode")  val startTimeMode: String = "USER_SET",
)
