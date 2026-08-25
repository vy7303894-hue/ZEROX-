package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.model.AssistantState
import com.example.ui.components.CyberReactiveOrb
import com.example.ui.components.HistorySheet
import com.example.ui.components.LiveDialogueHUD
import com.example.ui.components.PersonalityChips
import com.example.ui.components.SettingsSheet
import com.example.ui.components.SoundWaveformVisualizer
import com.example.ui.components.ToolActionBanner
import com.example.ui.theme.BorderGlow
import com.example.ui.theme.BorderSubtle
import com.example.ui.theme.CyberCardSurface
import com.example.ui.theme.CyberDarkSurface
import com.example.ui.theme.CyberObsidian
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonMagentaLight
import com.example.ui.theme.NeonMint
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.viewmodel.ZoyaViewModel

@Composable
fun ZoyaScreen(
    viewModel: ZoyaViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            viewModel.wakeAndConnect()
        }
    }

    var showTextInputDialog by remember { mutableStateOf(false) }
    var textInputPrompt by remember { mutableStateOf("") }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(CyberObsidian),
        containerColor = CyberObsidian,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Atmospheric Background Elements
            Canvas(modifier = Modifier.fillMaxSize()) {
                // Top-right magenta nebula
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonMagenta.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.85f, size.height * 0.15f),
                        radius = size.width * 0.65f
                    ),
                    radius = size.width * 0.65f,
                    center = Offset(size.width * 0.85f, size.height * 0.15f)
                )

                // Bottom-left cyan nebula
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(NeonCyan.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.15f, size.height * 0.85f),
                        radius = size.width * 0.60f
                    ),
                    radius = size.width * 0.60f,
                    center = Offset(size.width * 0.15f, size.height * 0.85f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top App Bar
                TopActionBar(
                    state = uiState.assistantState,
                    latencyMs = uiState.sessionLatencyMs,
                    onOpenHistory = { viewModel.toggleHistoryDialog(true) },
                    onOpenSettings = { viewModel.toggleSettingsDialog(true) },
                    modifier = Modifier.fillMaxWidth()
                )

                // Central Interactive Arena
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    // Tool notification banner
                    ToolActionBanner(
                        toolInfo = uiState.lastExecutedTool,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    // Hero Glowing Orb
                    CyberReactiveOrb(
                        state = uiState.assistantState,
                        amplitude = uiState.amplitude,
                        onClick = {
                            if (!hasAudioPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.onPowerOrOrbClicked()
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Sound Waveform Equalizer
                    SoundWaveformVisualizer(
                        state = uiState.assistantState,
                        amplitude = uiState.amplitude,
                        modifier = Modifier.padding(horizontal = 36.dp)
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    // Live Speech HUD / Dialogue Capsule
                    LiveDialogueHUD(
                        state = uiState.assistantState,
                        statusMessage = uiState.statusMessage,
                        subtitleText = uiState.subtitleText
                    )
                }

                // Bottom Controls & Sparks
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Personality Spark Chips
                    PersonalityChips(
                        onChipClicked = { prompt ->
                            if (!hasAudioPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.submitPrompt(prompt)
                            }
                        }
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Main Control Bar
                    ControlActionBar(
                        state = uiState.assistantState,
                        isMuted = uiState.isMuted,
                        onPowerClicked = {
                            if (!hasAudioPermission) {
                                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            } else {
                                viewModel.onPowerOrOrbClicked()
                            }
                        },
                        onMuteClicked = { viewModel.toggleMute() },
                        onTextInputClicked = { showTextInputDialog = true },
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }
    }

    // Settings Modal Sheet
    if (uiState.isSettingsOpen) {
        SettingsSheet(
            selectedVoice = uiState.selectedVoice,
            sassLevel = uiState.sassLevel,
            isContinuousListening = uiState.isContinuousListening,
            isFlashlightOn = uiState.isFlashlightOn,
            onVoiceSelected = { viewModel.setVoice(it) },
            onSassLevelChanged = { viewModel.setSassLevel(it) },
            onContinuousListeningToggled = { viewModel.toggleContinuousListening() },
            onDismiss = { viewModel.toggleSettingsDialog(false) }
        )
    }

    // History Transcripts Sheet
    if (uiState.isHistoryOpen) {
        HistorySheet(
            historyTurns = uiState.historyTurns,
            onClearHistory = { viewModel.clearHistory() },
            onDismiss = { viewModel.toggleHistoryDialog(false) }
        )
    }

    // Manual Text Prompt Input Dialog
    if (showTextInputDialog) {
        AlertDialog(
            onDismissRequest = { showTextInputDialog = false },
            containerColor = CyberDarkSurface,
            title = {
                Text(
                    text = "Talk to Zoya",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Type any prompt or command for Zoya:",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = textInputPrompt,
                        onValueChange = { textInputPrompt = it },
                        placeholder = { Text("e.g. Roast my outfit today...", color = TextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            focusedBorderColor = NeonMagenta,
                            unfocusedBorderColor = BorderSubtle
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_prompt_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (textInputPrompt.isNotBlank()) {
                            viewModel.submitPrompt(textInputPrompt.trim())
                            textInputPrompt = ""
                            showTextInputDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta),
                    modifier = Modifier.testTag("send_custom_prompt_button")
                ) {
                    Text("Send", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showTextInputDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
private fun TopActionBar(
    state: AssistantState,
    latencyMs: Long,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .padding(horizontal = 20.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Identity Brand
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.testTag("app_brand_header")
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(listOf(NeonMagenta, ElectricViolet))
                    )
                    .border(1.dp, NeonCyan.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Z",
                    color = Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = 18.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "ZOYA",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE",
                        color = NeonMagenta,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(NeonMagenta.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = if (latencyMs > 0) "${latencyMs}ms latency" else "Voice-to-Voice AI",
                    color = if (latencyMs > 0) NeonMint else TextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Action Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            IconButton(
                onClick = onOpenHistory,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CyberCardSurface)
                    .border(1.dp, BorderSubtle, CircleShape)
                    .testTag("history_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Forum,
                    contentDescription = "Transcripts",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(CyberCardSurface)
                    .border(1.dp, BorderSubtle, CircleShape)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = TextPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ControlActionBar(
    state: AssistantState,
    isMuted: Boolean,
    onPowerClicked: () -> Unit,
    onMuteClicked: () -> Unit,
    onTextInputClicked: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(32.dp))
            .background(CyberCardSurface.copy(alpha = 0.85f))
            .border(1.dp, BorderGlow.copy(alpha = 0.4f), RoundedCornerShape(32.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mute / Unmute Button
        IconButton(
            onClick = onMuteClicked,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isMuted) Color(0xFF381010) else CyberObsidian)
                .border(1.dp, if (isMuted) Color(0xFFFF5252) else BorderSubtle, CircleShape)
                .testTag("mute_button")
        ) {
            Icon(
                imageVector = if (isMuted) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isMuted) "Unmute" else "Mute",
                tint = if (isMuted) Color(0xFFFF5252) else TextPrimary,
                modifier = Modifier.size(22.dp)
            )
        }

        // Center Action / Power / Interrupt Button
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = when (state) {
                            AssistantState.SPEAKING -> listOf(NeonMagenta, Color(0xFF8B0045))
                            AssistantState.LISTENING -> listOf(NeonCyan, Color(0xFF006D77))
                            AssistantState.THINKING, AssistantState.CONNECTING -> listOf(ElectricViolet, Color(0xFF4A148C))
                            AssistantState.DISCONNECTED -> listOf(Color(0xFF353055), Color(0xFF1B1730))
                        }
                    )
                )
                .border(
                    2.dp,
                    when (state) {
                        AssistantState.SPEAKING -> NeonMagentaLight
                        AssistantState.LISTENING -> NeonCyan
                        AssistantState.THINKING -> ElectricViolet
                        AssistantState.CONNECTING -> NeonCyan
                        AssistantState.DISCONNECTED -> BorderSubtle
                    },
                    CircleShape
                )
                .clickable { onPowerClicked() }
                .testTag("main_action_button"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = when (state) {
                    AssistantState.SPEAKING -> Icons.Default.Stop
                    AssistantState.LISTENING -> Icons.Default.Mic
                    AssistantState.THINKING, AssistantState.CONNECTING -> Icons.Default.PowerSettingsNew
                    AssistantState.DISCONNECTED -> Icons.Default.PowerSettingsNew
                },
                contentDescription = "Toggle Zoya",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Text / Custom Prompt Input Button
        IconButton(
            onClick = onTextInputClicked,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(CyberObsidian)
                .border(1.dp, BorderSubtle, CircleShape)
                .testTag("text_input_button")
        ) {
            Icon(
                imageVector = Icons.Default.Bolt,
                contentDescription = "Quick Command",
                tint = NeonMint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}
