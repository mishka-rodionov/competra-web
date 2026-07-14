package com.competra.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** sportType: "RUNNING" | "CYCLING" | "SKIING". status: "PLANNED" | "IN_PROGRESS" | "COMPLETED" (веб использует только PLANNED/COMPLETED). */
@Serializable
data class Workout(
    @SerialName("id")                 val id: Long,
    @SerialName("sportType")          val sportType: String,
    @SerialName("status")             val status: String,
    @SerialName("scheduledDate")      val scheduledDate: Long? = null,
    @SerialName("startedAt")          val startedAt: Long? = null,
    @SerialName("durationSeconds")    val durationSeconds: Int? = null,
    @SerialName("distanceMeters")     val distanceMeters: Int? = null,
    @SerialName("elevationGainMeters") val elevationGainMeters: Int? = null,
    @SerialName("notes")              val notes: String? = null,
    @SerialName("trackEncoded")       val trackEncoded: String? = null,
    @SerialName("runDetails")         val runDetails: RunDetails? = null,
    @SerialName("bikeDetails")        val bikeDetails: BikeDetails? = null,
    @SerialName("skiDetails")         val skiDetails: SkiDetails? = null,
    @SerialName("updatedAt")          val updatedAt: Long = 0L,
)

@Serializable
data class RunDetails(
    @SerialName("cadenceSpm") val cadenceSpm: Int? = null,
)

@Serializable
data class BikeDetails(
    @SerialName("cadenceRpm") val cadenceRpm: Int? = null,
    @SerialName("powerWatts") val powerWatts: Int? = null,
)

/** style: "CLASSIC" | "SKATE" */
@Serializable
data class SkiDetails(
    @SerialName("style") val style: String? = null,
)

@Serializable
data class WorkoutRequest(
    @SerialName("workoutId")          val workoutId: Long? = null,
    @SerialName("sportType")          val sportType: String,
    @SerialName("status")             val status: String,
    @SerialName("scheduledDate")      val scheduledDate: Long? = null,
    @SerialName("startedAt")          val startedAt: Long? = null,
    @SerialName("durationSeconds")    val durationSeconds: Int? = null,
    @SerialName("distanceMeters")     val distanceMeters: Int? = null,
    @SerialName("elevationGainMeters") val elevationGainMeters: Int? = null,
    @SerialName("notes")              val notes: String? = null,
    @SerialName("trackEncoded")       val trackEncoded: String? = null,
    @SerialName("runDetails")         val runDetails: RunDetails? = null,
    @SerialName("bikeDetails")        val bikeDetails: BikeDetails? = null,
    @SerialName("skiDetails")         val skiDetails: SkiDetails? = null,
)
