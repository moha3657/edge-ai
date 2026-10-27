package com.example.model

enum class ModelDownloadStatus {
    IDLE,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    ERROR
}

data class HuggingFaceModel(
    val id: String,
    val name: String,
    val repoId: String,
    val filename: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val ramEstimateMB: Int,
    val quantization: String,
    val parameterCount: String,
    val description: String,
    val tags: List<String> = emptyList(),
    val isCurated: Boolean = true,
    val isDownloaded: Boolean = false,
    val downloadProgress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val downloadSpeedKbps: Long = 0L,
    val status: ModelDownloadStatus = ModelDownloadStatus.IDLE,
    val localFilePath: String? = null,
    val errorMessage: String? = null
) {
    val sizeMB: String
        get() = String.format(java.util.Locale.US, "%.1f MB", sizeBytes / (1024.0 * 1024.0))

    val isEdgeFriendly: Boolean
        get() = ramEstimateMB <= 1600 // Fits comfortably in 4GB RAM devices
}
