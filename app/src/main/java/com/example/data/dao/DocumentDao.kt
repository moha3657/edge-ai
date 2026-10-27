package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ChunkEntity
import com.example.data.model.DocumentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DocumentDao {

    @Query("SELECT * FROM documents ORDER BY timestamp DESC")
    fun getAllDocuments(): Flow<List<DocumentEntity>>

    @Query("SELECT * FROM documents WHERE id = :id")
    suspend fun getDocumentById(id: String): DocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDocument(doc: DocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChunks(chunks: List<ChunkEntity>)

    @Query("SELECT * FROM document_chunks WHERE documentId = :docId ORDER BY chunkIndex ASC")
    suspend fun getChunksForDocument(docId: String): List<ChunkEntity>

    @Query("SELECT * FROM document_chunks")
    suspend fun getAllChunks(): List<ChunkEntity>

    @Query("DELETE FROM documents WHERE id = :docId")
    suspend fun deleteDocument(docId: String)

    @Query("DELETE FROM document_chunks WHERE documentId = :docId")
    suspend fun deleteChunksForDocument(docId: String)

    @Query("DELETE FROM documents")
    suspend fun clearAllDocuments()

    @Query("DELETE FROM document_chunks")
    suspend fun clearAllChunks()
}
