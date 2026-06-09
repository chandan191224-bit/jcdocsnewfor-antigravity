package com.example.ai

import java.io.Serializable

data class AiMessage(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val mediaUrl: String? = null,
    val mediaType: String? = null
) : Serializable

interface AiProvider {
    suspend fun chat(
        messages: List<AiMessage>,
        systemPrompt: String? = null
    ): Result<AiResponse>

    suspend fun testConnection(): Boolean
}

data class AiResponse(
    val content: String,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val modelUsed: String
)
