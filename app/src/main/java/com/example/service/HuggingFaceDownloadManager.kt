package com.example.service

import android.content.Context
import com.example.model.HuggingFaceModel
import com.example.model.ModelDownloadStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class HuggingFaceDownloadManager(
    private val context: Context,
    private val scope: CoroutineScope
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val modelsDir: File by lazy {
        val dir = context.getExternalFilesDir("models") ?: File(context.filesDir, "models")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val downloadJobs = ConcurrentHashMap<String, Job>()

    private val _curatedModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val curatedModels: StateFlow<List<HuggingFaceModel>> = _curatedModels.asStateFlow()

    private val _customModels = MutableStateFlow<List<HuggingFaceModel>>(emptyList())
    val customModels: StateFlow<List<HuggingFaceModel>> = _customModels.asStateFlow()

    init {
        initializeCatalog()
        scanExistingFiles()
    }

    private fun initializeCatalog() {
        val catalog = listOf(
            HuggingFaceModel(
                id = "hf_qwen25_05b",
                name = "Qwen2.5-0.5B-Instruct",
                repoId = "Qwen/Qwen2.5-0.5B-Instruct-GGUF",
                filename = "qwen2.5-0.5b-instruct-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf",
                sizeBytes = 398_000_000L,
                ramEstimateMB = 480,
                quantization = "Q4_K_M (GGUF)",
                parameterCount = "0.5B",
                description = "Ultra-fast lightweight reasoning model optimized for budget mobile hardware.",
                tags = listOf("Fast", "Low RAM", "Mobile")
            ),
            HuggingFaceModel(
                id = "hf_tinyllama_11b",
                name = "TinyLlama-1.1B-Chat-v1.0",
                repoId = "TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF",
                filename = "tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF/resolve/main/tinyllama-1.1b-chat-v1.0.Q4_K_M.gguf",
                sizeBytes = 669_000_000L,
                ramEstimateMB = 720,
                quantization = "Q4_K_M (GGUF)",
                parameterCount = "1.1B",
                description = "Popular conversational model trained on 3 trillion tokens. Great for casual chat.",
                tags = listOf("Popular", "Chat", "Efficient")
            ),
            HuggingFaceModel(
                id = "hf_smollm2_17b",
                name = "SmolLM2-1.7B-Instruct",
                repoId = "HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF",
                filename = "smollm2-1.7b-instruct-q4_k_m.gguf",
                downloadUrl = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
                sizeBytes = 1_050_000_000L,
                ramEstimateMB = 1080,
                quantization = "Q4_K_M (GGUF)",
                parameterCount = "1.7B",
                description = "Hugging Face official compact flagship. Excellent instruction following and reasoning.",
                tags = listOf("Flagship", "Instructions", "Smart")
            ),
            HuggingFaceModel(
                id = "hf_deepseek_r1_15b",
                name = "DeepSeek-R1-Distill-1.5B",
                repoId = "bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF",
                filename = "DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
                sizeBytes = 1_120_000_000L,
                ramEstimateMB = 1150,
                quantization = "Q4_K_M (GGUF)",
                parameterCount = "1.5B",
                description = "Distilled from DeepSeek-R1. Strong mathematical reasoning and chain-of-thought capabilities.",
                tags = listOf("Math", "DeepSeek", "CoT Reasoning")
            ),
            HuggingFaceModel(
                id = "hf_phi2_27b",
                name = "Phi-2-2.7B",
                repoId = "TheBloke/phi-2-GGUF",
                filename = "phi-2.Q4_K_M.gguf",
                downloadUrl = "https://huggingface.co/TheBloke/phi-2-GGUF/resolve/main/phi-2.Q4_K_M.gguf",
                sizeBytes = 1_780_000_000L,
                ramEstimateMB = 1750,
                quantization = "Q4_K_M (GGUF)",
                parameterCount = "2.7B",
                description = "Microsoft Research 2.7B model showcasing strong logical reasoning and python coding.",
                tags = listOf("Microsoft", "Coding", "Logic")
            ),
            HuggingFaceModel(
                id = "hf_all_minilm_l6",
                name = "all-MiniLM-L6-v2",
                repoId = "second-state/All-MiniLM-L6-v2-Embedding-GGUF",
                filename = "all-MiniLM-L6-v2-Q4_0.gguf",
                downloadUrl = "https://huggingface.co/second-state/All-MiniLM-L6-v2-Embedding-GGUF/resolve/main/all-MiniLM-L6-v2-Q4_0.gguf",
                sizeBytes = 90_000_000L,
                ramEstimateMB = 95,
                quantization = "Q4_0 (FP16/GGUF)",
                parameterCount = "33M",
                description = "Compact 384-dimensional vector embedding model for high-speed local RAG document search.",
                tags = listOf("Embeddings", "RAG", "Ultra-Light")
            )
        )
        _curatedModels.value = catalog
    }

    fun scanExistingFiles() {
        val files = modelsDir.listFiles() ?: return
        val existingFilesMap = files.associateBy { it.name }

        // Update curated models
        _curatedModels.value = _curatedModels.value.map { model ->
            val file = existingFilesMap[model.filename]
            if (file != null && file.exists() && file.length() > 0) {
                model.copy(
                    isDownloaded = true,
                    downloadProgress = 1.0f,
                    downloadedBytes = file.length(),
                    status = ModelDownloadStatus.COMPLETED,
                    localFilePath = file.absolutePath
                )
            } else {
                model.copy(
                    isDownloaded = false,
                    downloadProgress = 0f,
                    status = ModelDownloadStatus.IDLE,
                    localFilePath = null
                )
            }
        }

        // Update custom models
        _customModels.value = _customModels.value.map { model ->
            val file = existingFilesMap[model.filename]
            if (file != null && file.exists() && file.length() > 0) {
                model.copy(
                    isDownloaded = true,
                    downloadProgress = 1.0f,
                    downloadedBytes = file.length(),
                    status = ModelDownloadStatus.COMPLETED,
                    localFilePath = file.absolutePath
                )
            } else {
                model
            }
        }
    }

    /**
     * Downloads any custom model by URL or Hugging Face repo ID.
     */
    fun startCustomDownload(input: String): Result<HuggingFaceModel> {
        val trimmed = input.trim()
        if (trimmed.isBlank()) {
            return Result.failure(IllegalArgumentException("Please enter a valid Hugging Face URL or Repo ID."))
        }

        val resolvedUrl: String
        val repoId: String
        val filename: String

        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            // Direct URL
            resolvedUrl = trimmed.replace("/blob/main/", "/resolve/main/")
            filename = resolvedUrl.substringAfterLast("/").takeIf { it.isNotBlank() } ?: "custom_model.gguf"
            val parts = resolvedUrl.removePrefix("https://").removePrefix("http://").split("/")
            repoId = if (parts.size >= 3) "${parts[1]}/${parts[2]}" else "custom/repo"
        } else {
            // Repo pattern: e.g. "Qwen/Qwen2.5-0.5B-Instruct-GGUF" or "author/repo:file.gguf"
            if (trimmed.contains(":")) {
                repoId = trimmed.substringBefore(":")
                filename = trimmed.substringAfter(":")
            } else {
                repoId = trimmed
                filename = "${repoId.substringAfterLast("/")}.Q4_K_M.gguf"
            }
            resolvedUrl = "https://huggingface.co/$repoId/resolve/main/$filename"
        }

        val model = HuggingFaceModel(
            id = "custom_" + UUID.randomUUID().toString().take(8),
            name = filename.removeSuffix(".gguf"),
            repoId = repoId,
            filename = filename,
            downloadUrl = resolvedUrl,
            sizeBytes = 500_000_000L, // Initial estimate until HTTP headers read
            ramEstimateMB = 850,
            quantization = "GGUF",
            parameterCount = "Custom",
            description = "Custom downloaded model from $repoId",
            tags = listOf("Custom", "Hugging Face"),
            isCurated = false
        )

        _customModels.value = listOf(model) + _customModels.value
        startDownload(model)
        return Result.success(model)
    }

    /**
     * Initiates streaming download of the given model.
     */
    fun startDownload(model: HuggingFaceModel) {
        if (downloadJobs[model.id]?.isActive == true) return

        val targetFile = File(modelsDir, model.filename)

        updateModelStatus(model.id) {
            it.copy(
                status = ModelDownloadStatus.DOWNLOADING,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                errorMessage = null
            )
        }

        val job = scope.launch(Dispatchers.IO) {
            var outputStream: FileOutputStream? = null
            var inputStream: InputStream? = null
            try {
                val request = Request.Builder()
                    .url(model.downloadUrl)
                    .header("User-Agent", "EdgeAI-Android/1.0")
                    .build()

                val response = client.newCall(request).execute()
                if (!response.isSuccessful) {
                    throw IllegalStateException("Server returned HTTP ${response.code}: ${response.message}")
                }

                val body = response.body ?: throw IllegalStateException("Empty response body from Hugging Face.")
                val totalBytes = if (body.contentLength() > 0) body.contentLength() else model.sizeBytes

                inputStream = body.byteStream()
                outputStream = FileOutputStream(targetFile)

                val buffer = ByteArray(16 * 1024)
                var bytesRead: Int
                var totalBytesRead = 0L
                var lastProgressUpdate = System.currentTimeMillis()
                var bytesSinceLastUpdate = 0L
                var speedKbps = 0L

                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    totalBytesRead += bytesRead
                    bytesSinceLastUpdate += bytesRead

                    val now = System.currentTimeMillis()
                    val timeDiff = now - lastProgressUpdate
                    if (timeDiff >= 350) {
                        speedKbps = (bytesSinceLastUpdate * 1000L) / (timeDiff * 1024L)
                        val progress = if (totalBytes > 0) (totalBytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0.5f

                        updateModelStatus(model.id) {
                            it.copy(
                                status = ModelDownloadStatus.DOWNLOADING,
                                downloadProgress = progress,
                                downloadedBytes = totalBytesRead,
                                sizeBytes = totalBytes,
                                downloadSpeedKbps = speedKbps
                            )
                        }
                        lastProgressUpdate = now
                        bytesSinceLastUpdate = 0L
                    }
                }

                outputStream.flush()

                withContext(Dispatchers.Main) {
                    updateModelStatus(model.id) {
                        it.copy(
                            status = ModelDownloadStatus.COMPLETED,
                            isDownloaded = true,
                            downloadProgress = 1.0f,
                            downloadedBytes = totalBytesRead,
                            sizeBytes = totalBytesRead,
                            downloadSpeedKbps = 0L,
                            localFilePath = targetFile.absolutePath
                        )
                    }
                }
            } catch (e: CancellationException) {
                if (targetFile.exists()) targetFile.delete()
                withContext(Dispatchers.Main) {
                    updateModelStatus(model.id) {
                        it.copy(
                            status = ModelDownloadStatus.IDLE,
                            downloadProgress = 0f,
                            downloadedBytes = 0L,
                            downloadSpeedKbps = 0L
                        )
                    }
                }
            } catch (e: Exception) {
                if (targetFile.exists()) targetFile.delete()
                withContext(Dispatchers.Main) {
                    updateModelStatus(model.id) {
                        it.copy(
                            status = ModelDownloadStatus.ERROR,
                            downloadProgress = 0f,
                            downloadedBytes = 0L,
                            downloadSpeedKbps = 0L,
                            errorMessage = e.message ?: "Download failed. Please check network connection."
                        )
                    }
                }
            } finally {
                try { inputStream?.close() } catch (_: Exception) {}
                try { outputStream?.close() } catch (_: Exception) {}
                downloadJobs.remove(model.id)
            }
        }

        downloadJobs[model.id] = job
    }

    /**
     * Cancels an ongoing download and cleans up temporary bytes.
     */
    fun cancelDownload(modelId: String) {
        val job = downloadJobs.remove(modelId)
        job?.cancel()

        updateModelStatus(modelId) {
            it.copy(
                status = ModelDownloadStatus.IDLE,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                downloadSpeedKbps = 0L
            )
        }
    }

    /**
     * Deletes a downloaded model from disk.
     */
    fun deleteModel(modelId: String): Boolean {
        cancelDownload(modelId)

        val model = findModel(modelId) ?: return false
        val file = File(modelsDir, model.filename)
        val deleted = if (file.exists()) file.delete() else true

        updateModelStatus(modelId) {
            it.copy(
                isDownloaded = false,
                downloadProgress = 0f,
                downloadedBytes = 0L,
                status = ModelDownloadStatus.IDLE,
                localFilePath = null
            )
        }
        return deleted
    }

    fun getTotalDownloadedStorageBytes(): Long {
        val all = _curatedModels.value + _customModels.value
        return all.filter { it.isDownloaded }.sumOf { it.downloadedBytes }
    }

    private fun findModel(id: String): HuggingFaceModel? {
        return _curatedModels.value.find { it.id == id } ?: _customModels.value.find { it.id == id }
    }

    private fun updateModelStatus(modelId: String, transform: (HuggingFaceModel) -> HuggingFaceModel) {
        _curatedModels.value = _curatedModels.value.map {
            if (it.id == modelId) transform(it) else it
        }
        _customModels.value = _customModels.value.map {
            if (it.id == modelId) transform(it) else it
        }
    }
}
