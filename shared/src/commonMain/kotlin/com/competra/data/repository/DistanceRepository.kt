package com.competra.data.repository

import com.competra.data.api.ApiResult
import com.competra.data.api.BASE_URL
import com.competra.data.api.CommonModel
import com.competra.data.api.safeApiCall
import com.competra.domain.models.Distance
import com.competra.domain.models.SaveDistanceRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders

class DistanceRepository(private val client: HttpClient) {

    suspend fun getByCompetition(remoteId: Long): ApiResult<List<Distance>> = safeApiCall {
        client.get("$BASE_URL/event/orienteering/distances") {
            parameter("competitionId", remoteId)
        }.body<CommonModel<List<Distance>>>()
    }

    suspend fun saveDistance(request: SaveDistanceRequest): ApiResult<List<Distance>> = safeApiCall {
        client.post("$BASE_URL/event/orienteering/save/distances") {
            setBody(listOf(request))
        }.body<CommonModel<List<Distance>>>()
    }

    suspend fun importFromXml(remoteId: Long, xmlBytes: ByteArray): ApiResult<List<Distance>> =
        safeApiCall {
            client.post("$BASE_URL/event/orienteering/import/courses") {
                setBody(MultiPartFormDataContent(formData {
                    append("competitionId", remoteId.toString())
                    append("xmlFile", xmlBytes, Headers.build {
                        append(HttpHeaders.ContentType, "application/xml")
                        append(HttpHeaders.ContentDisposition, "filename=\"courses.xml\"")
                    })
                }))
            }.body<CommonModel<List<Distance>>>()
        }
}
