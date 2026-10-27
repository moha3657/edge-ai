package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val title: String,
    val fileType: String, // "PDF", "TXT", "IMAGE_OCR"
    val totalChunks: Int,
    val fileSizeFormatted: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "document_chunks")
data class ChunkEntity(
    @PrimaryKey val id: String,
    val documentId: String,
    val chunkIndex: Int,
    val pageNumber: Int,
    val content: String,
    val tokenCount: Int,
    val vectorEmbeddingJson: String // Serialized float array for 384-dim semantic embedding
)
