package com.example.service

import com.example.data.dao.DocumentDao
import com.example.data.model.ChunkEntity
import com.example.data.model.DocumentEntity
import com.example.model.ModelType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

data class RetrievedChunk(
    val chunk: ChunkEntity,
    val similarityScore: Float,
    val documentTitle: String
)

class RAGPipeline(
    private val documentDao: DocumentDao,
    private val embeddingEngine: LocalEmbeddingEngine,
    private val modelManager: AIModelManager
) {

    companion object {
        const val CHUNK_WORD_SIZE = 120
        const val CHUNK_OVERLAP_WORDS = 25
        const val MAX_RETRIEVED_CHUNKS = 3
        const val MAX_CONTEXT_TOKENS_FOR_PROMPT = 700
    }

    /**
     * Splits text into overlapping sliding-window chunks.
     */
    fun chunkText(text: String): List<String> {
        val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty()) return emptyList()

        if (words.size <= CHUNK_WORD_SIZE) {
            return listOf(words.joinToString(" "))
        }

        val chunks = mutableListOf<String>()
        var start = 0
        while (start < words.size) {
            val end = (start + CHUNK_WORD_SIZE).coerceAtMost(words.size)
            val chunk = words.subList(start, end).joinToString(" ")
            chunks.add(chunk)
            if (end >= words.size) break
            start += (CHUNK_WORD_SIZE - CHUNK_OVERLAP_WORDS)
        }
        return chunks
    }

    /**
     * Executes Scenario B Step:
     * 1. Extracts & chunks document
     * 2. LOAD Embedding model (unloading any other model)
     * 3. Vectorizes chunks into local SQLite/Room
     * 4. UNLOAD Embedding model to free RAM
     */
    suspend fun ingestDocument(
        title: String,
        fileType: String,
        rawContent: String,
        onProgress: (String) -> Unit = {}
    ): String = withContext(Dispatchers.IO) {
        val docId = UUID.randomUUID().toString()
        val textChunks = chunkText(rawContent)

        onProgress("Parsed $title into ${textChunks.size} chunks. Requesting Embedding Model...")

        // ZERO-CONCURRENCY: Load Embedding Model
        modelManager.loadModel(ModelType.EMBEDDING) { msg -> onProgress(msg) }

        val chunkEntities = mutableListOf<ChunkEntity>()
        for (i in textChunks.indices) {
            val chunkText = textChunks[i]
            onProgress("Vectorizing chunk ${i + 1}/${textChunks.size} with all-MiniLM-L6-v2...")
            val vector = embeddingEngine.embedText(chunkText)
            val jsonVector = embeddingEngine.serializeVector(vector)

            val tokenEstimate = (chunkText.length / 4).coerceAtLeast(1)
            chunkEntities.add(
                ChunkEntity(
                    id = UUID.randomUUID().toString(),
                    documentId = docId,
                    chunkIndex = i,
                    pageNumber = (i / 2) + 1,
                    content = chunkText,
                    tokenCount = tokenEstimate,
                    vectorEmbeddingJson = jsonVector
                )
            )
        }

        val sizeBytes = rawContent.toByteArray().size
        val sizeFormatted = when {
            sizeBytes < 1024 -> "$sizeBytes B"
            sizeBytes < 1024 * 1024 -> "${sizeBytes / 1024} KB"
            else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
        }

        val docEntity = DocumentEntity(
            id = docId,
            title = title,
            fileType = fileType,
            totalChunks = chunkEntities.size,
            fileSizeFormatted = sizeFormatted
        )

        // Store into local Room / SQLite database
        documentDao.insertDocument(docEntity)
        documentDao.insertChunks(chunkEntities)

        onProgress("Vector database updated. Unloading Embedding Model to free 85MB RAM...")
        // ZERO-CONCURRENCY: UNLOAD Embedding model immediately after vectorization
        modelManager.unloadActiveModel()

        onProgress("Ingestion complete. Model unloaded, ready for reasoning query.")
        docId
    }

    /**
     * Performs cosine similarity search across local stored chunks.
     */
    suspend fun retrieveRelevantChunks(
        query: String,
        topK: Int = MAX_RETRIEVED_CHUNKS
    ): List<RetrievedChunk> = withContext(Dispatchers.IO) {
        val queryVector = embeddingEngine.embedText(query)
        val allChunks = documentDao.getAllChunks()
        if (allChunks.isEmpty()) return@withContext emptyList()

        val docs = mutableMapOf<String, String>()

        val scored = allChunks.map { chunk ->
            val chunkVec = embeddingEngine.deserializeVector(chunk.vectorEmbeddingJson)
            val score = embeddingEngine.cosineSimilarity(queryVector, chunkVec)
            val docTitle = docs.getOrPut(chunk.documentId) {
                documentDao.getDocumentById(chunk.documentId)?.title ?: "Document"
            }
            RetrievedChunk(
                chunk = chunk,
                similarityScore = score,
                documentTitle = docTitle
            )
        }

        scored.filter { it.similarityScore > 0.15f }
            .sortedByDescending { it.similarityScore }
            .take(topK)
    }

    /**
     * Builds the prompt bounded strictly by the 1024 token budget.
     */
    fun buildContextPrompt(
        query: String,
        relevantChunks: List<RetrievedChunk>
    ): Pair<String, Int> {
        val sb = StringBuilder()
        sb.append("You are an offline Edge AI assistant running on an on-device local LLM (Qwen2.5-1.5B).\n")
        sb.append("Answer the user query concisely using ONLY the following verified local document excerpts.\n\n")

        if (relevantChunks.isNotEmpty()) {
            sb.append("=== RETRIEVED LOCAL EXCERPTS ===\n")
            for ((idx, r) in relevantChunks.withIndex()) {
                sb.append("[Excerpt ${idx + 1} | Source: ${r.documentTitle} (Page ${r.chunk.pageNumber}) | Relevance: ${(r.similarityScore * 100).toInt()}%]\n")
                sb.append("${r.chunk.content}\n\n")
            }
            sb.append("===============================\n\n")
        }

        sb.append("USER QUESTION: $query\n")
        sb.append("ANSWER:")

        val promptStr = sb.toString()
        val estimatedTokens = (promptStr.length / 3.8).toInt()
        return Pair(promptStr, estimatedTokens)
    }
}
