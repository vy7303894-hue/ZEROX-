package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.AssistantState
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyberCardSurface
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

@Composable
fun LiveDialogueHUD(
    state: AssistantState,
    statusMessage: String,
    subtitleText: String,
    modifier: Modifier = Modifier
) {
    val borderColor = when (state) {
        AssistantState.SPEAKING -> NeonMagenta.copy(alpha = 0.5f)
        AssistantState.LISTENING -> NeonCyan.copy(alpha = 0.5f)
        AssistantState.THINKING -> ElectricViolet.copy(alpha = 0.5f)
        AssistantState.CONNECTING -> NeonCyan.copy(alpha = 0.3f)
        AssistantState.DISCONNECTED -> BorderSubtle
    }

    val glowBrush = Brush.verticalGradient(
        colors = listOf(
            CyberCardSurface.copy(alpha = 0.92f),
            Color(0xFF100D24).copy(alpha = 0.96f)
        )
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Status Badge Pill
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(Color(0xFF1E1A38).copy(alpha = 0.8f))
                .border(1.dp, borderColor, CircleShape)
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        when (state) {
                            AssistantState.SPEAKING -> NeonMagenta
                            AssistantState.LISTENING -> NeonCyan
                            AssistantState.THINKING -> ElectricViolet
                            AssistantState.CONNECTING -> NeonCyan
                            AssistantState.DISCONNECTED -> Color(0xFF6B6E8C)
                        }
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusMessage.uppercase(),
                color = when (state) {
                    AssistantState.SPEAKING -> NeonMagenta
                    AssistantState.LISTENING -> NeonCyan
                    AssistantState.THINKING -> ElectricViolet
                    AssistantState.CONNECTING -> NeonCyan
                    AssistantState.DISCONNECTED -> TextSecondary
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Subtitle Capsule
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(glowBrush)
                .border(1.dp, borderColor, RoundedCornerShape(22.dp))
                .padding(horizontal = 20.dp, vertical = 16.dp)
                .testTag("dialogue_hud_card"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = subtitleText,
                color = TextPrimary,
                fontSize = 17.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
