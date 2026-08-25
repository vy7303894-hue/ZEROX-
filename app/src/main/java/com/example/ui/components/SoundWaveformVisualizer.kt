package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.model.AssistantState
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonMint
import kotlin.math.abs
import kotlin.math.sin

@Composable
fun SoundWaveformVisualizer(
    state: AssistantState,
    amplitude: Float,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp,
    barCount: Int = 24
) {
    val infiniteTransition = rememberInfiniteTransition(label = "WaveformTransition")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * Math.PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "WavePhase"
    )

    val isActive = state == AssistantState.LISTENING || state == AssistantState.SPEAKING

    val primaryColor = when (state) {
        AssistantState.SPEAKING -> NeonMagenta
        AssistantState.LISTENING -> NeonCyan
        AssistantState.THINKING -> ElectricViolet
        AssistantState.CONNECTING -> NeonCyan
        AssistantState.DISCONNECTED -> Color(0xFF322E4D)
    }

    val secondaryColor = when (state) {
        AssistantState.SPEAKING -> NeonCyan
        AssistantState.LISTENING -> NeonMint
        AssistantState.THINKING -> NeonMagenta
        AssistantState.CONNECTING -> ElectricViolet
        AssistantState.DISCONNECTED -> Color(0xFF1E1A33)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        val totalWidth = size.width
        val barWidth = (totalWidth / (barCount * 1.8f)).coerceIn(3f, 10f)
        val gap = (totalWidth - (barCount * barWidth)) / (barCount - 1).coerceAtLeast(1)
        val centerY = size.height / 2f
        val maxBarHeight = size.height * 0.9f
        val minBarHeight = 6f

        val gradientBrush = Brush.verticalGradient(
            colors = listOf(primaryColor, secondaryColor),
            startY = 0f,
            endY = size.height
        )

        for (i in 0 until barCount) {
            val normalizedIdx = (i.toFloat() / (barCount - 1)) * 2f - 1f // -1.0 to 1.0 (center is 0)
            val centerWeight = 1f - abs(normalizedIdx) * 0.45f // taller in middle

            val dynamicFactor = if (isActive) {
                val wave1 = abs(sin(phase + i * 0.45f))
                val wave2 = abs(sin(phase * 1.5f + i * 0.25f))
                val combined = (wave1 * 0.6f + wave2 * 0.4f)
                (combined * (0.3f + amplitude * 1.8f) * centerWeight).coerceIn(0.1f, 1.0f)
            } else if (state == AssistantState.THINKING) {
                abs(sin(phase * 2f + i * 0.35f)) * 0.5f * centerWeight
            } else {
                0.08f * centerWeight
            }

            val curHeight = (minBarHeight + (maxBarHeight - minBarHeight) * dynamicFactor)
            val x = i * (barWidth + gap)
            val y = centerY - (curHeight / 2f)

            drawRoundRect(
                brush = gradientBrush,
                topLeft = Offset(x, y),
                size = Size(barWidth, curHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
