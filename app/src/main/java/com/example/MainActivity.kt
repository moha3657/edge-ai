package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.core.content.ContextCompat
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.ModelType
import com.example.service.InferenceMode
import com.example.ui.components.AppNavigationDrawer
import com.example.ui.components.AttachmentSheet
import com.example.ui.components.DocumentVaultScreen
import com.example.ui.components.LiveVoiceChatSheet
import com.example.ui.components.MainChatScreen
import com.example.ui.components.ModelSelectorSheet
import com.example.ui.components.ScenarioInspectorScreen
import com.example.ui.components.SettingsSheet
import com.example.ui.theme.AiAccentGreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.MainViewModel
import kotlinx.coroutines.launch

enum class AppSubScreen {
    CHAT, VAULT, INSPECTOR
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: MainViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsState()

            MyApplicationTheme(themeMode = themeMode) {
                EdgeAIApp(viewModel = viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgeAIApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val memorySnapshot by viewModel.memorySnapshot.collectAsState()
    val activeModel by viewModel.activeModel.collectAsState()
    val inferenceMode by viewModel.inferenceMode.collectAsState()
    val chatMessages by viewModel.chatMessages.collectAsState()
    val isBusy by viewModel.isBusy.collectAsState()
    val generationState by viewModel.generationState.collectAsState()
    val conversations by viewModel.conversations.collectAsState()
    val currentConversationId by viewModel.currentConversationId.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()

    val isLiveVoiceActive by viewModel.isLiveVoiceActive.collectAsState()
    val voiceState by viewModel.voiceState.collectAsState()
    val userTranscript by viewModel.userTranscript.collectAsState()
    val aiSpokenText by viewModel.aiSpokenText.collectAsState()
    val audioLevels by viewModel.audioLevels.collectAsState()
    val isSpeakingTts by viewModel.isSpeakingTts.collectAsState()
    val activeUtteranceId by viewModel.activeUtteranceId.collectAsState()

    val pipelineSteps by viewModel.pipelineSteps.collectAsState()
    val transitionLogs by viewModel.transitionLogs.collectAsState()
    val savedDocuments by viewModel.savedDocuments.collectAsState()
    val activeScenarioTitle by viewModel.activeScenarioTitle.collectAsState()

    // Navigation and Sheet visibility states
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    var currentSubScreen by remember { mutableStateOf(AppSubScreen.CHAT) }

    var showModelSelectorSheet by remember { mutableStateOf(false) }
    var showAttachmentSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // System File Picker (PDF, TXT, DOCX)
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processDocumentUri(uri)
            currentSubScreen = AppSubScreen.CHAT
        }
    }

    // Photo Picker
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.processImageUri(uri)
            currentSubScreen = AppSubScreen.CHAT
        }
    }

    // Camera Launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bitmap: Bitmap? ->
        if (bitmap != null) {
            viewModel.processCameraBitmap(bitmap)
            currentSubScreen = AppSubScreen.CHAT
        }
    }

    // Audio Permission Launcher for Live Voice Chat
    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Open live voice (VoiceInteractionService gracefully handles mic or prompt fallback)
        viewModel.openLiveVoice()
    }

    val requestLiveVoice = {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (hasPermission) {
            viewModel.openLiveVoice()
        } else {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    // Back button handling
    BackHandler(enabled = isLiveVoiceActive || drawerState.isOpen || currentSubScreen != AppSubScreen.CHAT) {
        if (isLiveVoiceActive) {
            viewModel.closeLiveVoice()
        } else if (drawerState.isOpen) {
            coroutineScope.launch { drawerState.close() }
        } else if (currentSubScreen != AppSubScreen.CHAT) {
            currentSubScreen = AppSubScreen.CHAT
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            AppNavigationDrawer(
                conversations = conversations,
                currentConversationId = currentConversationId,
                memorySnapshot = memorySnapshot,
                onSelectConversation = { convId ->
                    viewModel.loadConversation(convId)
                    coroutineScope.launch { drawerState.close() }
                    currentSubScreen = AppSubScreen.CHAT
                },
                onNewChatClick = {
                    viewModel.createNewConversation()
                    coroutineScope.launch { drawerState.close() }
                    currentSubScreen = AppSubScreen.CHAT
                },
                onRenameConversation = { convId, newTitle ->
                    viewModel.renameConversation(convId, newTitle)
                },
                onDeleteConversation = { convId ->
                    viewModel.deleteConversation(convId)
                },
                onOpenVault = {
                    coroutineScope.launch { drawerState.close() }
                    currentSubScreen = AppSubScreen.VAULT
                },
                onOpenInspector = {
                    coroutineScope.launch { drawerState.close() }
                    currentSubScreen = AppSubScreen.INSPECTOR
                },
                onOpenSettings = {
                    coroutineScope.launch { drawerState.close() }
                    showSettingsSheet = true
                }
            )
        }
    ) {
        Scaffold(
            contentWindowInsets = WindowInsets.safeDrawing,
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when (currentSubScreen) {
                    AppSubScreen.CHAT -> {
                        MainChatScreen(
                            messages = chatMessages,
                            isBusy = isBusy,
                            generationState = generationState,
                            activeModel = activeModel,
                            inferenceMode = inferenceMode,
                            isSpeakingTts = isSpeakingTts,
                            activeSpeakingMessageId = activeUtteranceId,
                            onOpenDrawer = {
                                coroutineScope.launch { drawerState.open() }
                            },
                            onOpenModelSelector = {
                                showModelSelectorSheet = true
                            },
                            onNewChat = {
                                viewModel.createNewConversation()
                            },
                            onOpenLiveVoice = {
                                requestLiveVoice()
                            },
                            onSpeakMessage = { id, text ->
                                viewModel.speakChatMessage(id, text)
                            },
                            onSendMessage = { query ->
                                viewModel.sendUserMessage(query)
                            },
                            onStopGeneration = {
                                viewModel.stopGeneration()
                            },
                            onRegenerateResponse = {
                                viewModel.regenerateLastResponse()
                            },
                            onOpenAttachmentSheet = {
                                showAttachmentSheet = true
                            },
                            onPickDocumentPrompt = {
                                documentPickerLauncher.launch(
                                    arrayOf("application/pdf", "text/plain", "text/markdown", "application/msword")
                                )
                            },
                            onAnalyzeImagePrompt = {
                                showAttachmentSheet = true
                            },
                            onExplainConceptPrompt = {
                                viewModel.sendUserMessage("Explain how on-device language models work without internet connection.")
                            },
                            onSearchFilesPrompt = {
                                currentSubScreen = AppSubScreen.VAULT
                            }
                        )
                    }

                    AppSubScreen.VAULT -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "Document Vault & Vector Index",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { currentSubScreen = AppSubScreen.CHAT }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back to Chat"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            DocumentVaultScreen(
                                documents = savedDocuments,
                                ragPipeline = viewModel.ragPipeline,
                                onIngestSampleDoc = { doc ->
                                    viewModel.runScenarioB(sampleDoc = doc)
                                    currentSubScreen = AppSubScreen.CHAT
                                },
                                onClearAll = { viewModel.clearAllData() }
                            )
                        }
                    }

                    AppSubScreen.INSPECTOR -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            TopAppBar(
                                title = {
                                    Text(
                                        text = "RAM & Scenario Inspector",
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                },
                                navigationIcon = {
                                    IconButton(onClick = { currentSubScreen = AppSubScreen.CHAT }) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                            contentDescription = "Back to Chat"
                                        )
                                    }
                                },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            )
                            ScenarioInspectorScreen(
                                pipelineSteps = pipelineSteps,
                                transitionLogs = transitionLogs,
                                activeScenarioTitle = activeScenarioTitle,
                                isBusy = isBusy,
                                onRunScenarioA = {
                                    viewModel.runScenarioA()
                                    currentSubScreen = AppSubScreen.CHAT
                                },
                                onRunScenarioB = {
                                    viewModel.runScenarioB()
                                    currentSubScreen = AppSubScreen.CHAT
                                }
                            )
                        }
                    }
                }
            }

            // MODEL SELECTOR SHEET
            if (showModelSelectorSheet) {
                ModelSelectorSheet(
                    activeModel = activeModel,
                    inferenceMode = inferenceMode,
                    onSelectModel = { selected ->
                        viewModel.switchModel(selected)
                        Toast.makeText(context, "Switching to ${selected.name}...", Toast.LENGTH_SHORT).show()
                    },
                    onToggleInferenceMode = {
                        viewModel.toggleInferenceMode()
                        val modeLabel = if (inferenceMode == InferenceMode.OFFLINE_EDGE_AI) "Gemini Live" else "100% Offline"
                        Toast.makeText(context, "AI Mode: $modeLabel", Toast.LENGTH_SHORT).show()
                    },
                    onUnloadActiveModel = {
                        viewModel.unloadModelManually()
                        Toast.makeText(context, "Active model unloaded to free RAM", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showModelSelectorSheet = false }
                )
            }

            // ATTACHMENT SHEET
            if (showAttachmentSheet) {
                AttachmentSheet(
                    onPickDocument = {
                        documentPickerLauncher.launch(
                            arrayOf("application/pdf", "text/plain", "text/markdown", "application/msword")
                        )
                    },
                    onLaunchCamera = {
                        cameraLauncher.launch(null)
                    },
                    onPickPhoto = {
                        photoPickerLauncher.launch(
                            androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onOpenVault = {
                        currentSubScreen = AppSubScreen.VAULT
                    },
                    onOpenInspector = {
                        currentSubScreen = AppSubScreen.INSPECTOR
                    },
                    onStartVoiceChat = {
                        requestLiveVoice()
                    },
                    onDismiss = { showAttachmentSheet = false }
                )
            }

            // SETTINGS SHEET
            if (showSettingsSheet) {
                SettingsSheet(
                    themeMode = themeMode,
                    onThemeModeChange = { mode ->
                        viewModel.setThemeMode(mode)
                    },
                    inferenceMode = inferenceMode,
                    onToggleInferenceMode = {
                        viewModel.toggleInferenceMode()
                    },
                    memorySnapshot = memorySnapshot,
                    onForceGC = {
                        viewModel.unloadModelManually()
                        Toast.makeText(context, "Model unloaded & GC requested", Toast.LENGTH_SHORT).show()
                    },
                    onClearConversations = {
                        viewModel.clearAllConversations()
                        Toast.makeText(context, "All conversations cleared", Toast.LENGTH_SHORT).show()
                    },
                    onClearVault = {
                        viewModel.clearAllData()
                        Toast.makeText(context, "Vault and local embeddings cleared", Toast.LENGTH_SHORT).show()
                    },
                    onDismiss = { showSettingsSheet = false }
                )
            }

            // LIVE VOICE CHAT SHEET
            if (isLiveVoiceActive) {
                LiveVoiceChatSheet(
                    voiceState = voiceState,
                    userTranscript = userTranscript,
                    aiSpokenText = aiSpokenText,
                    audioLevels = audioLevels,
                    isMuted = viewModel.isLiveVoiceMuted(),
                    isSpeakingTts = isSpeakingTts,
                    activeModel = activeModel,
                    inferenceMode = inferenceMode,
                    onClose = { viewModel.closeLiveVoice() },
                    onToggleMute = { viewModel.toggleLiveVoiceMute() },
                    onStopSpeaking = { viewModel.stopSpeakingTts() },
                    onStartListening = { viewModel.startListeningLoop() },
                    onSetSpeechRate = { rate -> viewModel.setVoiceSpeechRate(rate) },
                    onSimulatePrompt = { prompt -> viewModel.simulateSpokenPrompt(prompt) }
                )
            }
        }
    }
}
