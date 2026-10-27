package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.HuggingFaceModel
import com.example.model.UserSettings
import com.example.service.DeviceMemorySnapshot
import com.example.service.InferenceMode
import com.example.ui.theme.AiAccentGreen
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.ThemeMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsSheet(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    inferenceMode: InferenceMode,
    onToggleInferenceMode: () -> Unit,
    memorySnapshot: DeviceMemorySnapshot,
    userSettings: UserSettings,
    onUpdateUserSettings: (UserSettings) -> Unit,
    curatedModels: List<HuggingFaceModel>,
    customModels: List<HuggingFaceModel>,
    onDownloadModel: (HuggingFaceModel) -> Unit,
    onDownloadCustomUrl: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onLoadModel: (HuggingFaceModel) -> Unit,
    onForceGC: () -> Unit,
    onClearConversations: () -> Unit,
    onClearVault: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollState = rememberScrollState()

    var selectedTab by remember { mutableIntStateOf(0) }
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
                .padding(bottom = 24.dp)
                .testTag("settings_sheet")
        ) {
            // Sheet Title & Dismiss
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Assistant Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                TextButton(onClick = onDismiss) {
                    Text("Done", fontWeight = FontWeight.Bold, color = AiAccentGreen)
                }
            }

            // Segmented Tabs
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = AiAccentGreen,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = AiAccentGreen
                    )
                },
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Inference", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Hugging Face", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("General & RAM", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) },
                    icon = { Icon(Icons.Default.Memory, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Scrollable Content
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp)
            ) {
                when (selectedTab) {
                    0 -> {
                        // TAB 0: INFERENCE PARAMETERS & PROMPTS
                        InferenceSettingsTab(
                            settings = userSettings,
                            onUpdate = onUpdateUserSettings
                        )
                    }

                    1 -> {
                        // TAB 1: HUGGING FACE MODEL HUB
                        HuggingFaceTab(
                            curatedModels = curatedModels,
                            customModels = customModels,
                            onDownloadModel = onDownloadModel,
                            onDownloadCustomUrl = onDownloadCustomUrl,
                            onCancelDownload = onCancelDownload,
                            onDeleteModel = onDeleteModel,
                            onLoadModel = onLoadModel
                        )
                    }

                    2 -> {
                        // TAB 2: GENERAL, HARDWARE & DATA
                        GeneralAndHardwareTab(
                            themeMode = themeMode,
                            onThemeModeChange = onThemeModeChange,
                            inferenceMode = inferenceMode,
                            onToggleInferenceMode = onToggleInferenceMode,
                            memorySnapshot = memorySnapshot,
                            onForceGC = onForceGC,
                            onClearConversations = { showClearConversationsConfirm = true },
                            onClearVault = { showClearVaultConfirm = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun InferenceSettingsTab(
    settings: UserSettings,
    onUpdate: (UserSettings) -> Unit
) {
    // 1. SYSTEM PROMPT
    SettingsSectionHeader(icon = Icons.Default.Tune, title = "System Prompt")
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Defines assistant behavior, tone, reasoning methodology, and output format.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            OutlinedTextField(
                value = settings.systemPrompt,
                onValueChange = { onUpdate(settings.copy(systemPrompt = it)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("system_prompt_input"),
                minLines = 3,
                maxLines = 6,
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AiAccentGreen,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, lineHeight = 18.sp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Quick Presets:",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(6.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UserSettings.PRESET_PROMPTS.forEach { preset ->
                    Surface(
                        color = if (settings.systemPrompt == preset.prompt) AiAccentGreen.copy(alpha = 0.2f)
                        else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .clickable { onUpdate(settings.copy(systemPrompt = preset.prompt)) }
                            .border(
                                width = 0.5.dp,
                                color = if (settings.systemPrompt == preset.prompt) AiAccentGreen else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Text(
                            text = preset.name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (settings.systemPrompt == preset.prompt) AiAccentGreen else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = { onUpdate(settings.copy(systemPrompt = UserSettings.DEFAULT_SYSTEM_PROMPT)) }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset to Default", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 2. CONTEXT WINDOW CEILING
    SettingsSectionHeader(icon = Icons.Default.Memory, title = "Context Window")
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = "Max token history stored in RAM. 1,024 tokens is optimal for 4GB RAM devices.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UserSettings.CONTEXT_WINDOW_OPTIONS.forEach { option ->
                    val isSelected = settings.contextWindowTokens == option
                    Surface(
                        color = if (isSelected) AiAccentGreen else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .clickable { onUpdate(settings.copy(contextWindowTokens = option)) }
                            .testTag("context_chip_$option")
                    ) {
                        Text(
                            text = if (option == 1024) "1,024 (Default)" else "$option tokens",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(20.dp))

    // 3. TEMPERATURE
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        SettingsSectionHeader(icon = Icons.Default.Tune, title = "Temperature")
        Text(
            text = String.format(java.util.Locale.US, "%.2f", settings.temperature),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AiAccentGreen
        )
    }
    Spacer(modifier = Modifier.height(4.dp))
    Text(
        text = if (settings.temperature <= 0.3f) "Precise & Deterministic (Best for math, coding, factual Q&A)"
        else if (settings.temperature <= 0.8f) "Balanced & Coherent (Recommended for conversation)"
        else "Creative & Expressive (Brainstorming & storytelling)",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)
    )
    Spacer(modifier = Modifier.height(4.dp))

    Slider(
        value = settings.temperature,
        onValueChange = { onUpdate(settings.copy(temperature = it)) },
        valueRange = 0.0f..1.5f,
        steps = 14,
        colors = SliderDefaults.colors(
            thumbColor = AiAccentGreen,
            activeTrackColor = AiAccentGreen
        ),
        modifier = Modifier.testTag("temp_slider")
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 4. TOP-P (NUCLEUS SAMPLING)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Top-P (Nucleus Sampling)",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = String.format(java.util.Locale.US, "%.2f", settings.topP),
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AiAccentGreen
        )
    }
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = "Restricts token probability mass. 0.90 ensures natural coherence without straying.",
        fontSize = 12.sp,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    )
    Slider(
        value = settings.topP,
        onValueChange = { onUpdate(settings.copy(topP = it)) },
        valueRange = 0.1f..1.0f,
        steps = 8,
        colors = SliderDefaults.colors(
            thumbColor = AiAccentGreen,
            activeTrackColor = AiAccentGreen
        ),
        modifier = Modifier.testTag("top_p_slider")
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 5. MAX OUTPUT TOKENS
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Max Generation Output",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${settings.maxOutputTokens} tokens",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = AiAccentGreen
        )
    }
    Spacer(modifier = Modifier.height(2.dp))
    Slider(
        value = settings.maxOutputTokens.toFloat(),
        onValueChange = { onUpdate(settings.copy(maxOutputTokens = it.toInt())) },
        valueRange = 64f..1024f,
        steps = 14,
        colors = SliderDefaults.colors(
            thumbColor = AiAccentGreen,
            activeTrackColor = AiAccentGreen
        ),
        modifier = Modifier.testTag("max_output_tokens_slider")
    )

    Spacer(modifier = Modifier.height(16.dp))

    // 6. STREAMING SPEED
    Text(
        text = "Streaming Token Cadence",
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(6.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserSettings.STREAM_SPEED_OPTIONS.forEach { (label, speedMs) ->
            val isSelected = settings.streamDelayMs == speedMs
            Surface(
                color = if (isSelected) AiAccentGreen else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .clickable { onUpdate(settings.copy(streamDelayMs = speedMs)) }
            ) {
                Box(
                    modifier = Modifier.padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(16.dp))

    // 7. ZERO-CONCURRENCY AUTO EVICT
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
                    text = "Strict Mutex RAM Auto-Evict",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Automatically unloads active model from RAM when app is backgrounded to protect device stability.",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            Switch(
                checked = settings.autoEvictOnBackground,
                onCheckedChange = { onUpdate(settings.copy(autoEvictOnBackground = it)) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AiAccentGreen,
                    checkedTrackColor = AiAccentGreen.copy(alpha = 0.3f)
                )
            )
        }
    }
}

@Composable
private fun HuggingFaceTab(
    curatedModels: List<HuggingFaceModel>,
    customModels: List<HuggingFaceModel>,
    onDownloadModel: (HuggingFaceModel) -> Unit,
    onDownloadCustomUrl: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onDeleteModel: (String) -> Unit,
    onLoadModel: (HuggingFaceModel) -> Unit
) {
    HuggingFaceModelHubScreen(
        curatedModels = curatedModels,
        customModels = customModels,
        onDownloadModel = onDownloadModel,
        onDownloadCustomUrl = onDownloadCustomUrl,
        onCancelDownload = onCancelDownload,
        onDeleteModel = onDeleteModel,
        onLoadModel = onLoadModel
    )
}

@Composable
private fun GeneralAndHardwareTab(
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    inferenceMode: InferenceMode,
    onToggleInferenceMode: () -> Unit,
    memorySnapshot: DeviceMemorySnapshot,
    onForceGC: () -> Unit,
    onClearConversations: () -> Unit,
    onClearVault: () -> Unit
) {
    // APPEARANCE
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

    // AI INFERENCE MODE
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

    // HARDWARE & RAM MANAGEMENT
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
                    text = "Zero-Concurrency Mutex",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
                Text(
                    text = "Active & Enforced",
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

    // DATA & STORAGE
    SettingsSectionHeader(icon = Icons.Default.DeleteSweep, title = "Data Management")
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            TextButton(
                onClick = onClearConversations,
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
                onClick = onClearVault,
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

    // ABOUT
    SettingsSectionHeader(icon = Icons.Default.Info, title = "About & Privacy")
    Spacer(modifier = Modifier.height(8.dp))

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "EdgeAI Offline v1.3.0",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "A private, on-device mobile AI assistant designed for low-memory Android hardware. All vector embeddings, document chunking, downloaded Hugging Face models, and neural OCR processes happen locally on your device.",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                lineHeight = 17.sp
            )
        }
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
