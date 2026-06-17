package com.competra.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Competition(
    @SerialName("remoteId")        val remoteId: Long?,
    @SerialName("title")           val title: String,
    @SerialName("startDate")       val startDate: Long,
    @SerialName("endDate")         val endDate: Long?,
    @SerialName("kindOfSport")     val kindOfSport: String,
    @SerialName("description")     val description: String?,
    @SerialName("address")         val address: String?,
    @SerialName("status")          val status: String,
    @SerialName("imageUrl")        val imageUrl: String?,
    @SerialName("contactPhone")    val contactPhone: String?,
    @SerialName("contactEmail")    val contactEmail: String?,
    @SerialName("maxParticipants") val maxParticipants: Int?,
    @SerialName("feeAmount")       val feeAmount: Double?,
    @SerialName("feeCurrency")     val feeCurrency: String?,
    @SerialName("timeZoneId")      val timeZoneId: String,
)

@Serializable
data class OrienteeringCompetition(
    @SerialName("competitionId")     val competitionId: Long,
    @SerialName("competition")       val competition: Competition,
    @SerialName("direction")         val direction: String,
    @SerialName("punchingSystem")    val punchingSystem: String,
    @SerialName("startTimeMode")     val startTimeMode: String,
    @SerialName("isDrawConducted")   val isDrawConducted: Boolean,
    @SerialName("startTime")         val startTime: Long?,
    @SerialName("startIntervalSeconds") val startIntervalSeconds: Int?,
    @SerialName("countdownTimer")    val countdownTimer: Int?,
)
