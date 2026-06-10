package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val role: String,              // "user" or "ai"
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val sourcesJson: String? = null // JSON array of article IDs, for AI responses
)
