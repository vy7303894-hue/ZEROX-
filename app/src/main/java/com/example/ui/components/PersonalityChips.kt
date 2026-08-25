package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyberCardSurface
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.TextPrimary

data class SparkChip(
    val title: String,
    val prompt: String
)

val DEFAULT_SPARKS = listOf(
    SparkChip("Roast me 😏", "Give me a playful, sassy roast about my day!"),
    SparkChip("Tease me 💅", "Say something confident and teasing like my best friend."),
    SparkChip("Turn on flashlight 💡", "Turn on the flashlight for me please!"),
    SparkChip("Play music 🎶", "Play some vibe music on YouTube!"),
    SparkChip("Tell me a secret 🤫", "Tell me a juicy secret or witty life advice."),
    SparkChip("Device info 🔋", "What is my battery level and device status?"),
    SparkChip("Open Reddit 🌐", "Open reddit.com in the browser."),
    SparkChip("Set 5m timer ⏳", "Set a 5 minute timer for me.")
)

@Composable
fun PersonalityChips(
    onChipClicked: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DEFAULT_SPARKS.forEachIndexed { index, chip ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(CyberCardSurface.copy(alpha = 0.85f))
                    .border(1.dp, BorderSubtle, RoundedCornerShape(18.dp))
                    .clickable { onChipClicked(chip.prompt) }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .testTag("spark_chip_$index"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = chip.title,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
