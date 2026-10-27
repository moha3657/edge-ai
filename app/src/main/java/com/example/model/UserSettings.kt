package com.example.model

data class UserSettings(
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val contextWindowTokens: Int = 1024,
    val temperature: Float = 0.7f,
    val topP: Float = 0.9f,
    val maxOutputTokens: Int = 256,
    val streamDelayMs: Long = 24L,
    val autoEvictOnBackground: Boolean = true
) {
    companion object {
        const val DEFAULT_SYSTEM_PROMPT =
            "You are EdgeAI, an intelligent and helpful assistant running 100% locally on Android. " +
            "Answer clearly, concisely, and helpfully with accurate information and clean formatting."

        val PRESET_PROMPTS = listOf(
            PromptPreset(
                name = "Helpful Assistant",
                description = "Balanced, clear, and friendly tone for everyday queries",
                prompt = "You are an intelligent, friendly, and articulate on-device AI assistant. " +
                        "Provide well-structured, clear, and insightful answers. Use Markdown formatting where helpful."
            ),
            PromptPreset(
                name = "Coding & Architecture Expert",
                description = "Focuses on clean code, bug fixes, performance, and best practices",
                prompt = "You are an expert software engineer and systems architect. " +
                        "Provide clean, type-safe, idiomatic code examples with concise technical explanations and error handling."
            ),
            PromptPreset(
                name = "Concise & Fast",
                description = "Ultra-brief answers, bullet points, and no filler words",
                prompt = "You are a concise assistant. Answer directly in brief bullet points. " +
                        "Omit conversational filler, disclaimers, and pleasantries. Deliver key facts immediately."
            ),
            PromptPreset(
                name = "Deep Document Analyst",
                description = "Strict citations and thorough extraction from indexed documents",
                prompt = "You are an expert document analyst. Ground your responses strictly in the provided document excerpts. " +
                        "Cite specific pages, sections, and exact facts with high fidelity."
            ),
            PromptPreset(
                name = "Creative & Brainstorming",
                description = "Expressive, inventive, and detailed exploration of ideas",
                prompt = "You are an imaginative and thoughtful creative collaborator. " +
                        "Explore ideas with depth, fresh perspectives, analogies, and detailed examples."
            )
        )

        val CONTEXT_WINDOW_OPTIONS = listOf(256, 512, 1024, 2048, 4096)
        val STREAM_SPEED_OPTIONS = listOf(
            Pair("Fast (12ms)", 12L),
            Pair("Balanced (24ms)", 24L),
            Pair("Relaxed (40ms)", 40L)
        )
    }
}

data class PromptPreset(
    val name: String,
    val description: String,
    val prompt: String
)
