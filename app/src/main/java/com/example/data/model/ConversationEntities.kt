package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val modelTag: String = "Qwen2.5-1.5B (Offline)"
)

@Entity(tableName = "chat_history_messages")
data class ChatMessageEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val sender: String, // "USER", "ASSISTANT", "SYSTEM_ALERT"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val modelTag: String? = null,
    val tokenCount: Int = 0,
    val imageTag: String? = null
)
