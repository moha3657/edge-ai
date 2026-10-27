package com.example.ui.components

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ModelType
import com.example.service.InferenceMode
import com.example.ui.theme.AiAccentGreen
import com.example.ui.theme.AiAccentGreenLight
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.RoseDanger
import com.example.viewmodel.ChatMessage
import com.example.viewmodel.GenerationState
import com.example.viewmodel.MessageSender

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainChatScreen(
    messages: List<ChatMessage>,
    isBusy: Boolean,
    generationState: GenerationState,
    activeModel: ModelType?,
    inferenceMode: InferenceMode,
    isSpeakingTts: Boolean = false,
    activeSpeakingMessageId: String? = null,
    onOpenDrawer: () -> Unit,
    onOpenModelSelector: () -> Unit,
    onNewChat: () -> Unit,
    onOpenLiveVoice: () -> Unit,
    onSpeakMessage: (id: String, text: String) -> Unit,
    onSendMessage: (String) -> Unit,
    onStopGeneration: () -> Unit,
    onRegenerateResponse: () -> Unit,
    onOpenAttachmentSheet: () -> Unit,
    onPickDocumentPrompt: () -> Unit,
    onAnalyzeImagePrompt: () -> Unit,
    onExplainConceptPrompt: () -> Unit,
    onSearchFilesPrompt: () -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    // Automatically scroll to bottom when new messages arrive or stream updates
    LaunchedEffect(messages.size, messages.lastOrNull()?.text?.length) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val modelDisplayTitle = remember(activeModel, inferenceMode) {
        if (inferenceMode == InferenceMode.GEMINI_LIVE) "Gemini Live"
        else when (activeModel) {
            ModelType.REASONING -> "Qwen 2.5 (1.5B)"
            ModelType.VISION_OCR -> "ML Kit Vision OCR"
            ModelType.EMBEDDING -> "MiniLM Embeddings"
            null -> "Qwen 2.5 (1.5B)"
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("main_chat_screen")
    ) {
        // TOP APP BAR (Compact ChatGPT Style)
        TopAppBar(
            title = {
                // Centered Model Selector Pill
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier
                        .clickable(onClick = onOpenModelSelector)
                        .testTag("top_bar_model_selector")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = if (inferenceMode == InferenceMode.GEMINI_LIVE) MaterialTheme.colorScheme.primary
                            else AiAccentGreen,
                            modifier = Modifier.size(7.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(7.dp))
                        Text(
                            text = modelDisplayTitle,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Select Model",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = onOpenDrawer,
                    modifier = Modifier.testTag("top_bar_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Open Navigation Drawer",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            },
            actions = {
                IconButton(
                    onClick = onOpenLiveVoice,
                    modifier = Modifier.testTag("top_bar_live_voice_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Live Voice Chat",
                        tint = AiAccentGreen,
                        modifier = Modifier.size(22.dp)
                    )
                }
                IconButton(
                    onClick = onNewChat,
                    modifier = Modifier.testTag("top_bar_new_chat_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.EditNote,
                        contentDescription = "New Chat",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(22.dp)
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background
            )
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

        // CHAT CONVERSATION OR EMPTY STATE
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (messages.isEmpty()) {
                // Empty State with Elegant Suggestions
                EmptyChatWelcomeView(
                    onSummarizeDoc = onPickDocumentPrompt,
                    onExplainConcept = {
                        inputText = "Explain how neural networks and local language models process text on mobile devices."
                        onExplainConceptPrompt()
                    },
                    onAnalyzeImage = onAnalyzeImagePrompt,
                    onSearchFiles = onSearchFilesPrompt
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(messages, key = { it.id }) { msg ->
                        ConversationMessageRow(
                            message = msg,
                            isSpeakingTts = isSpeakingTts,
                            activeSpeakingMessageId = activeSpeakingMessageId,
                            onSpeakMessage = onSpeakMessage,
                            onRegenerate = onRegenerateResponse
                        )
                    }

                    // Dynamic State Banners (Preparing / Generating / OOM)
                    if (isBusy && messages.lastOrNull()?.isStreaming != true) {
                        item {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = AiAccentGreen
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = if (generationState == GenerationState.PREPARING)
                                            "Allocating zero-concurrency RAM & loading weights..."
                                        else "Thinking on-device...",
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                    }

                    if (generationState == GenerationState.OUT_OF_MEMORY) {
                        item {
                            Surface(
                                color = RoseDanger.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = RoseDanger,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Low Memory Warning: Model inference paused to protect device RAM.",
                                        fontSize = 12.sp,
                                        color = RoseDanger
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // FLOATING ROUNDED MESSAGE COMPOSER
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(26.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            0.7.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                            RoundedCornerShape(26.dp)
                        )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Plus / Attachment Button
                        IconButton(
                            onClick = onOpenAttachmentSheet,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .testTag("composer_plus_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add Attachment or Tool",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Multiline Expanding Text Input
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "Message your AI assistant...",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                                )
                            },
                            maxLines = 5,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("composer_text_input"),
                            shape = RoundedCornerShape(20.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                cursorColor = AiAccentGreen
                            )
                        )

                        Spacer(modifier = Modifier.width(4.dp))

                        // Send / Stop Generation Morphing Action Button
                        if (isBusy) {
                            // Stop Generation Button
                            FilledIconButton(
                                onClick = onStopGeneration,
                                colors = IconButtonDefaults.filledIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.onSurface,
                                    contentColor = MaterialTheme.colorScheme.surface
                                ),
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .testTag("composer_stop_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop Generating",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            val canSend = inputText.isNotBlank()
                            if (canSend) {
                                FilledIconButton(
                                    onClick = {
                                        onSendMessage(inputText.trim())
                                        inputText = ""
                                    },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = AiAccentGreen,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .testTag("composer_send_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Send Message",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            } else {
                                // Live Voice Chat Button (ChatGPT-style waveform)
                                FilledIconButton(
                                    onClick = onOpenLiveVoice,
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = AiAccentGreen,
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .testTag("composer_live_voice_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Live Voice Mode",
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Centered empty conversation state inspired by ChatGPT's welcoming screen
 */
@Composable
private fun EmptyChatWelcomeView(
    onSummarizeDoc: () -> Unit,
    onExplainConcept: () -> Unit,
    onAnalyzeImage: () -> Unit,
    onSearchFiles: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = AiAccentGreen.copy(alpha = 0.12f),
            modifier = Modifier.size(54.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = AiAccentGreen,
                    modifier = Modifier.size(28.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "How can I help you today?",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "100% on-device neural reasoning & document intelligence.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        )

        Spacer(modifier = Modifier.height(28.dp))

        // Compact Suggestion Chips
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChipPill(
                    icon = Icons.Default.Description,
                    label = "Summarize a document",
                    onClick = onSummarizeDoc,
                    modifier = Modifier.weight(1f)
                )
                SuggestionChipPill(
                    icon = Icons.Default.Lightbulb,
                    label = "Explain a concept",
                    onClick = onExplainConcept,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChipPill(
                    icon = Icons.Default.CameraAlt,
                    label = "Analyze an image",
                    onClick = onAnalyzeImage,
                    modifier = Modifier.weight(1f)
                )
                SuggestionChipPill(
                    icon = Icons.Default.Layers,
                    label = "Search my files",
                    onClick = onSearchFiles,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SuggestionChipPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
            .border(
                0.5.dp,
                MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AiAccentGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

/**
 * Message Row supporting clean user bubble and open Markdown assistant layout
 */
@Composable
private fun ConversationMessageRow(
    message: ChatMessage,
    isSpeakingTts: Boolean = false,
    activeSpeakingMessageId: String? = null,
    onSpeakMessage: (id: String, text: String) -> Unit = { _, _ -> },
    onRegenerate: () -> Unit
) {
    val isUser = message.sender == MessageSender.USER
    val isAlert = message.sender == MessageSender.SYSTEM_ALERT
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            // USER MESSAGE BUBBLE
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(
                    topStart = 18.dp,
                    topEnd = 18.dp,
                    bottomStart = 18.dp,
                    bottomEnd = 4.dp
                ),
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                        RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
                    )
            ) {
                SelectionContainer {
                    Text(
                        text = message.text,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        } else if (isAlert) {
            // SYSTEM ALERT MESSAGE
            Surface(
                color = AmberWarning.copy(alpha = 0.1f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = AmberWarning,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = message.text,
                        fontSize = 13.sp,
                        color = AmberWarning
                    )
                }
            }
        } else {
            // ASSISTANT MESSAGE (Clean ChatGPT Reading Layout)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                // Attribution Header
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = AiAccentGreen,
                        modifier = Modifier.size(6.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = message.modelTag ?: "Assistant",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AiAccentGreen
                    )
                    if (message.tokenCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "• ${message.tokenCount} tokens",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }
                }

                // Render Markdown Content
                SelectionContainer {
                    MarkdownContentView(content = message.text)
                }

                // Retrieved Citations if present
                if (message.retrievedExcerpts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Indexed Excerpt References (${message.retrievedExcerpts.size})",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            message.retrievedExcerpts.forEach { chunk ->
                                Text(
                                    text = "• ${chunk.documentTitle} (Page ${chunk.chunk.pageNumber}) — ${(chunk.similarityScore * 100).toInt()}% vector match",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)
                                )
                            }
                        }
                    }
                }

                // Completed Response Action Row (Copy, Share, Regenerate)
                if (!message.isStreaming && message.text.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Copy Button
                        IconButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(message.text))
                                copied = true
                                Toast.makeText(context, "Copied response to clipboard", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                                contentDescription = "Copy Response",
                                tint = if (copied) EmeraldSafe else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Share Button
                        IconButton(
                            onClick = {
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, message.text)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share Assistant Response"))
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Response",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Read Aloud / Stop Speaking Button
                        val isThisMessageSpeaking = isSpeakingTts && activeSpeakingMessageId == message.id
                        IconButton(
                            onClick = { onSpeakMessage(message.id, message.text) },
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("speak_message_${message.id.take(4)}")
                        ) {
                            Icon(
                                imageVector = if (isThisMessageSpeaking) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = if (isThisMessageSpeaking) "Stop Speaking" else "Read Aloud",
                                tint = if (isThisMessageSpeaking) AiAccentGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Regenerate Button
                        IconButton(
                            onClick = onRegenerate,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Regenerate Response",
                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Lightweight Markdown renderer supporting headers, code blocks, bold, and bullet points
 */
@Composable
private fun MarkdownContentView(content: String) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val codeBlockRegex = Regex("```([a-zA-Z0-9_-]*)\\n?([\\s\\S]*?)```")
    val parts = mutableListOf<MarkdownPart>()

    var lastIndex = 0
    for (match in codeBlockRegex.findAll(content)) {
        if (match.range.first > lastIndex) {
            val textBefore = content.substring(lastIndex, match.range.first)
            if (textBefore.isNotBlank()) parts.add(MarkdownPart.Text(textBefore))
        }
        val lang = match.groupValues[1].ifBlank { "code" }
        val code = match.groupValues[2].trimEnd()
        parts.add(MarkdownPart.CodeBlock(lang, code))
        lastIndex = match.range.last + 1
    }
    if (lastIndex < content.length) {
        val remaining = content.substring(lastIndex)
        if (remaining.isNotBlank()) parts.add(MarkdownPart.Text(remaining))
    }

    if (parts.isEmpty()) {
        parts.add(MarkdownPart.Text(content))
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        parts.forEach { part ->
            when (part) {
                is MarkdownPart.Text -> {
                    val lines = part.text.split("\n")
                    lines.forEach { line ->
                        when {
                            line.startsWith("### ") -> {
                                Text(
                                    text = line.removePrefix("### "),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            line.startsWith("## ") -> {
                                Text(
                                    text = line.removePrefix("## "),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            line.startsWith("# ") -> {
                                Text(
                                    text = line.removePrefix("# "),
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            line.startsWith("• ") || line.startsWith("- ") -> {
                                Row(modifier = Modifier.padding(start = 6.dp, top = 2.dp)) {
                                    Text(
                                        text = "•",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = AiAccentGreen
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = formatInlineMarkdown(line.substring(2)),
                                        fontSize = 15.sp,
                                        lineHeight = 22.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            line.isNotBlank() -> {
                                Text(
                                    text = formatInlineMarkdown(line),
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
                is MarkdownPart.CodeBlock -> {
                    Surface(
                        color = Color(0xFF1E1E24),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(0.5.dp, Color(0xFF33333E), RoundedCornerShape(10.dp))
                    ) {
                        Column {
                            // Code block bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16161B))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = part.language,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFFAAAAAA)
                                )
                                Row(
                                    modifier = Modifier.clickable {
                                        clipboardManager.setText(AnnotatedString(part.code))
                                        Toast.makeText(context, "Code copied", Toast.LENGTH_SHORT).show()
                                    },
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCopy,
                                        contentDescription = "Copy code",
                                        tint = Color(0xFFAAAAAA),
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "Copy",
                                        fontSize = 11.sp,
                                        color = Color(0xFFAAAAAA)
                                    )
                                }
                            }

                            // Code text
                            Text(
                                text = part.code,
                                fontSize = 13.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color(0xFFF1F1F1),
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed class MarkdownPart {
    data class Text(val text: String) : MarkdownPart()
    data class CodeBlock(val language: String, val code: String) : MarkdownPart()
}

/**
 * Format **bold**, *italic*, and `code` inline spans, safely cleaning up any stray markdown markers.
 */
private fun formatInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        val regex = Regex("(\\*{2}(.+?)\\*{2})|(`([^`]+?)`)|(\\*(.+?)\\*)")
        for (match in regex.findAll(text)) {
            if (match.range.first > cursor) {
                append(text.substring(cursor, match.range.first).replace("**", ""))
            }
            if (match.value.startsWith("**")) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(match.groupValues[2])
                }
            } else if (match.value.startsWith("`")) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x22888888)
                    )
                ) {
                    append(match.groupValues[4])
                }
            } else if (match.value.startsWith("*")) {
                withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                    append(match.groupValues[6])
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < text.length) {
            append(text.substring(cursor).replace("**", ""))
        }
    }
}
