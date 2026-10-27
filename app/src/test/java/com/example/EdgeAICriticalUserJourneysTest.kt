package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.database.AppDatabase
import com.example.model.ModelType
import com.example.service.AIModelManager
import com.example.service.LocalEmbeddingEngine
import com.example.service.MemoryMonitor
import com.example.service.RAGPipeline
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class EdgeAICriticalUserJourneysTest {

    private lateinit var context: Context
    private lateinit var memoryMonitor: MemoryMonitor
    private lateinit var modelManager: AIModelManager
    private lateinit var database: AppDatabase
    private lateinit var embeddingEngine: LocalEmbeddingEngine
    private lateinit var ragPipeline: RAGPipeline

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        memoryMonitor = MemoryMonitor(context)
        modelManager = AIModelManager(memoryMonitor)
        database = AppDatabase.getInstance(context)
        embeddingEngine = LocalEmbeddingEngine()
        ragPipeline = RAGPipeline(database.documentDao(), embeddingEngine, modelManager)
    }

    @After
    fun tearDown() {
        runBlocking {
            database.documentDao().clearAllDocuments()
            database.documentDao().clearAllChunks()
            modelManager.unloadActiveModel()
        }
    }

    @Test
    fun testZeroConcurrencyModelTransitions() {
        runBlocking {
            // Initial state: No model loaded
            assertNull(modelManager.activeModel.value)

            // Step 1: Load Model 1 (Reasoning: Qwen2.5-1.5B)
            val loadedReasoning = modelManager.loadModel(ModelType.REASONING)
            assertTrue(loadedReasoning)
            assertEquals(ModelType.REASONING, modelManager.activeModel.value)

            // Step 2: Switch to Model 3 (Embedding: all-MiniLM-L6-v2)
            // Zero-concurrency: ModelManager MUST unload Reasoning first, then load Embedding
            val loadedEmbedding = modelManager.loadModel(ModelType.EMBEDDING)
            assertTrue(loadedEmbedding)
            assertEquals(ModelType.EMBEDDING, modelManager.activeModel.value)

            // Step 3: Switch to Model 2 (Vision OCR: ML Kit)
            val loadedOcr = modelManager.loadModel(ModelType.VISION_OCR)
            assertTrue(loadedOcr)
            assertEquals(ModelType.VISION_OCR, modelManager.activeModel.value)

            // Step 4: Explicit unload restores Idle state
            modelManager.unloadActiveModel()
            assertNull(modelManager.activeModel.value)

            // Verify transition logs captured all swaps
            val logs = modelManager.transitionLogs.value
            assertTrue("Expected multiple transition audit logs", logs.size >= 4)
        }
    }

    @Test
    fun testContextWindowCeiling() {
        // Within budget
        val safeBudget = modelManager.updateContextUsage(512)
        assertTrue(safeBudget)
        assertEquals(512, modelManager.contextTokensUsed.value)

        // Max limit boundary
        val maxBudget = modelManager.updateContextUsage(1024)
        assertTrue(maxBudget)
        assertEquals(1024, modelManager.contextTokensUsed.value)

        // Over-budget is clamped to 1024 and flags false
        val overBudget = modelManager.updateContextUsage(1200)
        assertFalse("Tokens exceeding 1024 should fail validation", overBudget)
        assertEquals(1024, modelManager.contextTokensUsed.value)
    }

    @Test
    fun testLocalRAGPipelineVectorRetrieval() {
        runBlocking {
            val documentText = """
                TechEdge Financial Report Q3 2025.
                Total revenue reached 14.2 million dollars, an increase of 28 percent year over year.
                Operating expenses were reduced to 8.6 million dollars through efficient edge computing.
                Net income was 5.6 million dollars. Cash equivalents stood at 22.4 million dollars.
                Patient was discharged with normal vital signs. BP was 120 over 80 mmHg.
                Prescription medications include Amoxicillin 500mg capsules and oral electrolyte salts.
            """.trimIndent()

            // Ingest and vectorize into SQLite
            val docId = ragPipeline.ingestDocument(
                title = "Test_Report.txt",
                fileType = "TXT",
                rawContent = documentText
            )
            assertNotNull(docId)

            // Verify document and chunks stored in Room SQLite database
            val docs = database.documentDao().getDocumentById(docId)
            assertNotNull(docs)
            assertEquals("Test_Report.txt", docs?.title)

            val chunks = database.documentDao().getChunksForDocument(docId)
            assertTrue(chunks.isNotEmpty())

            // Query 1: Financial revenue
            val finQuery = "What was the total revenue in Q3?"
            val finResults = ragPipeline.retrieveRelevantChunks(finQuery, topK = 1)
            assertTrue("Expected financial excerpt retrieved", finResults.isNotEmpty())
            assertTrue(finResults.first().chunk.content.contains("revenue", ignoreCase = true))

            // Query 2: Medical prescription
            val medQuery = "What medication was prescribed to the patient?"
            val medResults = ragPipeline.retrieveRelevantChunks(medQuery, topK = 1)
            assertTrue("Expected medical excerpt retrieved", medResults.isNotEmpty())
            assertTrue(medResults.first().chunk.content.contains("Amoxicillin", ignoreCase = true))

            // Prompt formatting within 1024 token budget
            val (prompt, estTokens) = ragPipeline.buildContextPrompt(finQuery, finResults)
            assertTrue(prompt.contains("RETRIEVED LOCAL EXCERPTS"))
            assertTrue("Estimated prompt tokens should be well below 1024", estTokens < 750)
        }
    }

    @Test
    fun testMemoryMonitorReportsRealTelemetry() {
        val snapshot = memoryMonitor.getSnapshot()
        assertTrue("Total RAM must be greater than 0", snapshot.totalRamMB > 0)
        assertTrue("Available RAM must be greater than 0", snapshot.availRamMB > 0)
        assertTrue("Threshold must be greater than 0", snapshot.thresholdMB > 0)
        assertTrue("Max heap should be allocated", snapshot.heapMaxMB > 0)
    }
}
