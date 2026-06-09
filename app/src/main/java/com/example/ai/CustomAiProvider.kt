package com.example.ai

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class CustomAiProvider(
    private val name: String,
    private val apiKey: String,
    private val baseUrl: String = "https://api.openai.com/v1/",
    private val model: String = "gpt-3.5-turbo"
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
        .build()

    private val api = Retrofit.Builder()
        .baseUrl(if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client)
        .build()
        .create(OpenRouterApi::class.java) // Reusing the same interface as it's OpenAI compatible

    override suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String?
    ): Result<AiResponse> {
        return try {
            val orMessages = mutableListOf<OpenRouterMessage>()
            if (systemPrompt != null) {
                orMessages.add(OpenRouterMessage("system", systemPrompt))
            }
            messages.forEach { msg ->
                orMessages.add(OpenRouterMessage(msg.role, msg.content))
            }

            val request = OpenRouterRequest(model = model, messages = orMessages)
            val response = api.chat(
                url = "${if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"}chat/completions",
                auth = "Bearer $apiKey",
                referer = "https://ai.studio",
                title = "JCDocs AI",
                request = request
            )

            if (response.isSuccessful) {
                val content = response.body()?.choices?.getOrNull(0)?.message?.content
                if (content != null) {
                    Result.success(AiResponse(content = content, modelUsed = model))
                } else {
                    Result.failure(Exception("Empty response from $name"))
                }
            } else {
                Result.failure(Exception("Error from $name: ${response.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun testConnection(): Boolean {
        return try {
            val request = OpenRouterRequest(
                model = model,
                messages = listOf(OpenRouterMessage("user", "hi"))
            )
            val response = api.chat(
                url = "${if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"}chat/completions",
                auth = "Bearer $apiKey",
                referer = "https://ai.studio",
                title = "JCDocs AI",
                request = request
            )
            response.isSuccessful
        } catch (e: Exception) {
            false
        }
    }
}
