package com.example.ai

import android.util.Log
import com.example.BuildConfig
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class OpenRouterAiProvider(
    private val apiKey: String = BuildConfig.OPENROUTER_API_KEY,
    private val customModel: String? = null
) : AiProvider {

    private val freeModels = listOf(
        "google/gemini-2.5-flash:free",
        "google/gemini-2.5-pro:free",
        "deepseek/deepseek-r1:free",
        "openai/gpt-oss-20b:free",
        "deepseek/deepseek-chat:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "meta-llama/llama-3.1-8b-instruct:free",
        "qwen/qwen-2.5-72b-instruct:free",
        "qwen/qwen-2.5-coder-32b-instruct:free",
        "mistralai/mistral-7b-instruct:free",
        "microsoft/phi-3-medium-128k-instruct:free",
        "openrouter/auto" // Last resort
    )

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
        .baseUrl("https://openrouter.ai/api/v1/")
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .client(client)
        .build()
        .create(OpenRouterApi::class.java)

    override suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String?
    ): Result<AiResponse> {
        val baseList = freeModels.toMutableList()
        if (customModel != null && customModel.isNotBlank()) {
            baseList.remove(customModel)
            baseList.add(0, customModel)
        }
        val modelsToTry = baseList
        
        var lastError: Throwable? = null
        for (model in modelsToTry) {
            try {
                val orMessages = mutableListOf<OpenRouterMessage>()
                if (systemPrompt != null) {
                    orMessages.add(OpenRouterMessage("system", systemPrompt))
                }
                messages.forEach { msg ->
                    orMessages.add(OpenRouterMessage(msg.role, msg.content))
                }

                val request = OpenRouterRequest(model = model, messages = orMessages)
                val response = api.chat(
                    url = "https://openrouter.ai/api/v1/chat/completions",
                    auth = "Bearer $apiKey",
                    referer = "https://ai.studio",
                    title = "JCDocs AI",
                    request = request
                )

                if (response.isSuccessful) {
                    val body = response.body()
                    val content = body?.choices?.getOrNull(0)?.message?.content
                    if (content != null) {
                        return Result.success(AiResponse(content = content, modelUsed = model))
                    } else {
                        val errMsg = "Response succeeded but content was empty or choice format was unexpected"
                        Log.e("OpenRouter", errMsg)
                        lastError = Exception(errMsg)
                    }
                } else {
                    val code = response.code()
                    val rawError = response.errorBody()?.string() ?: ""
                    val keyPreview = when {
                        apiKey.isEmpty() -> "EMPTY"
                        apiKey.length > 10 -> apiKey.take(5) + "..." + apiKey.takeLast(5)
                        else -> "SHORT(len=${apiKey.length})"
                    }
                    val errMsg = "[OpenRouter API Error] with model $model (Key: $keyPreview): $code $rawError"
                    Log.e("OpenRouter", errMsg)
                    lastError = Exception(errMsg)

                    if (code == 401 || code == 403) {
                        // Fast fallback abort if key/credentials are invalid
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e("OpenRouter", "Exception with model $model", e)
                lastError = e
            }
        }

        return Result.failure(lastError ?: Exception("All models failed or no response from OpenRouter"))
    }

    override suspend fun testConnection(): Boolean {
        val modelsToTest = mutableListOf<String>()
        if (customModel != null && customModel.isNotBlank()) {
            modelsToTest.add(customModel)
        }
        modelsToTest.addAll(freeModels) // Try ALL fallback models if specific one fails

        for (model in modelsToTest.distinct()) {
            try {
                val request = OpenRouterRequest(
                    model = model,
                    messages = listOf(OpenRouterMessage("user", "hi"))
                )
                val response = api.chat(
                    url = "https://openrouter.ai/api/v1/chat/completions",
                    auth = "Bearer $apiKey",
                    referer = "https://ai.studio",
                    title = "JCDocs AI",
                    request = request
                )
                if (response.isSuccessful) {
                    return true
                }
                // If it's a critical auth error, don't keep trying
                if (response.code() == 401 || response.code() == 403) {
                    return false
                }
            } catch (e: Exception) {
                // Try next model
            }
        }
        return false
    }
}
