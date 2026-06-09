package com.example.db

import com.example.ai.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AiChatRepository(private val chatDao: ChatDao) {
    val allConversations: Flow<List<ChatConversation>> = chatDao.getAllConversations()
    val allProviders: Flow<List<AiProviderConfig>> = chatDao.getAllProviderConfigs()

    fun getMessages(conversationId: Int): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForConversation(conversationId)

    suspend fun createConversation(title: String): Long {
        val conv = ChatConversation(title = title)
        return chatDao.insertConversation(conv)
    }

    suspend fun saveMessage(conversationId: Int, role: String, content: String, mediaUrl: String? = null, mediaType: String? = null) {
        val msg = ChatMessageEntity(
            conversationId = conversationId,
            role = role,
            content = content,
            mediaUrl = mediaUrl,
            mediaType = mediaType
        )
        chatDao.insertMessage(msg)
        
        // Update conversation last message and timestamp
        val conv = chatDao.getConversationById(conversationId)
        if (conv != null) {
            chatDao.updateConversation(conv.copy(
                lastMessage = content.take(100),
                updatedAt = System.currentTimeMillis()
            ))
        }
    }

    suspend fun deleteConversation(conversationId: Int) {
        val conv = chatDao.getConversationById(conversationId)
        if (conv != null) {
            chatDao.deleteConversation(conv)
        }
    }

    suspend fun clearHistory(conversationId: Int) {
        chatDao.deleteMessagesForConversation(conversationId)
        val conv = chatDao.getConversationById(conversationId)
        if (conv != null) {
            chatDao.updateConversation(conv.copy(lastMessage = "", updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun getActiveProvider(): AiProvider {
        val config = chatDao.getActiveProvider()
        return when {
            config == null -> {
                GeminiAiProvider()
            }
            config.isDefault && config.name == "Gemini" -> {
                val key = if (config.apiKey.isNotBlank()) config.apiKey else com.example.BuildConfig.GEMINI_API_KEY
                GeminiAiProvider(apiKey = key)
            }
            config.isDefault && config.name == "OpenRouter" -> {
                val key = if (config.apiKey.isNotBlank()) config.apiKey else com.example.BuildConfig.OPENROUTER_API_KEY
                val model = if (!config.modelId.isNullOrBlank()) config.modelId else "google/gemini-2.5-flash:free"
                OpenRouterAiProvider(apiKey = key, customModel = model)
            }
            else -> {
                CustomAiProvider(
                    name = config.name,
                    apiKey = config.apiKey,
                    baseUrl = config.baseUrl ?: "https://api.openai.com/v1/",
                    model = config.modelId ?: "gpt-4"
                )
            }
        }
    }

    suspend fun setActiveProvider(config: AiProviderConfig) {
        chatDao.deactivateAllProviders()
        chatDao.updateProviderConfig(config.copy(isActive = true))
    }

    suspend fun addProvider(config: AiProviderConfig): Long {
        return chatDao.insertProviderConfig(config)
    }

    suspend fun updateProvider(config: AiProviderConfig) {
        chatDao.updateProviderConfig(config)
    }
}
