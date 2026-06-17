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
