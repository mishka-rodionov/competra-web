package com.competra.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Distance(
    @SerialName("id")                 val id: Long,
    @SerialName("competitionId")      val competitionId: String,
    @SerialName("name")               val name: String?,
    @SerialName("lengthMeters")       val lengthMeters: Int,
    @SerialName("climbMeters")        val climbMeters: Int,
    @SerialName("controlsCount")      val controlsCount: Int,
    @SerialName("description")        val description: String?,
    @SerialName("controlPoints")      val controlPoints: List<ControlPoint> = emptyList(),
    @SerialName("finishControlPoint") val finishControlPoint: Int?,
    @SerialName("updatedAt")          val updatedAt: Long = 0L,
)

@Serializable
data class ControlPoint(
    @SerialName("number") val number: Int,
    @SerialName("role")   val role: String = "ORDINARY",
    @SerialName("score")  val score: Int = 0,
)

@Serializable
data class SaveDistanceRequest(
    @SerialName("distanceId")          val distanceId: Long?,
    @SerialName("competitionId")       val competitionId: String,
    @SerialName("name")                val name: String?,
    @SerialName("lengthMeters")        val lengthMeters: Int,
    @SerialName("climbMeters")         val climbMeters: Int,
    @SerialName("controlsCount")       val controlsCount: Int,
    @SerialName("description")         val description: String?,
    @SerialName("controlPoints")       val controlPoints: List<ControlPoint> = emptyList(),
    @SerialName("finishControlPoint")  val finishControlPoint: Int? = null,
)
