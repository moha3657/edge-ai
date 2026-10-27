package com.example.ui.components

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
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.service.DeviceMemorySnapshot
import com.example.service.InferenceMode
import com.example.ui.theme.AiAccentGreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    inferenceMode: InferenceMode,
    onToggleInferenceMode: () -> Unit,
    memorySnapshot: DeviceMemorySnapshot,
    onForceGC: () -> Unit,
    onClearConversations: () -> Unit,
    onClearVault: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var showClearConversationsConfirm by remember { mutableStateOf(false) }
    var showClearVaultConfirm by remember { mutableStateOf(false) }

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
                .padding(bottom = 36.dp)
                .testTag("settings_sheet")
        ) {
            Text(
                text = "Settings",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(18.dp))

            // SECTION 1: APPEARANCE
            SettingsSectionHeader(icon = Icons.Default.Palette, title = "Appearance")
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    ThemeOptionRow(
                        label = "System Default",
                        selected = themeMode == ThemeMode.SYSTEM,
                        onClick = { onThemeModeChange(ThemeMode.SYSTEM) }
                    )
                    ThemeOptionRow(
                        label = "Dark (Deep Obsidian)",
                        selected = themeMode == ThemeMode.DARK,
                        onClick = { onThemeModeChange(ThemeMode.DARK) }
                    )
                    ThemeOptionRow(
                        label = "Light (Clean Paper)",
                        selected = themeMode == ThemeMode.LIGHT,
                        onClick = { onThemeModeChange(ThemeMode.LIGHT) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 2: AI INFERENCE & OFFLINE ENGINE
            SettingsSectionHeader(icon = Icons.Default.WifiOff, title = "AI Inference Mode")
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (inferenceMode == InferenceMode.OFFLINE_EDGE_AI) "100% Offline Edge Mode" else "Gemini Live (Cloud)",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (inferenceMode == InferenceMode.OFFLINE_EDGE_AI)
                                "Zero external network calls. All reasoning & OCR executed on device."
                            else "Cloud multimodal reasoning enabled for real-time visual analysis.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }

                    Switch(
                        checked = inferenceMode == InferenceMode.GEMINI_LIVE,
                        onCheckedChange = { onToggleInferenceMode() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("toggle_offline_switch")
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 3: HARDWARE & RAM MANAGEMENT
            SettingsSectionHeader(icon = Icons.Default.Memory, title = "Hardware & Memory Optimization")
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RAM Available",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "${memorySnapshot.availRamMB} MB / ${memorySnapshot.totalRamMB} MB",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSafe
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Context Ceiling",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "1024 Tokens (4GB Safe)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Zero-Concurrency Lock",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Text(
                            text = "Active (Exclusive RAM Mutex)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = AiAccentGreen
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onForceGC() }
                            .testTag("force_gc_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CleaningServices,
                                contentDescription = null,
                                tint = AmberWarning,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Unload Model & Run Garbage Collection",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AmberWarning
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 4: DATA & STORAGE
            SettingsSectionHeader(icon = Icons.Default.DeleteSweep, title = "Data Management")
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    TextButton(
                        onClick = { showClearConversationsConfirm = true },
                        modifier = Modifier.fillMaxWidth().testTag("clear_conversations_btn")
                    ) {
                        Text(
                            text = "Clear All Conversations",
                            color = RoseDanger,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                    TextButton(
                        onClick = { showClearVaultConfirm = true },
                        modifier = Modifier.fillMaxWidth().testTag("clear_vault_btn")
                    ) {
                        Text(
                            text = "Clear Document Vault & Embeddings",
                            color = RoseDanger,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // SECTION 5: ABOUT
            SettingsSectionHeader(icon = Icons.Default.Info, title = "About & Privacy")
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "EdgeAI Offline v1.2.0",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "A private, on-device mobile AI assistant designed for low-memory Android hardware. All vector embeddings, document chunking, and neural OCR processes happen locally on your device.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        lineHeight = 17.sp
                    )
                }
            }
        }
    }

    // Confirmation dialogs
    if (showClearConversationsConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConversationsConfirm = false },
            title = { Text("Clear All Conversations?") },
            text = { Text("This will permanently delete your stored conversations and message histories.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearConversations()
                    showClearConversationsConfirm = false
                }) {
                    Text("Delete All", color = RoseDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConversationsConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showClearVaultConfirm) {
        AlertDialog(
            onDismissRequest = { showClearVaultConfirm = false },
            title = { Text("Clear Document Vault?") },
            text = { Text("This will remove all indexed PDFs, text documents, and vector embeddings.") },
            confirmButton = {
                TextButton(onClick = {
                    onClearVault()
                    showClearVaultConfirm = false
                }) {
                    Text("Clear Vault", color = RoseDanger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearVaultConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AiAccentGreen,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(selectedColor = AiAccentGreen)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
