package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ModelType
import com.example.service.InferenceMode
import com.example.service.VoiceState
import com.example.ui.theme.AiAccentGreen
import com.example.ui.theme.AiAccentGreenLight
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.RoseDanger

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveVoiceChatSheet(
    voiceState: VoiceState,
    userTranscript: String,
    aiSpokenText: String,
    audioLevels: List<Float>,
    isMuted: Boolean,
    isSpeakingTts: Boolean,
    activeModel: ModelType?,
    inferenceMode: InferenceMode,
    onClose: () -> Unit,
    onToggleMute: () -> Unit,
    onStopSpeaking: () -> Unit,
    onStartListening: () -> Unit,
    onSetSpeechRate: (Float) -> Unit,
    onSimulatePrompt: (String) -> Unit
) {
    BackHandler(onBack = onClose)

    var currentSpeed by remember { mutableFloatStateOf(1.0f) }

    val infiniteTransition = rememberInfiniteTransition(label = "voice_orb_anim")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val averageAudio = if (audioLevels.isNotEmpty()) audioLevels.average().toFloat() else 0.1f
    val dynamicOrbScale = pulseScale + (averageAudio * 0.25f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .statusBarsPadding()
            .navigationBarsPadding()
            .testTag("live_voice_chat_sheet")
    ) {
        // Ambient background glow
        Box(
            modifier = Modifier
                .size(320.dp)
                .align(Alignment.Center)
                .blur(80.dp)
                .background(
                    when (voiceState) {
                        VoiceState.SPEAKING -> AiAccentGreen.copy(alpha = glowAlpha * 0.45f)
                        VoiceState.LISTENING -> Color(0xFF38BDF8).copy(alpha = glowAlpha * 0.45f)
                        VoiceState.THINKING -> Color(0xFFA855F7).copy(alpha = glowAlpha * 0.45f)
                        VoiceState.MUTED -> AmberWarning.copy(alpha = 0.25f)
                        else -> AiAccentGreen.copy(alpha = 0.15f)
                    },
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TOP HEADER
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Close button
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .testTag("live_voice_close_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Live Voice",
                        tint = Color.White
                    )
                }

                // Model Tag Pill
                Surface(
                    color = Color.White.copy(alpha = 0.08f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = when (voiceState) {
                                VoiceState.SPEAKING -> AiAccentGreen
                                VoiceState.LISTENING -> Color(0xFF38BDF8)
                                VoiceState.THINKING -> Color(0xFFA855F7)
                                VoiceState.MUTED -> AmberWarning
                                else -> Color.Gray
                            },
                            modifier = Modifier.size(7.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (inferenceMode == InferenceMode.GEMINI_LIVE) "Gemini Live Voice" else "Qwen 2.5 • Offline Voice",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Speed Selector Button
                IconButton(
                    onClick = {
                        val nextSpeed = when (currentSpeed) {
                            1.0f -> 1.25f
                            1.25f -> 1.5f
                            else -> 1.0f
                        }
                        currentSpeed = nextSpeed
                        onSetSpeechRate(nextSpeed)
                    },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.08f))
                        .testTag("live_voice_speed_button")
                ) {
                    Text(
                        text = "${currentSpeed}x",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // STATUS LABEL
            Text(
                text = when (voiceState) {
                    VoiceState.LISTENING -> "Listening to you..."
                    VoiceState.THINKING -> "Thinking..."
                    VoiceState.SPEAKING -> "Speaking..."
                    VoiceState.MUTED -> "Microphone Muted"
                    VoiceState.ERROR -> "Voice Error"
                    VoiceState.IDLE -> if (isMuted) "Microphone Muted" else "Tap microphone to speak"
                },
                fontSize = 19.sp,
                fontWeight = FontWeight.SemiBold,
                color = when (voiceState) {
                    VoiceState.SPEAKING -> AiAccentGreenLight
                    VoiceState.LISTENING -> Color(0xFF7DD3FC)
                    VoiceState.THINKING -> Color(0xFFD8B4FE)
                    VoiceState.MUTED -> AmberWarning
                    else -> Color.White.copy(alpha = 0.8f)
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            // CENTER PULSING VOICE ORB & DYNAMIC AUDIO VISUALIZER
            Box(
                modifier = Modifier
                    .size(230.dp)
                    .clip(CircleShape)
                    .clickable {
                        if (voiceState == VoiceState.SPEAKING) {
                            onStopSpeaking()
                        } else if (!isMuted) {
                            onStartListening()
                        }
                    }
                    .testTag("live_voice_orb"),
                contentAlignment = Alignment.Center
            ) {
                // Interactive Waveform / Pulsing Ring Canvas
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val baseRadius = (size.minDimension / 2.7f) * dynamicOrbScale

                    // Outer halo ring
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                when (voiceState) {
                                    VoiceState.SPEAKING -> AiAccentGreen.copy(alpha = 0.65f)
                                    VoiceState.LISTENING -> Color(0xFF38BDF8).copy(alpha = 0.65f)
                                    VoiceState.THINKING -> Color(0xFFA855F7).copy(alpha = 0.65f)
                                    VoiceState.MUTED -> AmberWarning.copy(alpha = 0.4f)
                                    else -> Color.DarkGray.copy(alpha = 0.4f)
                                },
                                Color.Transparent
                            ),
                            center = center,
                            radius = baseRadius * 1.35f
                        ),
                        radius = baseRadius * 1.35f,
                        center = center
                    )

                    // Core Solid Glowing Orb
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                when (voiceState) {
                                    VoiceState.SPEAKING -> Color(0xFF4ADE80)
                                    VoiceState.LISTENING -> Color(0xFF38BDF8)
                                    VoiceState.THINKING -> Color(0xFFC084FC)
                                    VoiceState.MUTED -> AmberWarning
                                    else -> Color(0xFF334155)
                                },
                                when (voiceState) {
                                    VoiceState.SPEAKING -> Color(0xFF15803D)
                                    VoiceState.LISTENING -> Color(0xFF0369A1)
                                    VoiceState.THINKING -> Color(0xFF7E22CE)
                                    VoiceState.MUTED -> Color(0xFFB45309)
                                    else -> Color(0xFF1E293B)
                                }
                            ),
                            center = center,
                            radius = baseRadius
                        ),
                        radius = baseRadius,
                        center = center
                    )

                    // Inner Dynamic Audio EQ bars in center of orb
                    val barCount = audioLevels.size.coerceAtLeast(8)
                    val totalBarWidth = size.width * 0.48f
                    val barWidth = totalBarWidth / (barCount * 1.6f)
                    val startX = center.x - (totalBarWidth / 2f)

                    for (i in 0 until barCount) {
                        val level = audioLevels.getOrElse(i) { 0.15f }
                        val barHeight = (size.height * 0.35f * level).coerceIn(8f, size.height * 0.42f)
                        val x = startX + i * (barWidth * 1.6f)
                        val y = center.y - (barHeight / 2f)

                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.95f),
                            topLeft = Offset(x, y),
                            size = Size(barWidth, barHeight),
                            cornerRadius = CornerRadius(4f, 4f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // REAL-TIME CONVERSATION TRANSCRIPT CARD
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.White.copy(alpha = 0.05f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (userTranscript.isBlank() && aiSpokenText.isBlank()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Start speaking or tap one of the suggested prompts below to converse with the AI in real time.",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                                lineHeight = 18.sp,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    if (userTranscript.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "You: ",
                                color = Color(0xFF7DD3FC),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = userTranscript,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }

                    if (aiSpokenText.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.Top,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "AI: ",
                                color = AiAccentGreenLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = aiSpokenText,
                                color = Color.White.copy(alpha = 0.95f),
                                fontSize = 13.sp,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // QUICK VOICE PROMPT SUGGESTIONS (Immediate tap to try)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val prompts = listOf(
                    "Hello! Who are you?",
                    "How does offline AI work?",
                    "Tell me a short fun fact"
                )
                prompts.forEach { prompt ->
                    Surface(
                        color = Color.White.copy(alpha = 0.08f),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .clickable { onSimulatePrompt(prompt) }
                            .testTag("quick_prompt_${prompt.take(5)}")
                    ) {
                        Text(
                            text = prompt,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // BOTTOM CONTROL BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Switch to Text Keyboard
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.1f))
                        .testTag("live_voice_keyboard_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Keyboard,
                        contentDescription = "Switch to Text",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Mute / Unmute Microphone
                FilledIconButton(
                    onClick = onToggleMute,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isMuted) AmberWarning else Color.White.copy(alpha = 0.15f),
                        contentColor = if (isMuted) Color.Black else Color.White
                    ),
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .testTag("live_voice_mute_button")
                ) {
                    Icon(
                        imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = if (isMuted) "Unmute Microphone" else "Mute Microphone",
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Stop / Interrupt AI Speech (if speaking) or Listen trigger
                if (isSpeakingTts) {
                    FilledIconButton(
                        onClick = onStopSpeaking,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = RoseDanger,
                            contentColor = Color.White
                        ),
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .testTag("live_voice_stop_speaking_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = "Interrupt AI Speech",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onStartListening,
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                            .testTag("live_voice_listen_trigger_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Hearing,
                            contentDescription = "Tap to Listen",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                // End Session Button
                FilledIconButton(
                    onClick = onClose,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = RoseDanger.copy(alpha = 0.85f),
                        contentColor = Color.White
                    ),
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .testTag("live_voice_end_session_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "End Live Voice",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
