package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.AiAccentGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentSheet(
    onPickDocument: () -> Unit,
    onLaunchCamera: () -> Unit,
    onPickPhoto: () -> Unit,
    onOpenVault: () -> Unit,
    onOpenInspector: () -> Unit,
    onStartVoiceChat: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .testTag("attachment_sheet")
        ) {
            Text(
                text = "Add to Conversation",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(14.dp))

            AttachmentOptionRow(
                icon = Icons.Default.GraphicEq,
                title = "Live Voice Chat",
                subtitle = "Speak directly to AI with live hearing and speech synthesis",
                onClick = {
                    onDismiss()
                    onStartVoiceChat()
                },
                testTag = "attach_voice_option"
            )

            AttachmentOptionRow(
                icon = Icons.Default.Description,
                title = "Upload Document or PDF",
                subtitle = "Extracts text, chunks, and indexes in SQLite for local RAG",
                onClick = {
                    onDismiss()
                    onPickDocument()
                },
                testTag = "attach_doc_option"
            )

            AttachmentOptionRow(
                icon = Icons.Default.CameraAlt,
                title = "Scan with Camera",
                subtitle = "Capture a photo of a document or receipt for on-device OCR",
                onClick = {
                    onDismiss()
                    onLaunchCamera()
                },
                testTag = "attach_camera_option"
            )

            AttachmentOptionRow(
                icon = Icons.Default.Image,
                title = "Photo Library",
                subtitle = "Select an existing photo or screenshot for OCR analysis",
                onClick = {
                    onDismiss()
                    onPickPhoto()
                },
                testTag = "attach_photo_option"
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
            Spacer(modifier = Modifier.height(8.dp))

            AttachmentOptionRow(
                icon = Icons.Default.Layers,
                title = "Document Vault",
                subtitle = "Manage indexed documents and search local vector database",
                onClick = {
                    onDismiss()
                    onOpenVault()
                },
                testTag = "attach_vault_option"
            )

            AttachmentOptionRow(
                icon = Icons.Default.Hub,
                title = "RAM & Scenario Inspector",
                subtitle = "Inspect zero-concurrency state transitions and memory metrics",
                onClick = {
                    onDismiss()
                    onOpenInspector()
                },
                testTag = "attach_inspector_option"
            )
        }
    }
}

@Composable
private fun AttachmentOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    testTag: String
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AiAccentGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}
