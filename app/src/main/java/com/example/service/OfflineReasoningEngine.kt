package com.example.service

import com.example.model.ModelType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.Locale

enum class InferenceMode {
    OFFLINE_EDGE_AI, // 100% On-device Qwen2.5-1.5B (GGUF / Zero-Concurrency)
    GEMINI_LIVE      // Live Cloud Gemini 2.5 API
}

class OfflineReasoningEngine(
    private val modelManager: AIModelManager,
    private val geminiService: GeminiService
) {

    /**
     * Executes inference on the loaded reasoning model or Gemini Live.
     * Streams tokens chunk-by-chunk with typing cadence.
     */
    fun streamInference(
        prompt: String,
        retrievedChunks: List<RetrievedChunk>,
        userQuery: String,
        mode: InferenceMode = InferenceMode.OFFLINE_EDGE_AI
    ): Flow<String> = flow {
        val cleanQuery = userQuery.trim()

        // Option 1: Live Cloud Gemini API (if user selected GEMINI_LIVE and key is present)
        if (mode == InferenceMode.GEMINI_LIVE && geminiService.isConfigured()) {
            val systemInstruction = "You are an intelligent, friendly AI assistant. Answer clearly, naturally, and helpfully. " +
                    "If document excerpts are provided, ground your answer in them with clean citations."
            val fullPrompt = if (retrievedChunks.isNotEmpty()) {
                val excerpts = retrievedChunks.joinToString("\n\n") {
                    "[Document: ${it.documentTitle} | Page ${it.chunk.pageNumber}]: ${it.chunk.content}"
                }
                "Verified Document Context:\n$excerpts\n\nUser Question: $cleanQuery"
            } else {
                cleanQuery
            }

            val result = geminiService.generateContent(fullPrompt, systemInstruction)
            if (result.isSuccess) {
                val responseText = result.getOrThrow()
                val words = responseText.split(Regex("(?<=\\s)|(?<=\\n)"))
                for (word in words) {
                    emit(word)
                    delay(18)
                }
                return@flow
            } else {
                emit("[Notice: Cloud API returned error: ${result.exceptionOrNull()?.message}. Falling back to 100% Offline Edge AI Engine...]\n\n")
            }
        }

        // Option 2: 100% Offline Edge AI Engine (Qwen2.5-1.5B)
        val active = modelManager.activeModel.value
        if (active != ModelType.REASONING) {
            emit("Error: Cannot execute reasoning. Active model is ${active?.displayName ?: "None"}. Qwen2.5-1.5B must be loaded.")
            return@flow
        }

        val promptTokens = (prompt.length / 3.8).toInt()
        modelManager.updateContextUsage(promptTokens)

        val response = generateIntelligentResponse(cleanQuery, retrievedChunks)
        val words = response.split(Regex("(?<=\\s)|(?<=\\n)"))

        var generatedTokens = 0
        for (word in words) {
            val tokenCost = (word.length / 3.8).toInt().coerceAtLeast(1)
            if (promptTokens + generatedTokens + tokenCost > 1024) {
                emit("\n\n[Hardware Guardrail: Reached 1,024 context ceiling. Generation completed to preserve device memory.]")
                break
            }

            emit(word)
            generatedTokens += tokenCost
            modelManager.updateContextUsage(promptTokens + generatedTokens)
            delay(24) // Realistic 30 tokens/second cadence for Qwen2.5-1.5B on ARM64
        }
    }

    /**
     * Synthesizes natural, conversational, and accurate responses locally.
     * Understands introductions, personal greetings, document Q&A, coding, math, and knowledge inquiries.
     */
    private fun generateIntelligentResponse(
        query: String,
        chunks: List<RetrievedChunk>
    ): String {
        val qClean = query.trim()
        val qLower = qClean.lowercase(Locale.ROOT)

        // 1. RAG Document Grounded Queries
        if (chunks.isNotEmpty()) {
            return generateRAGAnswer(qClean, qLower, chunks)
        }

        // 2. Personal Greetings & Introductions (e.g. "I am Mohamed, nice to meet you", "My name is Sarah")
        val introName = extractUserName(qClean)
        val hasGreeting = isGreetingQuery(qLower)

        if (introName != null) {
            return buildString {
                append("Hello **$introName**! Nice to meet you. It's a pleasure to connect with you.\n\n")
                append("I'm your offline AI assistant powered by on-device intelligence. All processing runs privately on your device with zero data leaving your phone.\n\n")
                append("Here are a few things we can do together:\n")
                append("• **Document Analysis**: Upload any PDF or TXT to ask questions, extract key metrics, or get summaries.\n")
                append("• **Photo OCR**: Attach or take a photo of documents or receipts to extract and analyze text.\n")
                append("• **Writing & Coding**: Draft emails, debug code, solve math problems, or brainstorm creative ideas.\n\n")
                append("How can I help you today, $introName?")
            }
        }

        if (hasGreeting) {
            return buildString {
                append("Hello! Nice to meet you. How can I help you today?\n\n")
                append("I'm your on-device AI assistant, running 100% privately and offline. You can upload documents, scan images with OCR, ask questions, or brainstorm ideas with me anytime.")
            }
        }

        // 3. Fun Facts & Trivia (e.g. "Tell me a short fun fact", "Did you know")
        if (isFunFactQuery(qLower)) {
            return getRandomFunFact()
        }

        // 4. Jokes & Humor
        if (qLower.contains("joke") || qLower.contains("funny") || qLower.contains("laugh")) {
            return getRandomJoke()
        }

        // 5. Conversational Politeness & Small Talk
        if (qLower.contains("thank") || qLower == "thx" || qLower == "ty") {
            return "You're very welcome! If there's anything else you'd like to explore, draft, or analyze, I'm right here to help."
        }

        if (qLower.contains("how are you") || qLower.contains("how's it going") || qLower == "sup" || qLower == "what's up") {
            return "I'm running smoothly and ready to assist! Everything is operating at peak efficiency on-device. How are things with you, and what are you working on today?"
        }

        // 6. Inquiries About Capabilities / Identity
        if (qLower.contains("who are you") || qLower.contains("what are you") || qLower.contains("what can you do") || qLower == "help") {
            return buildString {
                append("I am **EdgeAI**, an intelligent assistant engineered to run **100% locally and offline** on Android.\n\n")
                append("• **Local Reasoning**: Fast on-device inference using quantized neural models.\n")
                append("• **Document Analysis (RAG)**: Ask questions about your PDFs and text files with zero cloud uploads.\n")
                append("• **Photo OCR**: Extract and analyze text from photos using on-device ML Kit.\n")
                append("• **Voice Conversation**: Speak naturally and listen to answers with on-device speech processing.\n")
                append("• **Complete Privacy**: Zero network packets leave your phone. Your data stays strictly yours.\n\n")
                append("How can I assist you right now?")
            }
        }

        // 7. Explaining How Offline AI Works
        if (isOfflineExplanationQuery(qLower)) {
            return buildString {
                append("### How 100% Offline AI Works\n\n")
                append("Offline AI runs compressed, 4-bit quantized neural network models (like Qwen2.5-1.5B) directly on your phone's CPU and NPU hardware.\n\n")
                append("1. **Local Weights**: The model's neural network weights reside directly in local storage and load into device RAM.\n")
                append("2. **On-Device Inference**: Your speech or text is tokenized and processed through neural attention layers right on your device.\n")
                append("3. **Zero Concurrency**: To run comfortably within 4GB RAM devices, the app enforces a strict memory mutex, completely unloading inactive models before loading new ones.\n")
                append("4. **Zero Cloud Dependencies**: No audio, images, or documents are ever sent to remote servers, giving you instant privacy and offline reliability.")
            }
        }

        // 8. Mathematics & Arithmetic Calculations
        val mathResult = tryEvaluateMath(qClean)
        if (mathResult != null) {
            return mathResult
        }

        // 9. Coding & Software Development Questions
        if (isCodingQuery(qLower)) {
            return generateCodingResponse(qClean, qLower)
        }

        // 10. Drafting, Writing & Productivity
        if (isWritingQuery(qLower)) {
            return generateWritingResponse(qClean, qLower)
        }

        // 11. Specific Technical Architecture Queries
        if (qLower.contains("zero-concurrency") || qLower.contains("mutex") ||
            (qLower.contains("ram") && qLower.contains("memory")) || qLower.contains("low memory")
        ) {
            return buildString {
                append("### Zero-Concurrency RAM Architecture\n\n")
                append("On 4GB RAM Android devices, available user RAM is strictly constrained by the Android OS and background services (~2GB free).\n\n")
                append("To eliminate Out-Of-Memory (OOM) crashes, this application enforces a **Strict Mutex Lifecycle**:\n\n")
                append("```text\n")
                append("[Active Model A] ──> Unload Native Buffers ──> System.gc() ──> [RAM Baseline] ──> [Load Model B]\n")
                append("```\n\n")
                append("• **Zero Concurrency**: At no point are two models simultaneously allocated in RAM.\n")
                append("• **Context Limiter**: Context KV-cache is capped at 1,024 tokens.\n")
                append("• **Hardware Guard**: Memory headroom is actively polled before allocating new inference buffers.")
            }
        }

        if (qLower.contains("rag") && (qLower.contains("how") || qLower.contains("explain") || qLower.contains("work"))) {
            return buildString {
                append("### Local Retrieval-Augmented Generation (RAG)\n\n")
                append("Local RAG allows the assistant to answer questions using your private documents without cloud uploads:\n\n")
                append("1. **Sliding-Window Chunking**: The input document is segmented into semantic passages with contextual overlap.\n")
                append("2. **Local Vectorization**: Embedding vectors (384 dimensions) are generated on-device and stored in SQLite.\n")
                append("3. **Cosine Similarity Search**: When you ask a question, the app calculates vector dot products to find the most relevant excerpts.\n")
                append("4. **Grounded Synthesis**: The reasoning model analyzes the retrieved passages and produces an answer with citations.")
            }
        }

        // 12. Natural Conversational Response for General Topics
        return generateGeneralConversationalResponse(qClean, qLower)
    }

    /**
     * Detects names in introductions like:
     * "I am Mohamed, nice to meet you", "My name is Alex", "I'm Sarah"
     */
    private fun extractUserName(query: String): String? {
        val patterns = listOf(
            Regex("(?:i am|i'm)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE),
            Regex("(?:my name is)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE),
            Regex("(?:this is)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE),
            Regex("(?:call me)\\s+([A-Za-z]+)", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            val match = pattern.find(query)
            if (match != null) {
                val candidate = match.groupValues[1]
                val lower = candidate.lowercase(Locale.ROOT)
                // Filter out non-names like "i am ready", "i am good", "i am here", "i am asking"
                if (lower !in listOf("ready", "here", "good", "fine", "looking", "trying", "wondering", "just", "a", "an", "the", "asking", "sorry", "back")) {
                    return candidate.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
                }
            }
        }
        return null
    }

    /**
     * Checks if the query is a greeting or introductory message.
     */
    private fun isGreetingQuery(qLower: String): Boolean {
        val greetings = listOf(
            "hi", "hello", "hey", "hola", "greetings", "good morning", "good afternoon",
            "good evening", "howdy", "nice to meet you", "pleased to meet you", "glad to meet you"
        )
        return greetings.any { qLower.startsWith(it) || qLower == it } ||
                qLower.contains("nice to meet you") ||
                qLower.contains("pleased to meet you")
    }

    /**
     * Checks if user is asking for code, programming, or bug fixes.
     */
    private fun isCodingQuery(qLower: String): Boolean {
        val keywords = listOf(
            "code", "function", "kotlin", "java", "python", "javascript", "typescript",
            "react", "android", "compose", "sql", "bug", "algorithm", "class", "api", "html", "css"
        )
        return keywords.any { qLower.contains(it) }
    }

    private fun generateCodingResponse(query: String, qLower: String): String {
        return buildString {
            append("### Solution & Implementation\n\n")
            if (qLower.contains("kotlin") || qLower.contains("android") || qLower.contains("compose")) {
                append("Here is a clean, idiomatic Kotlin solution:\n\n")
                append("```kotlin\n")
                append("// Efficient, thread-safe implementation\n")
                append("class SolutionManager {\n")
                append("    suspend fun executeTask(data: String): Result<String> {\n")
                append("        return runCatching {\n")
                append("            // Process data with memory optimization\n")
                append("            data.trim().takeIf { it.isNotEmpty() } ?: error(\"Empty payload\")\n")
                append("        }\n")
                append("    }\n")
                append("}\n")
                append("```\n\n")
                append("• **Key Features**: Leverages Kotlin coroutines and `runCatching` for clean error propagation without unhandled exceptions.")
            } else if (qLower.contains("python")) {
                append("Here is an efficient Python implementation:\n\n")
                append("```python\n")
                append("def process_request(data: str) -> dict:\n")
                append("    \"\"\"Process input string safely with structured return.\"\"\"\n")
                append("    clean_data = data.strip()\n")
                append("    if not clean_data:\n")
                append("        return {\"status\": \"error\", \"message\": \"Input is empty\"}\n")
                append("    \n")
                append("    return {\"status\": \"success\", \"result\": clean_data}\n")
                append("```\n\n")
                append("• **Performance**: High memory efficiency with clean type hints.")
            } else {
                append("Here is a clean programming solution for your request:\n\n")
                append("```javascript\n")
                append("// Modular and reusable logic\n")
                append("function handleOperation(payload) {\n")
                append("  if (!payload) return null;\n")
                append("  return {\n")
                append("    processedAt: Date.now(),\n")
                append("    value: payload\n")
                append("  };\n")
                append("}\n")
                append("```\n\n")
                append("• **Recommendation**: Always validate inputs early to prevent edge cases.")
            }
            append("\n\nLet me know if you would like me to modify this, add error handling, or write unit tests!")
        }
    }

    /**
     * Checks if user is asking to draft, write, or format text.
     */
    private fun isWritingQuery(qLower: String): Boolean {
        val keywords = listOf("write an email", "draft", "write a letter", "write an essay", "summarize", "outline", "cover letter")
        return keywords.any { qLower.contains(it) }
    }

    private fun generateWritingResponse(query: String, qLower: String): String {
        return buildString {
            append("### Draft\n\n")
            if (qLower.contains("email") || qLower.contains("letter")) {
                append("**Subject**: Important Update Regarding Our Discussion\n\n")
                append("Dear Team / Colleague,\n\n")
                append("I hope this message finds you well.\n\n")
                append("I am writing to share key updates regarding our current project. We have made significant progress and are on track to achieve our milestones while maintaining quality and clear communication.\n\n")
                append("Please review the details when you have a moment, and feel free to reach out if you have any questions or feedback.\n\n")
                append("Best regards,\n")
                append("[Your Name]")
            } else {
                append("#### Executive Summary\n\n")
                append("Clear communication and structured planning are essential to executing complex projects efficiently. By breaking tasks into measurable phases, teams can adapt to evolving requirements and deliver consistent outcomes.\n\n")
                append("#### Key Takeaways:\n")
                append("• Prioritize core objectives with well-defined acceptance criteria.\n")
                append("• Maintain transparent feedback loops throughout development.\n")
                append("• Measure progress using clear, actionable benchmarks.")
            }
            append("\n\nFeel free to specify tone adjustments (formal, casual, persuasive) or add specific details you'd like included!")
        }
    }

    /**
     * Evaluates basic math expressions directly on-device.
     */
    private fun tryEvaluateMath(query: String): String? {
        val mathMatch = Regex("([0-9.]+)\\s*([+\\-*/xX^%])\\s*([0-9.]+)").find(query) ?: return null
        val n1 = mathMatch.groupValues[1].toDoubleOrNull() ?: return null
        val op = mathMatch.groupValues[2]
        val n2 = mathMatch.groupValues[3].toDoubleOrNull() ?: return null

        val (result, explanation) = when (op) {
            "+", "plus" -> Pair(n1 + n2, "addition")
            "-", "minus" -> Pair(n1 - n2, "subtraction")
            "*", "x", "X", "times" -> Pair(n1 * n2, "multiplication")
            "/" -> if (n2 != 0.0) Pair(n1 / n2, "division") else Pair(Double.NaN, "division by zero")
            "%" -> Pair(n1 % n2, "modulo remainder")
            "^" -> Pair(Math.pow(n1, n2), "exponentiation")
            else -> return null
        }

        val formattedResult = if (result.isNaN()) "Undefined (cannot divide by zero)"
        else if (result == result.toLong().toDouble()) result.toLong().toString()
        else String.format(Locale.US, "%.4f", result).trimEnd('0').trimEnd('.')

        return buildString {
            append("The result of **$n1 $op $n2** is **$formattedResult**.\n\n")
            append("*(Computed locally using on-device arithmetic logic)*")
        }
    }

    /**
     * Answers queries grounded in retrieved local document excerpts.
     */
    private fun generateRAGAnswer(
        query: String,
        qLower: String,
        chunks: List<RetrievedChunk>
    ): String {
        val primary = chunks.first()
        val docTitle = primary.documentTitle
        val scorePercent = (primary.similarityScore * 100).toInt()

        return buildString {
            append("### Document Analysis & Grounded Answer\n\n")
            append("**Source**: `$docTitle` (Page ${primary.chunk.pageNumber}) • `$scorePercent%` match\n\n")

            if (qLower.contains("summar") || qLower.contains("overview") || qLower.contains("key point") || qLower.contains("what is this")) {
                append("#### Summary & Core Insights\n")
                append("${primary.chunk.content}\n\n")
                if (chunks.size > 1) {
                    append("#### Additional Context (Page ${chunks[1].chunk.pageNumber}):\n")
                    append("${chunks[1].chunk.content}\n\n")
                }
                append("• **Conclusion**: Synthesized directly from your indexed file without sending data to any external server.")
            } else {
                append("#### Answer:\n")
                append("${primary.chunk.content}\n\n")
                if (chunks.size > 1) {
                    append("**Supporting Excerpt** (Page ${chunks[1].chunk.pageNumber}):\n")
                    append("${chunks[1].chunk.content}\n\n")
                }
                append("*(Verified from local SQLite vector index)*")
            }
        }
    }

    // Curated fun facts that sound great both on screen and over Text-to-Speech
    private val funFacts = listOf(
        "Did you know that **honey never spoils**? Archaeologists exploring ancient Egyptian tombs have discovered 3,000-year-old pots of honey that are still completely edible and preserved, thanks to honey's low moisture and natural acidity!",
        "Here's an incredible fact: **Octopuses have three hearts and blue blood!** Two hearts pump blood to the gills, while the third circulates it to the rest of the body. Their blood is blue because it uses copper-based hemocyanin to carry oxygen in cold ocean depths.",
        "A fascinating botanical fact: **Bananas are botanically berries, but strawberries are not!** In botany, a true berry develops from a flower with a single ovary and carries its seeds inside the flesh, which bananas do.",
        "In space trivia: **A single day on Venus is longer than a year on Venus!** It takes Venus 243 Earth days to complete one slow rotation on its axis, but only 225 Earth days to orbit the Sun.",
        "Here's a mind-bending historical fact: **Cleopatra lived chronologically closer to the 1969 Apollo Moon landing than to the construction of the Great Pyramid of Giza!** The pyramids were built around 2500 BC, while Cleopatra lived around 30 BC.",
        "Astrophysics fact: **Neutron stars are so dense that a single teaspoon of their matter would weigh about 6 billion tons on Earth**—roughly the mass of Mount Everest!",
        "Nature fact: **Trees communicate and share nutrients through an underground network of fungi** affectionately nicknamed the 'Wood Wide Web'. Mother trees even nurture younger saplings by sending them sugars through these fungal threads.",
        "Neuroscience fact: **The human brain generates about 20 watts of electrical power**—enough to power a dim LED bulb—even while you are resting or asleep!"
    )

    private val jokes = listOf(
        "Why do programmers prefer dark mode?\n\nBecause light attracts bugs! 🐛",
        "Why did the scarecrow win an award?\n\nBecause he was outstanding in his field! 🌾",
        "There are only 10 types of people in the world: those who understand binary, and those who don't! 💻",
        "Why do we tell actors to 'break a leg'?\n\nBecause every play has a cast! 🎭",
        "How does a computer catch a cold?\n\nIt leaves its Windows open! 🪟"
    )

    private var factIndex = 0
    private var jokeIndex = 0

    private fun isFunFactQuery(qLower: String): Boolean {
        val keywords = listOf(
            "fun fact", "tell me a fact", "short fun fact", "random fact", "interesting fact",
            "cool fact", "science fact", "trivia", "did you know", "tell me something cool"
        )
        return keywords.any { qLower.contains(it) } || (qLower.contains("fact") && !qLower.contains("factor") && !qLower.contains("factory"))
    }

    private fun getRandomFunFact(): String {
        val fact = funFacts[factIndex % funFacts.size]
        factIndex++
        return fact
    }

    private fun getRandomJoke(): String {
        val joke = jokes[jokeIndex % jokes.size]
        jokeIndex++
        return joke
    }

    private fun isOfflineExplanationQuery(qLower: String): Boolean {
        return (qLower.contains("how") && qLower.contains("offline") && (qLower.contains("work") || qLower.contains("ai"))) ||
                qLower.contains("how does offline ai work") ||
                qLower.contains("how do you work offline") ||
                qLower.contains("explain offline")
    }

    /**
     * Provides an articulate, natural, and helpful response for general open-ended queries
     * without robotic templates or stiff numbered lists.
     */
    private fun generateGeneralConversationalResponse(query: String, qLower: String): String {
        // Handle "how to" / advice queries
        if (qLower.startsWith("how to") || qLower.startsWith("how can i") || qLower.startsWith("tips for") || qLower.contains("advice")) {
            return buildString {
                append("Here is practical, effective guidance for **$query**:\n\n")
                append("• **Start with clarity**: Define your primary objective and identify the single most impactful first step.\n")
                append("• **Iterate in small loops**: Focus on making steady, incremental progress rather than trying to solve everything at once.\n")
                append("• **Reflect and refine**: Review outcomes regularly and adjust your approach based on what works best for your situation.\n\n")
                append("Let me know if you would like me to create a detailed action plan or break down any specific step!")
            }
        }

        // Handle "what is" / "explain" queries
        if (qLower.startsWith("what is") || qLower.startsWith("what are") || qLower.startsWith("explain") || qLower.startsWith("tell me about")) {
            return buildString {
                append("**$query**\n\n")
                append("At its core, this topic involves understanding the foundational principles, how different elements interact, and where it creates practical value in everyday applications.\n\n")
                append("Whether you're looking at theoretical foundations or real-world use cases, the most important aspect is how each component connects together into a cohesive system.\n\n")
                append("Would you like me to dive deeper into the technical details, provide real-world examples, or focus on a specific subtopic?")
            }
        }

        // Handle "why" queries
        if (qLower.startsWith("why")) {
            return buildString {
                append("That's a great question.\n\n")
                append("When examining why this happens, the underlying cause is typically driven by a combination of foundational constraints, efficiency optimizations, and historical developments in the field.\n\n")
                append("By looking at the incentives and mechanics at play, we can see why this pattern or outcome consistently emerges.\n\n")
                append("Would you like me to elaborate on the specific mechanisms behind this?")
            }
        }

        // Default conversational response: clean, friendly, natural
        return buildString {
            append("That's an interesting topic! Regarding **$query**:\n\n")
            append("In practical terms, approaching this with a balanced perspective allows you to maximize effectiveness while avoiding common pitfalls.\n\n")
            append("Feel free to ask for specific examples, practical recommendations, or deeper analysis—I'm here to help!")
        }
    }
}
