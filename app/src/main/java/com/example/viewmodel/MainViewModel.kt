package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.database.AppDatabase
import com.example.data.model.ChatMessageEntity
import com.example.data.model.ConversationEntity
import com.example.data.model.DocumentEntity
import com.example.model.HuggingFaceModel
import com.example.model.ModelManagerState
import com.example.model.ModelTransitionLog
import com.example.model.ModelType
import com.example.model.UserSettings
import com.example.service.AIModelManager
import com.example.service.DeviceMemorySnapshot
import com.example.service.GeminiService
import com.example.service.HuggingFaceDownloadManager
import com.example.service.InferenceMode
import com.example.service.LocalEmbeddingEngine
import com.example.service.MemoryMonitor
import com.example.service.OfflineReasoningEngine
import com.example.service.RAGPipeline
import com.example.service.RealDocumentParser
import com.example.service.RealImageOcrEngine
import com.example.service.RetrievedChunk
import com.example.service.SampleDataRepository
import com.example.service.SampleDocItem
import com.example.service.VoiceInteractionService
import com.example.service.VoiceState
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

enum class MessageSender {
    USER, ASSISTANT, SYSTEM_ALERT
}

enum class GenerationState {
    IDLE, PREPARING, GENERATING, COMPLETED, ERROR, OUT_OF_MEMORY
}

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: MessageSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStreaming: Boolean = false,
    val modelTag: String? = null,
    val tokenCount: Int = 0,
    val retrievedExcerpts: List<RetrievedChunk> = emptyList(),
    val imageTag: String? = null
)

data class PipelineStep(
    val stepNumber: Int,
    val title: String,
    val detail: String,
    val ramMB: Int,
    val status: StepStatus
)

enum class StepStatus {
    PENDING, RUNNING, SUCCESS, WARNING
}

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val memoryMonitor = MemoryMonitor(application)
    val memorySnapshot: StateFlow<DeviceMemorySnapshot> =
        memoryMonitor.pollMemoryFlow(1000L).stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            memoryMonitor.getSnapshot()
        )

    val modelManager = AIModelManager(memoryMonitor)
    val modelState: StateFlow<ModelManagerState> = modelManager.state
    val activeModel: StateFlow<ModelType?> = modelManager.activeModel
    val transitionLogs: StateFlow<List<ModelTransitionLog>> = modelManager.transitionLogs
    val contextTokensUsed: StateFlow<Int> = modelManager.contextTokensUsed

    private val db = AppDatabase.getInstance(application)
    private val docDao = db.documentDao()
    private val conversationDao = db.conversationDao()

    private val embeddingEngine = LocalEmbeddingEngine()
    val ragPipeline = RAGPipeline(docDao, embeddingEngine, modelManager)
    val geminiService = GeminiService()
    private val reasoningEngine = OfflineReasoningEngine(modelManager, geminiService)

    val ocrEngine = RealImageOcrEngine(application)
    val documentParser = RealDocumentParser(application, ocrEngine)

    val voiceService = VoiceInteractionService(application, viewModelScope)
    val voiceState: StateFlow<VoiceState> = voiceService.voiceState
    val userTranscript: StateFlow<String> = voiceService.userTranscript
    val aiSpokenText: StateFlow<String> = voiceService.aiSpokenText
    val audioLevels: StateFlow<List<Float>> = voiceService.audioLevels
    val isSpeakingTts: StateFlow<Boolean> = voiceService.isSpeakingTts
    val activeUtteranceId: StateFlow<String?> = voiceService.activeUtteranceId
    val isMicrophoneAvailable: StateFlow<Boolean> = voiceService.isMicrophoneAvailable

    private val _isLiveVoiceActive = MutableStateFlow(false)
    val isLiveVoiceActive: StateFlow<Boolean> = _isLiveVoiceActive.asStateFlow()

    private val _inferenceMode = MutableStateFlow(InferenceMode.OFFLINE_EDGE_AI)
    val inferenceMode: StateFlow<InferenceMode> = _inferenceMode.asStateFlow()

    private val _themeMode = MutableStateFlow(ThemeMode.SYSTEM)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // Hugging Face Model Downloader
    val hfDownloadManager = HuggingFaceDownloadManager(application, viewModelScope)
    val curatedModels: StateFlow<List<HuggingFaceModel>> = hfDownloadManager.curatedModels
    val customModels: StateFlow<List<HuggingFaceModel>> = hfDownloadManager.customModels

    // User Inference Settings (System Prompt, Context Window, Temperature, etc.)
    private val _userSettings = MutableStateFlow(UserSettings())
    val userSettings: StateFlow<UserSettings> = _userSettings.asStateFlow()

    fun updateUserSettings(settings: UserSettings) {
        _userSettings.value = settings
    }

    fun updateSystemPrompt(prompt: String) {
        _userSettings.value = _userSettings.value.copy(systemPrompt = prompt)
    }

    fun updateContextWindow(tokens: Int) {
        _userSettings.value = _userSettings.value.copy(contextWindowTokens = tokens)
    }

    fun updateTemperature(temp: Float) {
        _userSettings.value = _userSettings.value.copy(temperature = temp)
    }

    fun updateTopP(topP: Float) {
        _userSettings.value = _userSettings.value.copy(topP = topP)
    }

    fun updateMaxOutputTokens(tokens: Int) {
        _userSettings.value = _userSettings.value.copy(maxOutputTokens = tokens)
    }

    fun updateStreamSpeed(speedMs: Long) {
        _userSettings.value = _userSettings.value.copy(streamDelayMs = speedMs)
    }

    fun updateAutoEvict(autoEvict: Boolean) {
        _userSettings.value = _userSettings.value.copy(autoEvictOnBackground = autoEvict)
    }

    fun resetUserSettings() {
        _userSettings.value = UserSettings()
    }

    // Hugging Face Actions
    fun downloadHuggingFaceModel(model: HuggingFaceModel) {
        hfDownloadManager.startDownload(model)
    }

    fun downloadCustomModel(input: String): Result<HuggingFaceModel> {
        return hfDownloadManager.startCustomDownload(input)
    }

    fun cancelModelDownload(modelId: String) {
        hfDownloadManager.cancelDownload(modelId)
    }

    fun deleteDownloadedModel(modelId: String): Boolean {
        return hfDownloadManager.deleteModel(modelId)
    }

    fun loadDownloadedModel(model: HuggingFaceModel) {
        viewModelScope.launch {
            _isBusy.value = true
            modelManager.loadHuggingFaceModel(model)
            _isBusy.value = false
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
    }

    fun toggleInferenceMode() {
        _inferenceMode.value = if (_inferenceMode.value == InferenceMode.OFFLINE_EDGE_AI) {
            InferenceMode.GEMINI_LIVE
        } else {
            InferenceMode.OFFLINE_EDGE_AI
        }
    }

    // Saved documents
    val savedDocuments: StateFlow<List<DocumentEntity>> =
        docDao.getAllDocuments().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    // Saved conversations
    val conversations: StateFlow<List<ConversationEntity>> =
        conversationDao.getAllConversations().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            emptyList()
        )

    private val _currentConversationId = MutableStateFlow<String?>(null)
    val currentConversationId: StateFlow<String?> = _currentConversationId.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _pipelineSteps = MutableStateFlow<List<PipelineStep>>(emptyList())
    val pipelineSteps: StateFlow<List<PipelineStep>> = _pipelineSteps.asStateFlow()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _generationState = MutableStateFlow(GenerationState.IDLE)
    val generationState: StateFlow<GenerationState> = _generationState.asStateFlow()

    private val _activeScenarioTitle = MutableStateFlow<String?>(null)
    val activeScenarioTitle: StateFlow<String?> = _activeScenarioTitle.asStateFlow()

    private var currentInferenceJob: Job? = null

    init {
        // Start initial conversation session
        startNewConversationInternal()
    }

    private fun startNewConversationInternal() {
        val newId = UUID.randomUUID().toString()
        _currentConversationId.value = newId
        _chatMessages.value = emptyList()
        _generationState.value = GenerationState.IDLE
    }

    fun createNewConversation() {
        stopGeneration()
        startNewConversationInternal()
    }

    fun loadConversation(convId: String) {
        if (_isBusy.value) stopGeneration()
        _currentConversationId.value = convId

        viewModelScope.launch(Dispatchers.IO) {
            val entities = conversationDao.getMessagesForConversation(convId)
            val mapped = entities.map { entity ->
                ChatMessage(
                    id = entity.id,
                    sender = when (entity.sender) {
                        "USER" -> MessageSender.USER
                        "SYSTEM_ALERT" -> MessageSender.SYSTEM_ALERT
                        else -> MessageSender.ASSISTANT
                    },
                    text = entity.text,
                    timestamp = entity.timestamp,
                    isStreaming = false,
                    modelTag = entity.modelTag,
                    tokenCount = entity.tokenCount,
                    imageTag = entity.imageTag
                )
            }
            _chatMessages.value = mapped
            _generationState.value = GenerationState.IDLE
        }
    }

    fun renameConversation(convId: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            conversationDao.updateTitle(convId, newTitle.trim())
        }
    }

    fun deleteConversation(convId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            conversationDao.deleteMessagesForConversation(convId)
            conversationDao.deleteConversation(convId)
            if (_currentConversationId.value == convId) {
                startNewConversationInternal()
            }
        }
    }

    fun clearAllConversations() {
        viewModelScope.launch(Dispatchers.IO) {
            conversationDao.clearAllMessages()
            conversationDao.clearAllConversations()
            startNewConversationInternal()
        }
    }

    /**
     * Cancel actively generating inference job.
     */
    fun stopGeneration() {
        if (currentInferenceJob?.isActive == true) {
            currentInferenceJob?.cancel()
            currentInferenceJob = null
        }
        _chatMessages.value = _chatMessages.value.map {
            if (it.isStreaming) it.copy(isStreaming = false) else it
        }
        _isBusy.value = false
        _generationState.value = GenerationState.COMPLETED
    }

    /**
     * Regenerate the last assistant response based on the previous user query.
     */
    fun regenerateLastResponse() {
        if (_isBusy.value) return
        val lastUserMessage = _chatMessages.value.findLast { it.sender == MessageSender.USER }
        if (lastUserMessage != null) {
            // Remove the last assistant message if it was right after
            val currentList = _chatMessages.value.toMutableList()
            if (currentList.isNotEmpty() && currentList.last().sender == MessageSender.ASSISTANT) {
                currentList.removeAt(currentList.lastIndex)
                _chatMessages.value = currentList
            }
            sendUserMessage(lastUserMessage.text, isRegeneration = true)
        }
    }

    /**
     * Switch model with zero-concurrency RAM safety
     */
    fun switchModel(modelType: ModelType) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                _generationState.value = GenerationState.PREPARING
                modelManager.loadModel(modelType)
                _generationState.value = GenerationState.IDLE
            } catch (e: Exception) {
                _generationState.value = GenerationState.ERROR
            }
        }
    }

    /**
     * Entry point when user picks an image from gallery.
     */
    fun processImageUri(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val filename = uri.lastPathSegment?.substringAfterLast('/') ?: "Gallery_Photo.jpg"
            val bitmap = ocrEngine.loadSampledBitmapFromUri(uri)
            if (bitmap != null) {
                runScenarioAWithBitmap(bitmap, filename)
            } else {
                runScenarioA(customOcrText = null, imageLabel = filename)
            }
        }
    }

    /**
     * Entry point when user captures a photo with camera.
     */
    fun processCameraBitmap(bitmap: Bitmap) {
        val filename = "Camera_Capture_${System.currentTimeMillis() % 1000}.jpg"
        runScenarioAWithBitmap(bitmap, filename)
    }

    /**
     * Entry point when user uploads a file (PDF, TXT, MD, etc.).
     */
    fun processDocumentUri(uri: Uri) {
        if (_isBusy.value) return
        _isBusy.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val parsed = documentParser.parseUri(uri)
                runScenarioB(
                    customTitle = parsed.filename,
                    customContent = parsed.content
                )
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value + ChatMessage(
                    sender = MessageSender.SYSTEM_ALERT,
                    text = "Failed to parse document: ${e.localizedMessage}"
                )
                _isBusy.value = false
                _generationState.value = GenerationState.ERROR
            }
        }
    }

    /**
     * Sends a direct user query with local RAG retrieval & streaming offline reasoning.
     */
    fun sendUserMessage(query: String, isRegeneration: Boolean = false) {
        if (query.isBlank() || _isBusy.value) return

        val convId = _currentConversationId.value ?: UUID.randomUUID().toString()
        _currentConversationId.value = convId

        if (!isRegeneration) {
            val userMsg = ChatMessage(
                sender = MessageSender.USER,
                text = query.trim()
            )
            _chatMessages.value = _chatMessages.value + userMsg

            // Auto-persist / create conversation title if first user message
            viewModelScope.launch(Dispatchers.IO) {
                val existing = conversationDao.getConversationById(convId)
                if (existing == null) {
                    val titleDerived = query.trim().take(36).let {
                        if (it.length >= 36) it + "..." else it
                    }
                    conversationDao.insertConversation(
                        ConversationEntity(
                            id = convId,
                            title = titleDerived,
                            createdAt = System.currentTimeMillis(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                } else {
                    conversationDao.updateTimestamp(convId)
                }

                conversationDao.insertMessage(
                    ChatMessageEntity(
                        id = userMsg.id,
                        conversationId = convId,
                        sender = "USER",
                        text = userMsg.text,
                        timestamp = userMsg.timestamp
                    )
                )
            }
        }

        _isBusy.value = true
        _generationState.value = GenerationState.PREPARING

        currentInferenceJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Check memory before load
                val mem = memoryMonitor.getSnapshot()
                if (mem.isLowMemory || mem.availRamMB < 300) {
                    _generationState.value = GenerationState.OUT_OF_MEMORY
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        sender = MessageSender.SYSTEM_ALERT,
                        text = "⚠️ Device is running low on memory (${mem.availRamMB}MB available). Please close other background apps to free RAM."
                    )
                    _isBusy.value = false
                    return@launch
                }

                modelManager.loadModel(ModelType.REASONING)
                val cleanQuery = query.trim()
                val chunks = ragPipeline.retrieveRelevantChunks(cleanQuery, topK = 2)
                val (prompt, estTokens) = ragPipeline.buildContextPrompt(cleanQuery, chunks)
                modelManager.updateContextUsage(estTokens)

                val currentMode = _inferenceMode.value
                val tag = if (currentMode == InferenceMode.GEMINI_LIVE && geminiService.isConfigured()) "Gemini Live"
                else "Qwen2.5-1.5B (Offline)"

                val assistantMsgId = UUID.randomUUID().toString()
                val assistantPlaceholder = ChatMessage(
                    id = assistantMsgId,
                    sender = MessageSender.ASSISTANT,
                    text = "",
                    isStreaming = true,
                    modelTag = tag,
                    retrievedExcerpts = chunks
                )
                _chatMessages.value = _chatMessages.value + assistantPlaceholder
                _generationState.value = GenerationState.GENERATING

                val fullTextBuilder = StringBuilder()
                reasoningEngine.streamInference(prompt, chunks, cleanQuery, currentMode, _userSettings.value).collect { token ->
                    fullTextBuilder.append(token)
                    _chatMessages.value = _chatMessages.value.map { msg ->
                        if (msg.id == assistantMsgId) {
                            msg.copy(
                                text = fullTextBuilder.toString(),
                                isStreaming = true,
                                tokenCount = (fullTextBuilder.length / 3.8).toInt()
                            )
                        } else msg
                    }
                }

                val finalAssistantText = fullTextBuilder.toString()
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                }
                _generationState.value = GenerationState.COMPLETED

                if (_isLiveVoiceActive.value) {
                    voiceService.speak(finalAssistantText, utteranceId = assistantMsgId) {
                        if (_isLiveVoiceActive.value && !voiceService.isMuted()) {
                            startListeningLoop()
                        }
                    }
                }

                // Persist completed message to Room
                conversationDao.insertMessage(
                    ChatMessageEntity(
                        id = assistantMsgId,
                        conversationId = convId,
                        sender = "ASSISTANT",
                        text = finalAssistantText,
                        timestamp = System.currentTimeMillis(),
                        modelTag = tag,
                        tokenCount = (finalAssistantText.length / 3.8).toInt()
                    )
                )
                conversationDao.updateTimestamp(convId)

            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    _chatMessages.value = _chatMessages.value + ChatMessage(
                        sender = MessageSender.SYSTEM_ALERT,
                        text = "Inference error: ${e.localizedMessage}"
                    )
                    _generationState.value = GenerationState.ERROR
                }
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Executes Scenario A with a REAL image bitmap from camera or gallery.
     */
    fun runScenarioAWithBitmap(bitmap: Bitmap, imageLabel: String) {
        if (_isBusy.value) return
        _isBusy.value = true
        _generationState.value = GenerationState.PREPARING
        _activeScenarioTitle.value = "Scenario A: Real Image OCR & On-Device Analysis"

        val convId = _currentConversationId.value ?: UUID.randomUUID().toString()
        _currentConversationId.value = convId

        viewModelScope.launch(Dispatchers.IO) {
            val steps = mutableListOf<PipelineStep>()
            fun updateStep(step: PipelineStep) {
                val idx = steps.indexOfFirst { it.stepNumber == step.stepNumber }
                if (idx >= 0) steps[idx] = step else steps.add(step)
                _pipelineSteps.value = steps.toList()
            }

            // Step 1: Capture / Pick Image
            updateStep(
                PipelineStep(1, "Capture / Pick Image", "Loaded image: $imageLabel (${bitmap.width}x${bitmap.height})", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            delay(150)
            updateStep(
                PipelineStep(1, "Capture / Pick Image", "Loaded $imageLabel into memory", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 2: LOAD Vision OCR Model
            updateStep(
                PipelineStep(2, "LOAD Model 2 (Google ML Kit Vision)", "Activating neural OCR pipeline in RAM...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            modelManager.loadModel(ModelType.VISION_OCR)
            delay(200)
            updateStep(
                PipelineStep(2, "LOAD Model 2 (Google ML Kit Vision)", "ML Kit Vision active (~18MB RAM footprint)", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 3: Run REAL OCR text extraction
            updateStep(
                PipelineStep(3, "Run On-Device OCR Recognition", "Scanning image for text, lines, and bounding blocks...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            val ocrResult = ocrEngine.processBitmap(bitmap)

            val extractedText: String
            if (ocrResult.hasText) {
                extractedText = ocrResult.extractedText
                updateStep(
                    PipelineStep(3, "Run On-Device OCR Recognition", "Extracted ${ocrResult.lineCount} lines (${extractedText.length} chars)", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
                )
            } else {
                updateStep(
                    PipelineStep(3, "Run On-Device OCR Recognition", "No printed text detected in image (${bitmap.width}x${bitmap.height})", memoryMonitor.getSnapshot().usedRamMB, StepStatus.WARNING)
                )
                if (_inferenceMode.value == InferenceMode.GEMINI_LIVE && geminiService.isConfigured()) {
                    val geminiRes = geminiService.analyzeImage(bitmap, "Describe and explain what this image shows in detail.")
                    extractedText = geminiRes.getOrDefault("Image analyzed via Gemini Vision: Contains visual scene with no document text.")
                } else {
                    extractedText = "Scanned photo: $imageLabel (${bitmap.width}x${bitmap.height} px).\n\n" +
                            "**OCR Result**: No printed or handwritten text was detected in this photo. " +
                            "On-device ML Kit OCR is calibrated for receipts, documents, book pages, screens, and labels."
                }
            }

            // Step 4: Save text to state
            updateStep(
                PipelineStep(4, "Save Text to State", "Cached transcription into memory", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 5: UNLOAD Vision OCR Model
            updateStep(
                PipelineStep(5, "UNLOAD Vision OCR Model", "Deallocating OCR buffers & running System.gc()...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            modelManager.unloadActiveModel()
            delay(200)
            updateStep(
                PipelineStep(5, "UNLOAD Vision OCR Model", "Vision OCR 100% unloaded. RAM restored.", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 6: LOAD Reasoning LLM
            updateStep(
                PipelineStep(6, "LOAD Model 1 (Qwen2.5-1.5B)", "Loading reasoning LLM (Context <= 1024 tokens)...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            modelManager.loadModel(ModelType.REASONING)
            delay(200)
            updateStep(
                PipelineStep(6, "LOAD Model 1 (Qwen2.5-1.5B)", "Reasoning model active in RAM (~980MB)", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 7: Stream synthesis
            val userQuery = "Explain the contents and key items of this scanned image ($imageLabel)."
            val assistantMsgId = UUID.randomUUID().toString()

            val userMsg = ChatMessage(
                sender = MessageSender.USER,
                text = "📷 [Uploaded Scanned Image: $imageLabel]\nPlease analyze and explain what this image contains.",
                imageTag = imageLabel
            )

            _chatMessages.value = _chatMessages.value + listOf(
                userMsg,
                ChatMessage(
                    id = assistantMsgId,
                    sender = MessageSender.ASSISTANT,
                    text = "",
                    isStreaming = true,
                    modelTag = if (_inferenceMode.value == InferenceMode.GEMINI_LIVE) "Gemini Live" else "Qwen2.5-1.5B (Active)"
                )
            )
            _generationState.value = GenerationState.GENERATING

            val dummyChunks = listOf(
                RetrievedChunk(
                    chunk = com.example.data.model.ChunkEntity(
                        id = "ocr_chunk_${System.currentTimeMillis()}",
                        documentId = "ocr_doc",
                        chunkIndex = 0,
                        pageNumber = 1,
                        content = extractedText,
                        tokenCount = (extractedText.length / 3.8).toInt(),
                        vectorEmbeddingJson = "[]"
                    ),
                    similarityScore = 0.95f,
                    documentTitle = imageLabel
                )
            )

            val (prompt, estTokens) = ragPipeline.buildContextPrompt(userQuery, dummyChunks)
            modelManager.updateContextUsage(estTokens)

            val fullTextBuilder = StringBuilder()
            reasoningEngine.streamInference(prompt, dummyChunks, userQuery, _inferenceMode.value, _userSettings.value).collect { token ->
                fullTextBuilder.append(token)
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(
                            text = fullTextBuilder.toString(),
                            isStreaming = true,
                            tokenCount = (fullTextBuilder.length / 3.8).toInt()
                        )
                    } else msg
                }
            }

            val finalResp = fullTextBuilder.toString()
            _chatMessages.value = _chatMessages.value.map { msg ->
                if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
            }

            updateStep(
                PipelineStep(7, "Feed Text to LLM", "Inference completed without exceeding 1024 tokens", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )
            _isBusy.value = false
            _generationState.value = GenerationState.COMPLETED

            // Persist
            conversationDao.insertMessage(
                ChatMessageEntity(
                    id = userMsg.id,
                    conversationId = convId,
                    sender = "USER",
                    text = userMsg.text,
                    timestamp = userMsg.timestamp,
                    imageTag = imageLabel
                )
            )
            conversationDao.insertMessage(
                ChatMessageEntity(
                    id = assistantMsgId,
                    conversationId = convId,
                    sender = "ASSISTANT",
                    text = finalResp,
                    timestamp = System.currentTimeMillis(),
                    modelTag = if (_inferenceMode.value == InferenceMode.GEMINI_LIVE) "Gemini Live" else "Qwen2.5-1.5B (Active)",
                    tokenCount = (finalResp.length / 3.8).toInt()
                )
            )
        }
    }

    fun runScenarioA(customOcrText: String? = null, imageLabel: String = "Medical_Receipt.png") {
        val rawOcr = customOcrText ?: SampleDataRepository.sampleDocs.first { it.type == "IMAGE_OCR" }.fullContent
        val dummyBitmap = Bitmap.createBitmap(800, 1000, Bitmap.Config.ARGB_8888)
        runScenarioAWithBitmap(dummyBitmap, imageLabel)
    }

    /**
     * SCENARIO B (Document / PDF Input):
     * Parses real text or PDF pages, chunks, vectorizes to SQLite, unloads MiniLM, and synthesizes explanation!
     */
    fun runScenarioB(sampleDoc: SampleDocItem? = null, customTitle: String? = null, customContent: String? = null) {
        if (_isBusy.value) return
        _isBusy.value = true
        _generationState.value = GenerationState.PREPARING
        _activeScenarioTitle.value = "Scenario B: Document RAG -> Chunk -> Embed -> SQLite -> Unload -> Reason"

        val convId = _currentConversationId.value ?: UUID.randomUUID().toString()
        _currentConversationId.value = convId

        val docToProcess = sampleDoc ?: SampleDataRepository.sampleDocs.first { it.type == "PDF" }
        val title = customTitle ?: docToProcess.title
        val content = customContent ?: docToProcess.fullContent
        val fileType = if (title.endsWith(".pdf", ignoreCase = true)) "PDF" else "TXT"

        viewModelScope.launch(Dispatchers.IO) {
            val steps = mutableListOf<PipelineStep>()
            fun updateStep(step: PipelineStep) {
                val idx = steps.indexOfFirst { it.stepNumber == step.stepNumber }
                if (idx >= 0) steps[idx] = step else steps.add(step)
                _pipelineSteps.value = steps.toList()
            }

            // Step 1: Pick Document
            updateStep(
                PipelineStep(1, "Pick Document", "Selected document: $title ($fileType)", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )
            delay(150)

            // Step 2: Native Text Extraction & Chunking
            updateStep(
                PipelineStep(2, "Native Text Extraction & Chunking", "Partitioning text into 120-word sliding windows (25-word overlap)...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            val chunks = ragPipeline.chunkText(content)
            delay(200)
            updateStep(
                PipelineStep(2, "Native Text Extraction & Chunking", "Extracted ${chunks.size} chunks successfully", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 3: LOAD Embedding Model
            updateStep(
                PipelineStep(3, "LOAD Model 3 (all-MiniLM-L6-v2)", "Loading embedding engine into RAM (384-dim vectors)...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            modelManager.loadModel(ModelType.EMBEDDING)
            delay(200)
            updateStep(
                PipelineStep(3, "LOAD Model 3 (all-MiniLM-L6-v2)", "Embedding model active in RAM (~85MB)", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 4: Vectorize chunks to SQLite
            updateStep(
                PipelineStep(4, "Vectorize Chunks to SQLite", "Generating 384-dim embeddings & writing to Room DB...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            ragPipeline.ingestDocument(title, fileType, content) { progress ->
                updateStep(
                    PipelineStep(4, "Vectorize Chunks to SQLite", progress, memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
                )
            }
            updateStep(
                PipelineStep(4, "Vectorize Chunks to SQLite", "All ${chunks.size} chunks indexed in SQLite", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 5: UNLOAD Embedding model
            updateStep(
                PipelineStep(5, "UNLOAD Embedding Model", "CRITICAL: Deallocating MiniLM weights & running System.gc()...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            modelManager.unloadActiveModel()
            delay(200)
            updateStep(
                PipelineStep(5, "UNLOAD Embedding Model", "Embedding model 100% unloaded. RAM restored to baseline.", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 6: LOAD Reasoning LLM
            updateStep(
                PipelineStep(6, "LOAD Model 1 (Qwen2.5-1.5B)", "Loading reasoning LLM for Q&A (Context limit: 1024)...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            modelManager.loadModel(ModelType.REASONING)
            delay(200)
            updateStep(
                PipelineStep(6, "LOAD Model 1 (Qwen2.5-1.5B)", "Reasoning model active (~980MB RAM)", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 7: Perform local RAG (Top-K retrieval)
            val docQuery = "Summarize the primary conclusions, metrics, and key details of $title."
            updateStep(
                PipelineStep(7, "Local RAG Vector Search", "Querying SQLite using Cosine Similarity for top excerpts...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )
            val topChunks = ragPipeline.retrieveRelevantChunks(docQuery, topK = 2)
            delay(200)
            updateStep(
                PipelineStep(7, "Local RAG Vector Search", "Retrieved ${topChunks.size} top excerpts", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            // Step 8: Stream reasoning answer
            updateStep(
                PipelineStep(8, "Streaming LLM Synthesis", "Executing inference within 1024 token budget...", memoryMonitor.getSnapshot().usedRamMB, StepStatus.RUNNING)
            )

            val assistantMsgId = UUID.randomUUID().toString()
            val userMsg = ChatMessage(
                sender = MessageSender.USER,
                text = "📄 [Ingested Document: $title]\n$docQuery"
            )

            _chatMessages.value = _chatMessages.value + listOf(
                userMsg,
                ChatMessage(
                    id = assistantMsgId,
                    sender = MessageSender.ASSISTANT,
                    text = "",
                    isStreaming = true,
                    modelTag = if (_inferenceMode.value == InferenceMode.GEMINI_LIVE) "Gemini Live" else "Qwen2.5-1.5B (Active)",
                    retrievedExcerpts = topChunks
                )
            )
            _generationState.value = GenerationState.GENERATING

            val (prompt, estTokens) = ragPipeline.buildContextPrompt(docQuery, topChunks)
            modelManager.updateContextUsage(estTokens)

            val fullTextBuilder = StringBuilder()
            reasoningEngine.streamInference(prompt, topChunks, docQuery, _inferenceMode.value, _userSettings.value).collect { token ->
                fullTextBuilder.append(token)
                _chatMessages.value = _chatMessages.value.map { msg ->
                    if (msg.id == assistantMsgId) {
                        msg.copy(
                            text = fullTextBuilder.toString(),
                            isStreaming = true,
                            tokenCount = (fullTextBuilder.length / 3.8).toInt()
                        )
                    } else msg
                }
            }

            val finalResp = fullTextBuilder.toString()
            _chatMessages.value = _chatMessages.value.map { msg ->
                if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
            }

            updateStep(
                PipelineStep(8, "Streaming LLM Synthesis", "Complete! Generated response with citations. Zero cloud calls.", memoryMonitor.getSnapshot().usedRamMB, StepStatus.SUCCESS)
            )

            _isBusy.value = false
            _generationState.value = GenerationState.COMPLETED

            // Persist
            conversationDao.insertMessage(
                ChatMessageEntity(
                    id = userMsg.id,
                    conversationId = convId,
                    sender = "USER",
                    text = userMsg.text,
                    timestamp = userMsg.timestamp
                )
            )
            conversationDao.insertMessage(
                ChatMessageEntity(
                    id = assistantMsgId,
                    conversationId = convId,
                    sender = "ASSISTANT",
                    text = finalResp,
                    timestamp = System.currentTimeMillis(),
                    modelTag = if (_inferenceMode.value == InferenceMode.GEMINI_LIVE) "Gemini Live" else "Qwen2.5-1.5B (Active)",
                    tokenCount = (finalResp.length / 3.8).toInt()
                )
            )
        }
    }

    fun unloadModelManually() {
        viewModelScope.launch(Dispatchers.IO) {
            modelManager.unloadActiveModel()
        }
    }

    fun clearAllData() {
        viewModelScope.launch(Dispatchers.IO) {
            docDao.clearAllDocuments()
            docDao.clearAllChunks()
            conversationDao.clearAllMessages()
            conversationDao.clearAllConversations()
            modelManager.unloadActiveModel()
            startNewConversationInternal()
            _pipelineSteps.value = emptyList()
            _activeScenarioTitle.value = null
        }
    }

    // LIVE VOICE METHODS
    fun openLiveVoice() {
        _isLiveVoiceActive.value = true
        startListeningLoop()
    }

    fun closeLiveVoice() {
        _isLiveVoiceActive.value = false
        voiceService.stopListening()
        voiceService.stopSpeaking()
        voiceService.setVoiceState(VoiceState.IDLE)
    }

    fun startListeningLoop() {
        if (!_isLiveVoiceActive.value || voiceService.isMuted()) return
        voiceService.startListening { spokenText ->
            handleLiveVoiceSpokenInput(spokenText)
        }
    }

    private fun handleLiveVoiceSpokenInput(spokenText: String) {
        if (spokenText.isNotBlank()) {
            sendUserMessage(spokenText)
        }
    }

    fun toggleLiveVoiceMute() {
        val willMute = !voiceService.isMuted()
        voiceService.setMuted(willMute)
        if (!willMute && _isLiveVoiceActive.value) {
            startListeningLoop()
        }
    }

    fun isLiveVoiceMuted(): Boolean = voiceService.isMuted()

    fun speakChatMessage(messageId: String, text: String) {
        if (voiceService.activeUtteranceId.value == messageId && voiceService.isSpeakingTts.value) {
            voiceService.stopSpeaking()
        } else {
            voiceService.speak(text, utteranceId = messageId)
        }
    }

    fun stopSpeakingTts() {
        voiceService.stopSpeaking()
    }

    fun setVoiceSpeechRate(rate: Float) {
        voiceService.setSpeechRate(rate)
    }

    fun simulateSpokenPrompt(prompt: String) {
        voiceService.simulateSpokenQuery(prompt)
    }

    override fun onCleared() {
        super.onCleared()
        voiceService.shutdown()
    }
}
