package com.example.db

import androidx.room.*
import java.io.Serializable

@Entity(tableName = "chat_conversations")
data class ChatConversation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val lastMessage: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,
    val activeProviderId: Int? = null // Link to AiProviderConfig if applicable
) : Serializable

@Entity(
    tableName = "chat_messages",
    foreignKeys = [
        ForeignKey(
            entity = ChatConversation::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("conversationId")]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val conversationId: Int,
    val role: String, // "user", "assistant"
    val content: String,
    val mediaUrl: String? = null,
    val mediaType: String? = null, // "image", "video", "text"
    val createdAt: Long = System.currentTimeMillis()
) : Serializable

@Entity(tableName = "ai_provider_configs")
data class AiProviderConfig(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val apiKey: String,
    val baseUrl: String? = null,
    val modelId: String? = null,
    val isActive: Boolean = false,
    val isDefault: Boolean = false
) : Serializable
