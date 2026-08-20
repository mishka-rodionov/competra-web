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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class UploadResponse(
    @SerialName("url") val url: String,
)

class DistanceRepository(private val client: HttpClient) {

    suspend fun getByCompetition(competitionId: String): ApiResult<List<Distance>> = safeApiCall {
        client.get("$BASE_URL/event/orienteering/distances") {
            parameter("competitionId", competitionId)
        }.body<CommonModel<List<Distance>>>()
    }

    suspend fun saveDistance(request: SaveDistanceRequest): ApiResult<List<Distance>> = safeApiCall {
        client.post("$BASE_URL/event/orienteering/save/distances") {
            setBody(listOf(request))
        }.body<CommonModel<List<Distance>>>()
    }

    /**
     * Сохраняет список дистанций одним запросом. Бэкенд (`DistanceService.upsertAll`)
     * возвращает дистанции в том же порядке, что и запрос, поэтому ответ можно сопоставлять
     * с исходным списком по индексу.
     */
    suspend fun saveDistances(requests: List<SaveDistanceRequest>): ApiResult<List<Distance>> = safeApiCall {
        client.post("$BASE_URL/event/orienteering/save/distances") {
            setBody(requests)
        }.body<CommonModel<List<Distance>>>()
    }

    /**
     * Загружает файл карты дистанции (растр из mapper) в объектное хранилище и возвращает
     * его публичный URL. Полученный URL и координаты углов нужно затем сохранить отдельным
     * вызовом [saveDistance] — так же, как загружаются аватары/обложки соревнований.
     */
    suspend fun uploadDistanceMap(bytes: ByteArray, fileName: String, contentType: String): ApiResult<String> =
        safeApiCall {
            val response = client.post("$BASE_URL/upload/file") {
                setBody(MultiPartFormDataContent(formData {
                    append("type", "distance-map")
                    append("file", bytes, Headers.build {
                        append(HttpHeaders.ContentType, contentType)
                        append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
                    })
                }))
            }.body<CommonModel<UploadResponse>>()
            CommonModel(status = response.status, result = response.result?.url, errors = response.errors)
        }

    suspend fun importFromXml(competitionId: String, xmlBytes: ByteArray): ApiResult<List<Distance>> =
        safeApiCall {
            client.post("$BASE_URL/event/orienteering/import/courses") {
                setBody(MultiPartFormDataContent(formData {
                    append("competitionId", competitionId)
                    append("xmlFile", xmlBytes, Headers.build {
                        append(HttpHeaders.ContentType, "application/xml")
                        append(HttpHeaders.ContentDisposition, "filename=\"courses.xml\"")
                    })
                }))
            }.body<CommonModel<List<Distance>>>()
        }
}
