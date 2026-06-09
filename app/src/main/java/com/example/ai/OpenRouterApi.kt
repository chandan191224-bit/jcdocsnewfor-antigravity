package com.example.ai

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

@JsonClass(generateAdapter = true)
data class OpenRouterRequest(
    val model: String,
    val messages: List<OpenRouterMessage>,
    @Json(name = "include_reasoning") val includeReasoning: Boolean = false
)

@JsonClass(generateAdapter = true)
data class OpenRouterMessage(
    val role: String,
    val content: String // Can be String or List of ContentPart
)

@JsonClass(generateAdapter = true)
data class ContentPart(
    val type: String,
    val text: String? = null,
    @Json(name = "image_url") val imageUrl: ImageUrl? = null
)

@JsonClass(generateAdapter = true)
data class ImageUrl(
    val url: String
)

@JsonClass(generateAdapter = true)
data class OpenRouterResponse(
    val choices: List<Choice>? = null,
    val error: ErrorDetail? = null
)

@JsonClass(generateAdapter = true)
data class Choice(
    val message: MessageDetail? = null
)

@JsonClass(generateAdapter = true)
data class MessageDetail(
    val content: String? = null
)

@JsonClass(generateAdapter = true)
data class ErrorDetail(
    val message: String? = null,
    val code: Int? = null
)

interface OpenRouterApi {
    @POST
    suspend fun chat(
        @Url url: String,
        @Header("Authorization") auth: String,
        @Header("HTTP-Referer") referer: String,
        @Header("X-Title") title: String,
        @Body request: OpenRouterRequest
    ): Response<OpenRouterResponse>
}
