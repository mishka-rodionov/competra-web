package com.competra.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CreateGroupRequest(
    @SerialName("groupId")               val groupId: Long = 0,
    @SerialName("competitionId")         val competitionId: String,
    @SerialName("title")                 val title: String,
    @SerialName("gender")                val gender: String? = null,
    @SerialName("minAge")                val minAge: Int? = null,
    @SerialName("maxAge")                val maxAge: Int? = null,
    @SerialName("distanceId")            val distanceId: Long? = null,
    @SerialName("maxParticipants")       val maxParticipants: Int? = null,
    /** Лимит времени для формата "по выбору" (BY_CHOICE), в минутах. */
    @SerialName("timeLimitMinutes")      val timeLimitMinutes: Int? = null,
    /** Штраф в очках за минуту опоздания сверх лимита (BY_CHOICE). */
    @SerialName("scorePenaltyPerMinute") val scorePenaltyPerMinute: Int? = null,
    /** Порог сильного опоздания, после которого результат обнуляется (BY_CHOICE). */
    @SerialName("maxLatenessMinutes")    val maxLatenessMinutes: Int? = null,
)

@Serializable
data class OrienteeringParticipant(
    @SerialName("id")            val id: String,
    @SerialName("userId")        val userId: String? = null,
    @SerialName("firstName")     val firstName: String = "",
    @SerialName("lastName")      val lastName: String = "",
    @SerialName("groupId")       val groupId: Long = 0,
    @SerialName("groupName")     val groupName: String? = null,
    @SerialName("competitionId") val competitionId: String = "",
    @SerialName("commandName")   val commandName: String? = null,
    @SerialName("startNumber")   val startNumber: String? = null,
    @SerialName("startTime")     val startTime: Long? = null,
    @SerialName("chipNumber")    val chipNumber: Long? = null,
    @SerialName("comment")       val comment: String? = null,
    @SerialName("isChipGiven")   val isChipGiven: Boolean = false,
)

/** Запрос на создание/обновление участника вручную (без self-регистрации) — 1:1 с бэкендовым OrienteeringParticipantRequest. */
@Serializable
data class SaveParticipantRequest(
    @SerialName("id")              val id: String,
    @SerialName("userId")          val userId: String? = null,
    @SerialName("firstName")       val firstName: String,
    @SerialName("lastName")        val lastName: String,
    @SerialName("groupId")         val groupId: Long,
    @SerialName("groupName")       val groupName: String,
    @SerialName("competitionId")   val competitionId: String,
    @SerialName("commandName")     val commandName: String? = null,
    @SerialName("startNumber")     val startNumber: Int,
    @SerialName("startTime")       val startTime: Long,
    @SerialName("chipNumber")      val chipNumber: Long = 0,
    @SerialName("comment")         val comment: String? = null,
    @SerialName("isChipGiven")     val isChipGiven: Boolean = false,
)

@Serializable
data class OrienteeringResult(
    @SerialName("id")            val id: String,
    @SerialName("competitionId") val competitionId: String = "",
    @SerialName("groupId")       val groupId: Long = 0,
    @SerialName("participantId") val participantId: String = "",
    @SerialName("startTime")     val startTime: Long? = null,
    @SerialName("finishTime")    val finishTime: Long? = null,
    @SerialName("totalTime")     val totalTime: Long? = null,
    @SerialName("rank")          val rank: Int? = null,
    @SerialName("status")        val status: String = "FINISHED",
    @SerialName("penaltyTime")   val penaltyTime: Long = 0,
    @SerialName("splits")        val splits: List<SplitTime>? = null,
    @SerialName("totalScore")    val totalScore: Int? = null,
    @SerialName("isEditable")    val isEditable: Boolean = true,
    @SerialName("isEdited")      val isEdited: Boolean = false,
)

@Serializable
data class SplitTime(
    @SerialName("controlPoint") val controlPoint: Int,
    @SerialName("timestamp")    val timestamp: Long,
)

/** Запрос на сохранение/обновление результата — форма 1:1 с бэкендовым OrienteeringResultRequest. */
@Serializable
data class SaveResultRequest(
    @SerialName("id")              val id: String,
    @SerialName("competitionId")   val competitionId: String,
    @SerialName("groupId")         val groupId: Long,
    @SerialName("participantId")   val participantId: String,
    @SerialName("startTime")       val startTime: Long?,
    @SerialName("finishTime")      val finishTime: Long?,
    @SerialName("totalTime")       val totalTime: Long?,
    @SerialName("rank")            val rank: Int?,
    @SerialName("status")          val status: String,
    @SerialName("penaltyTime")     val penaltyTime: Long,
    @SerialName("splits")          val splits: List<SplitTime>?,
    @SerialName("isEditable")      val isEditable: Boolean,
    @SerialName("isEdited")        val isEdited: Boolean,
    @SerialName("serverUpdatedAt") val serverUpdatedAt: Long? = null,
)
