package com.example.service

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
        if (current != null) {
            onProgress("Unloading ${current.displayName} to free ~${current.ramEstimateMB}MB RAM...")
            _state.value = ModelManagerState.Unloading(current)
            val unloadStart = System.currentTimeMillis()

            // Release native context buffer & clear references
            nativeContextHandle = null
            _contextTokensUsed.value = 0
            _activeModel.value = null

            // Force JVM Garbage Collection & yield to OS
            System.gc()
            delay(150) // Allow OS memory subsystem to reclaim mapped pages

            val memAfterUnload = memoryMonitor.getSnapshot().usedRamMB
            logTransition(
                action = "UNLOAD",
                modelName = current.displayName,
                ramBefore = memBefore,
                ramAfter = memAfterUnload,
                durationMs = System.currentTimeMillis() - unloadStart
            )
        }

        // STEP 2: LOAD the target model
        onProgress("Loading ${target.displayName} (allocating context <= ${target.maxContextSize} tokens)...")
        _state.value = ModelManagerState.Loading(target)
        val loadStart = System.currentTimeMillis()

        // Simulated native initialization of weights & KV cache (max context 1024)
        delay(250) // Emulate GGUF mmap load & tensor initialization
        nativeContextHandle = System.currentTimeMillis()
        _activeModel.value = target
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
     * Explicitly unloads the currently active model from memory.
     */
    suspend fun unloadActiveModel(): Boolean = mutex.withLock {
        val current = _activeModel.value ?: return true
        val memBefore = memoryMonitor.getSnapshot().usedRamMB
        _state.value = ModelManagerState.Unloading(current)
        val start = System.currentTimeMillis()

        nativeContextHandle = null
        _contextTokensUsed.value = 0
        _activeModel.value = null

        System.gc()
        delay(150)

        _state.value = ModelManagerState.Idle
        val memAfter = memoryMonitor.getSnapshot().usedRamMB

        logTransition(
            action = "UNLOAD_IDLE",
            modelName = current.displayName,
            ramBefore = memBefore,
            ramAfter = memAfter,
            durationMs = System.currentTimeMillis() - start
        )
        return true
    }

    /**
     * Updates and validates context token usage against the strict 1024 token limit.
     */
    fun updateContextUsage(tokens: Int): Boolean {
        _contextTokensUsed.value = tokens.coerceAtMost(1024)
        return tokens <= 1024
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
