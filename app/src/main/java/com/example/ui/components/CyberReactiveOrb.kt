package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AssistantState
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonMagentaLight
import com.example.ui.theme.NeonMint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun CyberReactiveOrb(
    state: AssistantState,
    amplitude: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 260.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "OrbTransitions")

    // Rotation animation
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    AssistantState.CONNECTING -> 2000
                    AssistantState.THINKING -> 1500
                    AssistantState.SPEAKING -> 4000
                    AssistantState.LISTENING -> 6000
                    AssistantState.DISCONNECTED -> 16000
                },
                easing = LinearEasing
            ),
            repeatMode = RepeatMode.Restart
        ),
        label = "OrbRotation"
    )

    // Breathing pulse
    val breathingPulse by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BreathingPulse"
    )

    // Dynamic scale driven by audio amplitude and state
    val animatedAmp by animateFloatAsState(
        targetValue = amplitude.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = 800f),
        label = "AmplitudeScale"
    )

    val baseScale = when (state) {
        AssistantState.DISCONNECTED -> 0.88f
        AssistantState.CONNECTING -> 1.0f * breathingPulse
        AssistantState.LISTENING -> 1.02f + (animatedAmp * 0.35f)
        AssistantState.THINKING -> 1.05f * breathingPulse
        AssistantState.SPEAKING -> 1.04f + (animatedAmp * 0.40f)
    }

    val primaryColor = when (state) {
        AssistantState.DISCONNECTED -> Color(0xFF4A4468)
        AssistantState.CONNECTING -> NeonCyan
        AssistantState.LISTENING -> NeonCyan
        AssistantState.THINKING -> ElectricViolet
        AssistantState.SPEAKING -> NeonMagenta
    }

    val secondaryColor = when (state) {
        AssistantState.DISCONNECTED -> Color(0xFF231E3D)
        AssistantState.CONNECTING -> ElectricViolet
        AssistantState.LISTENING -> NeonMint
        AssistantState.THINKING -> NeonMagenta
        AssistantState.SPEAKING -> NeonCyan
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(size)
            .scale(baseScale)
            .testTag("cyber_reactive_orb")
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Multi-layered Canvas rendering
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val radius = this.size.minDimension / 2.5f

            // 1. Ambient Outer Glow
            val glowBrush = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = if (state == AssistantState.DISCONNECTED) 0.15f else 0.45f),
                    secondaryColor.copy(alpha = if (state == AssistantState.DISCONNECTED) 0.05f else 0.20f),
                    Color.Transparent
                ),
                center = center,
                radius = radius * 1.55f
            )
            drawCircle(brush = glowBrush, radius = radius * 1.55f, center = center)

            // 2. Holographic Cyber Orbit Rings
            rotate(rotation, pivot = center) {
                // Outer dotted ring
                drawCircle(
                    color = primaryColor.copy(alpha = 0.45f),
                    radius = radius * 1.25f,
                    center = center,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round)
                )

                // Particle ticks on the ring
                val tickCount = 12
                for (i in 0 until tickCount) {
                    val angle = (i * (360f / tickCount)) * (PI / 180f)
                    val tickRadius = radius * 1.25f
                    val x = center.x + (tickRadius * cos(angle)).toFloat()
                    val y = center.y + (tickRadius * sin(angle)).toFloat()
                    drawCircle(
                        color = if (i % 2 == 0) secondaryColor else primaryColor,
                        radius = if (i % 3 == 0) 4f else 2.5f,
                        center = Offset(x, y)
                    )
                }
            }

            // 3. Counter-rotating inner cyber ring
            rotate(-rotation * 1.3f, pivot = center) {
                val wavePath = Path()
                val steps = 48
                for (i in 0..steps) {
                    val angle = (i * (360f / steps)) * (PI / 180f)
                    val waveAmp = if (state == AssistantState.SPEAKING || state == AssistantState.LISTENING) {
                        (sin(angle * 4.0 + rotation * 0.1).toFloat() * (12f * animatedAmp.coerceAtLeast(0.1f)))
                    } else {
                        0f
                    }
                    val currentR = radius * 1.08f + waveAmp
                    val px = center.x + (currentR * cos(angle)).toFloat()
                    val py = center.y + (currentR * sin(angle)).toFloat()
                    if (i == 0) wavePath.moveTo(px, py) else wavePath.lineTo(px, py)
                }
                wavePath.close()

                drawPath(
                    path = wavePath,
                    color = secondaryColor.copy(alpha = 0.7f),
                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                )
            }

            // 4. Central Solid Core with Vibrant Cyber Gradient
            val coreGradient = Brush.radialGradient(
                colors = listOf(
                    primaryColor.copy(alpha = 0.95f),
                    secondaryColor.copy(alpha = 0.85f),
                    Color(0xFF0F0B24)
                ),
                center = center - Offset(radius * 0.2f, radius * 0.2f),
                radius = radius * 0.95f
            )
            drawCircle(brush = coreGradient, radius = radius * 0.85f, center = center)

            // 5. Gloss Highlight Arc
            drawArc(
                color = Color.White.copy(alpha = 0.35f),
                startAngle = 200f,
                sweepAngle = 100f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.72f, center.y - radius * 0.72f),
                size = androidx.compose.ui.geometry.Size(radius * 1.44f, radius * 1.44f),
                style = Stroke(width = 3.5f, cap = StrokeCap.Round)
            )
        }

        // Center Icon Overlay
        Box(
            modifier = Modifier
                .size(size * 0.35f)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.3f)),
            contentAlignment = Alignment.Center
        ) {
            when (state) {
                AssistantState.DISCONNECTED -> {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Wake Zoya",
                        tint = Color(0xFFA09CC0),
                        modifier = Modifier.size(36.dp)
                    )
                }
                AssistantState.LISTENING -> {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Listening",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                AssistantState.SPEAKING -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Speaking",
                        tint = Color.White,
                        modifier = Modifier.size(36.dp)
                    )
                }
                AssistantState.THINKING, AssistantState.CONNECTING -> {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = "Thinking",
                        tint = NeonMagentaLight,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }
    }
}
