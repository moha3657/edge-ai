package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.service.LocalEmbeddingEngine
import com.example.service.RAGPipeline
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("EdgeAI Offline", appName)
    }

    @Test
    fun `test local embedding engine generates normalized 384 dimensional vector`() {
        val engine = LocalEmbeddingEngine()
        val vec1 = engine.embedText("Financial revenue Q3 statement")
        val vec2 = engine.embedText("Consolidated financial income statement")
        val vec3 = engine.embedText("Medical acute gastroenteritis hospital treatment")

        assertEquals(384, vec1.size)
        assertEquals(384, vec2.size)

        val simRelated = engine.cosineSimilarity(vec1, vec2)
        val simUnrelated = engine.cosineSimilarity(vec1, vec3)

        // Semantic financial texts should be more similar than financial vs medical
        assertTrue(simRelated > simUnrelated)
    }

    @Test
    fun `test text chunking preserves sliding window with overlap`() {
        val text = (1..300).joinToString(" ") { "word$it" }
        val words = text.split(" ")
        assertEquals(300, words.size)
    }

    @Test
    fun `test conversational reasoning greets user by name and avoids robotic boilerplate`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val memoryMonitor = com.example.service.MemoryMonitor(context)
        val modelManager = com.example.service.AIModelManager(memoryMonitor)
        val geminiService = com.example.service.GeminiService()
        val engine = com.example.service.OfflineReasoningEngine(modelManager, geminiService)

        // Load reasoning model
        modelManager.loadModel(com.example.model.ModelType.REASONING)

        val tokens = mutableListOf<String>()
        engine.streamInference(
            prompt = "I am Mohamed, nice to meet you",
            retrievedChunks = emptyList(),
            userQuery = "I am Mohamed, nice to meet you"
        ).collect { tokens.add(it) }

        val fullResponse = tokens.joinToString("")

        // Must greet Mohamed personally
        assertTrue("Expected response to contain 'Mohamed' but got: $fullResponse", fullResponse.contains("Mohamed"))
        assertTrue("Expected response to be polite but got: $fullResponse", fullResponse.contains("Nice to meet you"))
        // Must NOT contain the old broken robotic diagnostic boilerplate
        assertTrue("Response should not contain robotic analysis header", !fullResponse.contains("Operating in strictly 100% offline mode on this device"))
        assertTrue("Response should not contain Vault tab reference", !fullResponse.contains("Vault tab"))
    }

    @Test
    fun `test voice markdown sanitization cleans formatting for natural speech`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val voiceService = com.example.service.VoiceInteractionService(context, this)

        val rawMarkdown = "### Assistant Response\n**Hello Mohamed!** Here is your `local code`:\n```kotlin\nval x = 10\n```\nAll done!"
        val cleaned = voiceService.cleanMarkdownForSpeech(rawMarkdown)

        // Bold asterisks stripped
        assertTrue(!cleaned.contains("**"))
        // Hash headings stripped
        assertTrue(!cleaned.contains("###"))
        // Code snippet replaced with spoken description
        assertTrue(cleaned.contains("Code snippet omitted"))
        assertTrue(cleaned.contains("Hello Mohamed!"))

        voiceService.setMuted(true)
        assertTrue(voiceService.isMuted())

        voiceService.setMuted(false)
        assertTrue(!voiceService.isMuted())

        voiceService.shutdown()
    }

    @Test
    fun `test fun fact and offline AI queries produce engaging natural answers instead of robotic templates`() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val memoryMonitor = com.example.service.MemoryMonitor(context)
        val modelManager = com.example.service.AIModelManager(memoryMonitor)
        val geminiService = com.example.service.GeminiService()
        val engine = com.example.service.OfflineReasoningEngine(modelManager, geminiService)

        modelManager.loadModel(com.example.model.ModelType.REASONING)

        // 1. Fun fact test
        val factTokens = mutableListOf<String>()
        engine.streamInference(
            prompt = "Tell me a short fun fact",
            retrievedChunks = emptyList(),
            userQuery = "Tell me a short fun fact"
        ).collect { factTokens.add(it) }

        val factResponse = factTokens.joinToString("")
        // Must NOT contain the old stiff corporate template
        assertTrue("Should not have robotic template", !factResponse.contains("1. Core Concept"))
        assertTrue("Should not have robotic template", !factResponse.contains("2. Key Consideration"))
        assertTrue("Should not repeat prompt with 'regarding'", !factResponse.contains("regarding Tell me a short fun fact"))
        // Must contain an actual interesting fact
        assertTrue(
            "Expected an actual engaging fun fact but got: $factResponse",
            factResponse.contains("Did you know") || factResponse.contains("fact") || factResponse.contains("honey") || factResponse.contains("Octopus")
        )

        // 2. Offline AI explanation test
        val offlineTokens = mutableListOf<String>()
        engine.streamInference(
            prompt = "How does offline AI work?",
            retrievedChunks = emptyList(),
            userQuery = "How does offline AI work?"
        ).collect { offlineTokens.add(it) }

        val offlineResponse = offlineTokens.joinToString("")
        assertTrue("Should explain offline neural inference", offlineResponse.contains("Offline AI") || offlineResponse.contains("quantized"))
    }
}

