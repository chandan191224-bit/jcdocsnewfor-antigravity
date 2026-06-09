package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query
import retrofit2.http.Url
import java.util.concurrent.TimeUnit

@JsonClass(generateAdapter = true)
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @Json(name = "systemInstruction") val systemInstruction: GeminiContent? = null,
    @Json(name = "generationConfig") val generationConfig: GeminiGenerationConfig? = null
)

@JsonClass(generateAdapter = true)
data class GeminiContent(
    val role: String? = null, // "user" or "model"
    val parts: List<GeminiPart>
)

@JsonClass(generateAdapter = true)
data class GeminiPart(
    val text: String
)

@JsonClass(generateAdapter = true)
data class GeminiGenerationConfig(
    val temperature: Float? = null
)

@JsonClass(generateAdapter = true)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>? = null,
    val error: GeminiError? = null
)

@JsonClass(generateAdapter = true)
data class GeminiCandidate(
    val content: GeminiContent? = null
)

@JsonClass(generateAdapter = true)
data class GeminiError(
    val message: String? = null,
    val status: String? = null
)

interface GeminiApi {
    @POST
    suspend fun generateContent(
        @Url url: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>
}

class GeminiAiProvider(
    private val apiKey: String = BuildConfig.GEMINI_API_KEY
) : AiProvider {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val client = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://generativelanguage.googleapis.com/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client)
        .build()
        .create(GeminiApi::class.java)

    override suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String?
    ): Result<AiResponse> {
        return try {
            val contents = mutableListOf<GeminiContent>()
            messages.forEach { msg ->
                val role = when (msg.role.lowercase()) {
                    "user" -> "user"
                    "assistant", "model" -> "model"
                    else -> "user"
                }
                contents.add(GeminiContent(role = role, parts = listOf(GeminiPart(msg.content))))
            }

            val systemInstruction = if (!systemPrompt.isNullOrBlank()) {
                GeminiContent(parts = listOf(GeminiPart(systemPrompt)))
            } else null

            val request = GeminiRequest(
                contents = contents,
                systemInstruction = systemInstruction
            )

            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
            val response = api.generateContent(
                url = url,
                apiKey = apiKey,
                request = request
            )

            if (response.isSuccessful) {
                val body = response.body()
                val content = body?.candidates?.getOrNull(0)?.content?.parts?.getOrNull(0)?.text
                if (content != null) {
                    Result.success(AiResponse(content = content, modelUsed = "gemini-3.5-flash"))
                } else {
                    val errorMsg = body?.error?.message ?: "Response succeeded but content was empty or format was unexpected"
                    Result.failure(Exception(errorMsg))
                }
            } else {
                val errorBodyString = response.errorBody()?.string()
                Log.e("GeminiAiProvider", "Error: $errorBodyString")
                Result.failure(Exception("Gemini API Error: ${response.code()} $errorBodyString"))
            }
        } catch (e: Exception) {
            Log.e("GeminiAiProvider", "Exception in chat", e)
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Boolean {
        return try {
            val request = GeminiRequest(
                contents = listOf(GeminiContent(role = "user", parts = listOf(GeminiPart("hi"))))
            )
            val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"
            val response = api.generateContent(
                url = url,
                apiKey = apiKey,
                request = request
            )
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
