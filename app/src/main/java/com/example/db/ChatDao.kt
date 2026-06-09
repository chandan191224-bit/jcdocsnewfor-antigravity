package com.example.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    // --- Conversations ---
    @Query("SELECT * FROM chat_conversations WHERE isArchived = 0 ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ChatConversation>>

    @Query("SELECT * FROM chat_conversations WHERE id = :id")
    suspend fun getConversationById(id: Int): ChatConversation?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ChatConversation): Long

    @Update
    suspend fun updateConversation(conversation: ChatConversation)

    @Delete
    suspend fun deleteConversation(conversation: ChatConversation)

    // --- Messages ---
    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun getMessagesForConversation(conversationId: Int): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    suspend fun getMessagesList(conversationId: Int): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("DELETE FROM chat_messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: Int)

    // --- Provider Configs ---
    @Query("SELECT * FROM ai_provider_configs")
    fun getAllProviderConfigs(): Flow<List<AiProviderConfig>>

    @Query("SELECT * FROM ai_provider_configs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProvider(): AiProviderConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProviderConfig(config: AiProviderConfig): Long

    @Update
    suspend fun updateProviderConfig(config: AiProviderConfig)

    @Delete
    suspend fun deleteProviderConfig(config: AiProviderConfig)

    @Query("UPDATE ai_provider_configs SET isActive = 0")
    suspend fun deactivateAllProviders()
}
