package com.example.model

enum class ModelType(
    val displayName: String,
    val architecture: String,
    val quantization: String,
    val ramEstimateMB: Int,
    val maxContextSize: Int,
    val purpose: String
) {
    REASONING(
        displayName = "Qwen2.5-1.5B-Instruct",
        architecture = "Transformer (Dense)",
        quantization = "Q4_K_M (GGUF)",
        ramEstimateMB = 980,
        maxContextSize = 1024,
        purpose = "Explaining, reasoning, & Q&A within 1024 token budget"
    ),
    QWEN_05B(
        displayName = "Qwen2.5-0.5B-Instruct",
        architecture = "Transformer (Dense)",
        quantization = "Q4_K_M (GGUF)",
        ramEstimateMB = 480,
        maxContextSize = 1024,
        purpose = "Ultra-fast lightweight reasoning model optimized for budget mobile hardware"
    ),
    TINY_LLAMA(
        displayName = "TinyLlama-1.1B-Chat",
        architecture = "Llama-2 Architecture",
        quantization = "Q4_K_M (GGUF)",
        ramEstimateMB = 720,
        maxContextSize = 1024,
        purpose = "Popular conversational model for quick chat and casual conversation"
    ),
    SMOLLM2_17B(
        displayName = "SmolLM2-1.7B-Instruct",
        architecture = "Transformer (Dense)",
        quantization = "Q4_K_M (GGUF)",
        ramEstimateMB = 1080,
        maxContextSize = 2048,
        purpose = "Hugging Face official compact flagship with high instruction-following quality"
    ),
    DEEPSEEK_R1_15B(
        displayName = "DeepSeek-R1-Distill-1.5B",
        architecture = "Qwen / DeepSeek MoE",
        quantization = "Q4_K_M (GGUF)",
        ramEstimateMB = 1150,
        maxContextSize = 2048,
        purpose = "Distilled from DeepSeek-R1. Strong mathematical reasoning and chain-of-thought"
    ),
    PHI2_27B(
        displayName = "Phi-2-2.7B",
        architecture = "Transformer (Microsoft)",
        quantization = "Q4_K_M (GGUF)",
        ramEstimateMB = 1750,
        maxContextSize = 2048,
        purpose = "Microsoft Research 2.7B model with strong logical reasoning and python coding"
    ),
    VISION_OCR(
        displayName = "Google ML Kit Vision OCR",
        architecture = "Neural Mobile OCR Engine",
        quantization = "Native Zero-Copy",
        ramEstimateMB = 18,
        maxContextSize = 1024,
        purpose = "Ultra-fast on-device text & document recognition"
    ),
    EMBEDDING(
        displayName = "all-MiniLM-L6-v2",
        architecture = "MiniLM Transformer",
        quantization = "FP16 (384-dim)",
        ramEstimateMB = 85,
        maxContextSize = 512,
        purpose = "Vectorizing document chunks for local RAG retrieval"
    )
}

sealed class ModelManagerState {
    data object Idle : ModelManagerState()
    data class Loading(val target: ModelType) : ModelManagerState()
    data class Loaded(val active: ModelType, val allocatedRamMB: Int) : ModelManagerState()
    data class Unloading(val previous: ModelType) : ModelManagerState()
    data class Inferencing(val active: ModelType, val progressText: String) : ModelManagerState()
    data class Error(val message: String) : ModelManagerState()
}

data class ModelTransitionLog(
    val timestamp: Long = System.currentTimeMillis(),
    val action: String,
    val modelName: String,
    val ramBeforeMB: Int,
    val ramAfterMB: Int,
    val durationMs: Long
)
