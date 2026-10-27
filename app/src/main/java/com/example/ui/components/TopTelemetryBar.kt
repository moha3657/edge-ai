package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.ModelType
import com.example.service.DeviceMemorySnapshot
import com.example.ui.theme.AmberWarning
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.EmeraldSafe
import com.example.ui.theme.RoseDanger
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceElevated

@Composable
fun TopTelemetryBar(
    memory: DeviceMemorySnapshot,
    activeModel: ModelType?,
    contextTokensUsed: Int,
    onUnloadModel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val usedRatio = if (memory.totalRamMB > 0) {
        (memory.usedRamMB.toFloat() / memory.totalRamMB.toFloat()).coerceIn(0f, 1f)
    } else 0.5f

    val animProgress by animateFloatAsState(
        targetValue = usedRatio,
        animationSpec = tween(500),
        label = "ram_progress"
    )

    val ramHealthColor by animateColorAsState(
        targetValue = when {
            usedRatio > 0.85f -> RoseDanger
            usedRatio > 0.70f -> AmberWarning
            else -> EmeraldSafe
        },
        label = "ram_color"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .border(0.7.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .testTag("telemetry_bar"),
        color = SurfaceCard,
        shape = RoundedCornerShape(14.dp),
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 9.dp)
        ) {
            // Row 1: Active Model Pill & RAM Summary Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Active Model Pill with pulsing status dot
                Surface(
                    color = if (activeModel != null) SurfaceElevated else SurfaceElevated.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.border(0.5.dp, if (activeModel != null) CyanAccent.copy(alpha = 0.4f) else Color.Transparent, RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(if (activeModel != null) EmeraldGlow else MaterialTheme.colorScheme.outline)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = activeModel?.displayName ?: "No Model in RAM (Idle)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (activeModel != null) ElectricCyan else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        if (activeModel != null) {
                            Spacer(modifier = Modifier.width(5.dp))
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .clickable(onClick = onUnloadModel),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Unload Model",
                                    tint = CyanAccent,
                                    modifier = Modifier.size(11.dp)
                                )
                            }
                        }
                    }
                }

                // Telemetry Readout
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${memory.availRamMB}MB Free",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = ramHealthColor
                    )
                    Text(
                        text = " • 4GB Base",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(7.dp))

            // Row 2: Dual Sleek Micro-Gauges (RAM on Left, KV Context on Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // RAM Bar
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "RAM ${(usedRatio * 100).toInt()}%",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "${memory.usedRamMB}MB Used",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = ramHealthColor
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { animProgress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = ramHealthColor,
                        trackColor = SurfaceElevated
                    )
                }

                // KV Context Bar (Strict 1024 ceiling)
                val contextRatio = (contextTokensUsed.toFloat() / 1024f).coerceIn(0f, 1f)
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "KV Context",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                        Text(
                            text = "$contextTokensUsed/1024",
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (contextTokensUsed > 800) AmberWarning else CyanAccent
                        )
                    }
                    Spacer(modifier = Modifier.height(3.dp))
                    LinearProgressIndicator(
                        progress = { contextRatio },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = if (contextTokensUsed > 800) AmberWarning else CyanAccent,
                        trackColor = SurfaceElevated
                    )
                }
            }
        }
    }
}
