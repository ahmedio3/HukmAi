package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.example.data.model.ChatMessage
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAllChatMessages(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    suspend fun getAllChatMessagesSync(): List<ChatMessage>

    @Insert
    suspend fun insertChatMessage(message: ChatMessage)

    @Query("DELETE FROM chat_messages WHERE id = (SELECT id FROM chat_messages WHERE role = 'ai' ORDER BY timestamp DESC LIMIT 1)")
    suspend fun deleteLastAiMessage()

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllChatMessages()
}
