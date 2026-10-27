package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HuggingFaceModel
import com.example.model.ModelType
import com.example.service.InferenceMode
import com.example.ui.theme.AiAccentGreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSafe

data class ModelInfoItem(
    val modelType: ModelType?,
    val name: String,
    val subtitle: String,
    val description: String,
    val memoryFootprint: String,
    val icon: ImageVector,
    val isCloud: Boolean = false
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelectorSheet(
    activeModel: ModelType?,
    inferenceMode: InferenceMode,
    downloadedModels: List<HuggingFaceModel> = emptyList(),
    onSelectModel: (ModelType) -> Unit,
    onLoadDownloadedModel: (HuggingFaceModel) -> Unit = {},
    onToggleInferenceMode: () -> Unit,
    onUnloadActiveModel: () -> Unit,
    onOpenModelHub: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    val registeredModels = listOf(
        ModelInfoItem(
            modelType = ModelType.REASONING,
            name = "Qwen 2.5 (1.5B-Instruct)",
            subtitle = "Primary Reasoning LLM • Offline",
            description = "Handles deep text reasoning, question answering, and document synthesis. Quantized to Q4_K_M for 4GB RAM devices.",
            memoryFootprint = "~980 MB RAM",
            icon = Icons.Default.Memory
        ),
        ModelInfoItem(
            modelType = ModelType.VISION_OCR,
            name = "Google ML Kit Vision",
            subtitle = "On-Device Neural OCR • Offline",
            description = "High-speed, zero-latency optical character recognition for scanned receipts, documents, and photos.",
            memoryFootprint = "~18 MB RAM",
            icon = Icons.Default.Visibility
        ),
        ModelInfoItem(
            modelType = ModelType.EMBEDDING,
            name = "all-MiniLM-L6-v2",
            subtitle = "Local Vector Embeddings • Offline",
            description = "Generates 384-dimensional semantic embeddings for local SQLite vector indexing and document retrieval.",
            memoryFootprint = "~85 MB RAM",
            icon = Icons.Default.Description
        ),
        ModelInfoItem(
            modelType = null,
            name = "Gemini Live (Cloud)",
            subtitle = "Multimodal Cloud Intelligence",
            description = "Optional online fallback for real-time visual reasoning and complex cloud multimodal queries.",
            memoryFootprint = "0 MB (Network)",
            icon = Icons.Default.Cloud,
            isCloud = true
        )
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = {
            Surface(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f)
            ) {}
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("model_selector_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Active Model",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Zero-concurrency: Only 1 model active in RAM",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onOpenModelHub()
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Model Hub", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Standard built-in / system engines
            registeredModels.forEach { item ->
                val isSelected = if (item.isCloud) {
                    inferenceMode == InferenceMode.GEMINI_LIVE
                } else {
                    inferenceMode == InferenceMode.OFFLINE_EDGE_AI && activeModel == item.modelType
                }

                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .border(
                            width = if (isSelected) 1.2.dp else 0.5.dp,
                            color = if (isSelected) AiAccentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable {
                            if (item.isCloud) {
                                if (inferenceMode != InferenceMode.GEMINI_LIVE) onToggleInferenceMode()
                            } else {
                                if (inferenceMode == InferenceMode.GEMINI_LIVE) onToggleInferenceMode()
                                item.modelType?.let { onSelectModel(it) }
                            }
                            onDismiss()
                        }
                        .testTag("model_item_${item.name.lowercase().take(10)}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) AiAccentGreen.copy(alpha = 0.2f)
                            else MaterialTheme.colorScheme.surface,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = item.icon,
                                    contentDescription = null,
                                    tint = if (isSelected) AiAccentGreen else MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = item.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "• ${item.memoryFootprint}",
                                    fontSize = 11.sp,
                                    color = if (item.isCloud) MaterialTheme.colorScheme.primary else EmeraldSafe
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = item.subtitle,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
                            )
                        }

                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = AiAccentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Downloaded Hugging Face Models
            val downloadedList = downloadedModels.filter { it.isDownloaded }
            if (downloadedList.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Downloaded Hugging Face Models",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))

                downloadedList.forEach { hf ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable {
                                onLoadDownloadedModel(hf)
                                onDismiss()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = null,
                                tint = AiAccentGreen,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = hf.name,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${hf.sizeMB} • ~${hf.ramEstimateMB}MB RAM (${hf.quantization})",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(14.dp))

            // Option to unload model manually
            if (activeModel != null) {
                Surface(
                    color = AmberWarning.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onUnloadActiveModel()
                            onDismiss()
                        }
                        .testTag("unload_model_action")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.RemoveCircleOutline,
                            contentDescription = null,
                            tint = AmberWarning,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Unload Active Model to Maximize Free RAM",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AmberWarning
                        )
                    }
                }
            }
        }
    }
}
