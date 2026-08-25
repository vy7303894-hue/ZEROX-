package com.example.model

enum class AssistantState {
    DISCONNECTED, // Sleeping / Off
    CONNECTING,   // Establishing live session
    LISTENING,    // Mic active, capturing audio
    THINKING,     // Gemini Live processing
    SPEAKING      // Voice playing back via AudioTrack
}

data class TurnItem(
    val id: String,
    val sender: String, // "You" or "Zoya"
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val toolTag: String? = null
)

data class ZoyaUiState(
    val assistantState: AssistantState = AssistantState.DISCONNECTED,
    val amplitude: Float = 0f,
    val subtitleText: String = "Tap the glowing orb to wake Zoya",
    val statusMessage: String = "Standby",
    val lastExecutedTool: String? = null,
    val sessionLatencyMs: Long = 0L,
    val isFlashlightOn: Boolean = false,
    val selectedVoice: String = "Aoede",
    val sassLevel: Int = 2,
    val isContinuousListening: Boolean = true,
    val isMuted: Boolean = false,
    val historyTurns: List<TurnItem> = emptyList(),
    val errorMessage: String? = null,
    val isSettingsOpen: Boolean = false,
    val isHistoryOpen: Boolean = false
)
