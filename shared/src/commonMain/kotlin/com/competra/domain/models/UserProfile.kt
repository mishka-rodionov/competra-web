package com.competra.domain.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    @SerialName("id")          val id: String = "",
    @SerialName("firstName")   val firstName: String = "",
    @SerialName("lastName")    val lastName: String = "",
    @SerialName("middleName")  val middleName: String? = null,
    @SerialName("email")       val email: String = "",
    @SerialName("avatarUrl")   val avatarUrl: String? = null,
    @SerialName("birthDate")   val birthDate: Long? = null,
    @SerialName("gender")      val gender: String? = null,
    @SerialName("phoneNumber") val phoneNumber: String? = null,
)
