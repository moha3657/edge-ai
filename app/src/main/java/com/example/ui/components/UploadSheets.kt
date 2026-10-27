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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Receipt
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
import com.example.service.SampleDataRepository
import com.example.service.SampleDocItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadOptionsSheet(
    onDismiss: () -> Unit,
    onSelectSampleDoc: (SampleDocItem) -> Unit,
    onPickFromStorage: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("upload_options_sheet")
        ) {
            Text(
                text = "UPLOAD / SELECT DOCUMENT",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Choose an offline document to chunk and vectorize into local SQLite:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Storage Picker Option
            SheetOptionItem(
                icon = Icons.Default.FolderOpen,
                title = "Choose File from Device Storage",
                subtitle = "Pick any PDF, TXT, or DOCX from device storage",
                onClick = {
                    onDismiss()
                    onPickFromStorage()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "FAST PRELOADED TEST SAMPLES (OFFLINE)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(6.dp))

            SampleDataRepository.sampleDocs.filter { it.type != "IMAGE_OCR" }.forEach { sample ->
                SheetOptionItem(
                    icon = if (sample.type == "PDF") Icons.Default.PictureAsPdf else Icons.Default.Description,
                    title = sample.title,
                    subtitle = sample.preview,
                    onClick = {
                        onDismiss()
                        onSelectSampleDoc(sample)
                    }
                )
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOptionsSheet(
    onDismiss: () -> Unit,
    onSelectSampleReceipt: (SampleDocItem) -> Unit,
    onLaunchCamera: () -> Unit,
    onPickImage: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("scan_options_sheet")
        ) {
            Text(
                text = "SCAN / OCR IMAGE (SCENARIO A)",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                ),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Extract text via Vision OCR, unload OCR, then explain using local Qwen2.5-1.5B:",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline
            )

            Spacer(modifier = Modifier.height(14.dp))

            SheetOptionItem(
                icon = Icons.Default.CameraAlt,
                title = "Take Photo with Camera",
                subtitle = "Capture a physical document or invoice directly",
                onClick = {
                    onDismiss()
                    onLaunchCamera()
                }
            )

            SheetOptionItem(
                icon = Icons.Default.PhotoLibrary,
                title = "Pick Image from Gallery",
                subtitle = "Zero-permission Android Photo Picker",
                onClick = {
                    onDismiss()
                    onPickImage()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "FAST TEST OCR SAMPLE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(modifier = Modifier.height(6.dp))

            val receiptSample = SampleDataRepository.sampleDocs.first { it.type == "IMAGE_OCR" }
            SheetOptionItem(
                icon = Icons.Default.Receipt,
                title = "Sample Medical Pharmacy Receipt",
                subtitle = "Includes itemized Rx prescriptions, dosages, and totals",
                onClick = {
                    onDismiss()
                    onSelectSampleReceipt(receiptSample)
                }
            )

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SheetOptionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1
                )
            }
        }
    }
}
