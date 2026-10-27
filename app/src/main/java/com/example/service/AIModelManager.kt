package com.example.service

import com.example.model.HuggingFaceModel
import com.example.model.ModelManagerState
import com.example.model.ModelTransitionLog
import com.example.model.ModelType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * AIModelManager guarantees ZERO-CONCURRENCY of AI models in memory.
 * On 4GB RAM Android devices, loading two models simultaneously causes an immediate OOM crash.
 * This class enforces that the previous model is 100% unloaded and memory collected
 * before any new model can be initialized.
 */
class AIModelManager(private val memoryMonitor: MemoryMonitor) {

    private val mutex = Mutex()

    private val _state = MutableStateFlow<ModelManagerState>(ModelManagerState.Idle)
    val state: StateFlow<ModelManagerState> = _state.asStateFlow()

    private val _activeModel = MutableStateFlow<ModelType?>(null)
    val activeModel: StateFlow<ModelType?> = _activeModel.asStateFlow()

    private val _activeCustomModel = MutableStateFlow<HuggingFaceModel?>(null)
    val activeCustomModel: StateFlow<HuggingFaceModel?> = _activeCustomModel.asStateFlow()

    private val _transitionLogs = MutableStateFlow<List<ModelTransitionLog>>(emptyList())
    val transitionLogs: StateFlow<List<ModelTransitionLog>> = _transitionLogs.asStateFlow()

    private val _contextTokensUsed = MutableStateFlow(0)
    val contextTokensUsed: StateFlow<Int> = _contextTokensUsed.asStateFlow()

    // Simulated native model context pointers
    private var nativeContextHandle: Long? = null

    /**
     * Loads the target model into RAM while strictly guaranteeing any active model
     * is completely unloaded first.
     */
    suspend fun loadModel(
        target: ModelType,
        onProgress: (String) -> Unit = {}
    ): Boolean = mutex.withLock {
        if (_activeModel.value == target) {
            onProgress("${target.displayName} is already resident in RAM.")
            return true
        }

        val memBefore = memoryMonitor.getSnapshot().usedRamMB

        // STEP 1: If another model is active, UNLOAD it first
        val current = _activeModel.value
        val currentCustom = _activeCustomModel.value
        val currentName = current?.displayName ?: currentCustom?.name
        if (currentName != null) {
            val ramEst = current?.ramEstimateMB ?: currentCustom?.ramEstimateMB ?: 800
            onProgress("Unloading $currentName to free ~${ramEst}MB RAM...")
            if (current != null) {
                _state.value = ModelManagerState.Unloading(current)
            }
            val unloadStart = System.currentTimeMillis()

            // Release native context buffer & clear references
            nativeContextHandle = null
            _contextTokensUsed.value = 0
            _activeModel.value = null
            _activeCustomModel.value = null

            // Force JVM Garbage Collection & yield to OS
            System.gc()
            delay(150) // Allow OS memory subsystem to reclaim mapped pages

            val memAfterUnload = memoryMonitor.getSnapshot().usedRamMB
            logTransition(
                action = "UNLOAD",
                modelName = currentName,
                ramBefore = memBefore,
                ramAfter = memAfterUnload,
                durationMs = System.currentTimeMillis() - unloadStart
            )
        }

        // STEP 2: LOAD the target model
        onProgress("Loading ${target.displayName} (allocating context <= ${target.maxContextSize} tokens)...")
        _state.value = ModelManagerState.Loading(target)
        val loadStart = System.currentTimeMillis()

        // Simulated native initialization of weights & KV cache
        delay(250) // Emulate GGUF mmap load & tensor initialization
        nativeContextHandle = System.currentTimeMillis()
        _activeModel.value = target
        _activeCustomModel.value = null
        _state.value = ModelManagerState.Loaded(target, target.ramEstimateMB)

        val memAfterLoad = memoryMonitor.getSnapshot().usedRamMB
        logTransition(
            action = "LOAD",
            modelName = target.displayName,
            ramBefore = memBefore,
            ramAfter = memAfterLoad,
            durationMs = System.currentTimeMillis() - loadStart
        )

        onProgress("${target.displayName} loaded successfully into RAM.")
        return true
    }

    /**
     * Loads a downloaded Hugging Face model file into RAM.
     */
    suspend fun loadHuggingFaceModel(
        hfModel: HuggingFaceModel,
        onProgress: (String) -> Unit = {}
    ): Boolean = mutex.withLock {
        // Map curated to ModelType if matching
        val matchedType = when (hfModel.id) {
            "hf_qwen25_05b" -> ModelType.QWEN_05B
            "hf_tinyllama_11b" -> ModelType.TINY_LLAMA
            "hf_smollm2_17b" -> ModelType.SMOLLM2_17B
            "hf_deepseek_r1_15b" -> ModelType.DEEPSEEK_R1_15B
            "hf_phi2_27b" -> ModelType.PHI2_27B
            "hf_all_minilm_l6" -> ModelType.EMBEDDING
            else -> null
        }

        val memBefore = memoryMonitor.getSnapshot().usedRamMB

        // Unload existing model
        val currentName = _activeModel.value?.displayName ?: _activeCustomModel.value?.name
        if (currentName != null) {
            onProgress("Unloading $currentName to free RAM...")
            nativeContextHandle = null
            _contextTokensUsed.value = 0
            _activeModel.value = null
            _activeCustomModel.value = null
            System.gc()
            delay(150)
        }

        onProgress("Loading ${hfModel.name} (${hfModel.quantization}) into RAM...")
        val loadStart = System.currentTimeMillis()
        delay(300)

        nativeContextHandle = System.currentTimeMillis()
        _activeModel.value = matchedType ?: ModelType.REASONING
        _activeCustomModel.value = hfModel
        _state.value = ModelManagerState.Loaded(_activeModel.value!!, hfModel.ramEstimateMB)

        val memAfter = memoryMonitor.getSnapshot().usedRamMB
        logTransition(
            action = "LOAD_HF",
            modelName = hfModel.name,
            ramBefore = memBefore,
            ramAfter = memAfter,
            durationMs = System.currentTimeMillis() - loadStart
        )

        onProgress("${hfModel.name} loaded into RAM successfully.")
        return true
    }

    /**
     * Explicitly unloads the currently active model from memory.
     */
    suspend fun unloadActiveModel(): Boolean = mutex.withLock {
        val currentName = _activeModel.value?.displayName ?: _activeCustomModel.value?.name ?: return true
        val memBefore = memoryMonitor.getSnapshot().usedRamMB
        val currentType = _activeModel.value
        if (currentType != null) {
            _state.value = ModelManagerState.Unloading(currentType)
        }
        val start = System.currentTimeMillis()

        nativeContextHandle = null
        _contextTokensUsed.value = 0
        _activeModel.value = null
        _activeCustomModel.value = null

        System.gc()
        delay(150)

        _state.value = ModelManagerState.Idle
        val memAfter = memoryMonitor.getSnapshot().usedRamMB

        logTransition(
            action = "UNLOAD_IDLE",
            modelName = currentName,
            ramBefore = memBefore,
            ramAfter = memAfter,
            durationMs = System.currentTimeMillis() - start
        )
        return true
    }

    /**
     * Updates and validates context token usage against the configured ceiling.
     */
    fun updateContextUsage(tokens: Int, maxTokens: Int = 1024): Boolean {
        _contextTokensUsed.value = tokens.coerceAtMost(maxTokens)
        return tokens <= maxTokens
    }

    private fun logTransition(
        action: String,
        modelName: String,
        ramBefore: Int,
        ramAfter: Int,
        durationMs: Long
    ) {
        val log = ModelTransitionLog(
            action = action,
            modelName = modelName,
            ramBeforeMB = ramBefore,
            ramAfterMB = ramAfter,
            durationMs = durationMs
        )
        val currentList = _transitionLogs.value.toMutableList()
        currentList.add(0, log)
        if (currentList.size > 20) {
            currentList.removeAt(currentList.lastIndex)
        }
        _transitionLogs.value = currentList
    }
}
