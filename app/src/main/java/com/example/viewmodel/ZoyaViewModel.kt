package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.AudioPlayer
import com.example.audio.AudioRecorder
import com.example.audio.SoundEffects
import com.example.model.AssistantState
import com.example.model.TurnItem
import com.example.model.ZoyaUiState
import com.example.service.GeminiLiveResult
import com.example.service.GeminiLiveService
import com.example.service.ZoyaPersona
import com.example.tools.DeviceTools
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class ZoyaViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "ZoyaViewModel"
    }

    private val deviceTools = DeviceTools(application)
    private val geminiService = GeminiLiveService(deviceTools)
    private val audioRecorder = AudioRecorder()
    private val audioPlayer = AudioPlayer()

    private val _uiState = MutableStateFlow(ZoyaUiState())
    val uiState: StateFlow<ZoyaUiState> = _uiState.asStateFlow()

    private var amplitudeJob: Job? = null
    private var activeRequestJob: Job? = null

    init {
        setupAudioVisualizerPipes()
    }

    private fun setupAudioVisualizerPipes() {
        // Collect mic amplitude
        viewModelScope.launch {
            audioRecorder.amplitude.collect { micAmp ->
                if (_uiState.value.assistantState == AssistantState.LISTENING) {
                    _uiState.update { it.copy(amplitude = micAmp) }
                }
            }
        }

        // Collect speaker amplitude
        viewModelScope.launch {
            audioPlayer.amplitude.collect { spkAmp ->
                if (_uiState.value.assistantState == AssistantState.SPEAKING) {
                    _uiState.update { it.copy(amplitude = spkAmp) }
                }
            }
        }

        // Recorder auto-VAD trigger
        audioRecorder.onSilenceDetected = { pcmBytes ->
            if (_uiState.value.assistantState == AssistantState.LISTENING && pcmBytes.isNotEmpty()) {
                submitVoiceUtterance(pcmBytes)
            }
        }
    }

    /**
     * Primary action: Toggles Zoya between Connected/Listening and Disconnected,
     * or interrupts active speech.
     */
    fun onPowerOrOrbClicked() {
        val currentState = _uiState.value.assistantState
        when (currentState) {
            AssistantState.DISCONNECTED -> {
                wakeAndConnect()
            }
            AssistantState.SPEAKING, AssistantState.THINKING -> {
                // Interruption! Halts playback and begins listening immediately
                interruptAndListen()
            }
            AssistantState.LISTENING -> {
                // Manually stop and process recorded buffer
                val captured = audioRecorder.stopRecording()
                if (captured.isNotEmpty()) {
                    submitVoiceUtterance(captured)
                } else {
                    disconnect()
                }
            }
            AssistantState.CONNECTING -> {
                disconnect()
            }
        }
    }

    fun wakeAndConnect() {
        activeRequestJob?.cancel()
        audioPlayer.stopPlayback()
        SoundEffects.playConnect()

        _uiState.update {
            it.copy(
                assistantState = AssistantState.CONNECTING,
                statusMessage = "Connecting to Zoya...",
                subtitleText = "Booting neural circuits... ✨",
                errorMessage = null
            )
        }

        viewModelScope.launch {
            delay(400)
            geminiService.resetSession()
            geminiService.selectedVoice = _uiState.value.selectedVoice
            geminiService.sassLevel = _uiState.value.sassLevel

            startListeningSession()
        }
    }

    fun startListeningSession() {
        if (_uiState.value.isMuted) return
        audioPlayer.stopPlayback()
        SoundEffects.playListeningStart()

        _uiState.update {
            it.copy(
                assistantState = AssistantState.LISTENING,
                statusMessage = "Listening...",
                subtitleText = "I'm listening darling, speak your mind...",
                amplitude = 0f,
                errorMessage = null
            )
        }

        audioRecorder.startRecording(autoVad = _uiState.value.isContinuousListening)
    }

    fun interruptAndListen() {
        SoundEffects.playInterrupt()
        activeRequestJob?.cancel()
        audioPlayer.stopPlayback()
        startListeningSession()
    }

    fun disconnect() {
        activeRequestJob?.cancel()
        audioPlayer.stopPlayback()
        audioRecorder.stopRecording()
        SoundEffects.playDisconnect()

        _uiState.update {
            it.copy(
                assistantState = AssistantState.DISCONNECTED,
                statusMessage = "Standby",
                subtitleText = "Tap the glowing orb to wake Zoya",
                amplitude = 0f,
                lastExecutedTool = null
            )
        }
    }

    fun submitPrompt(prompt: String) {
        if (_uiState.value.assistantState == AssistantState.SPEAKING) {
            audioPlayer.stopPlayback()
        }
        audioRecorder.stopRecording()

        _uiState.update { state ->
            val turns = state.historyTurns.toMutableList().apply {
                add(TurnItem(id = UUID.randomUUID().toString(), sender = "You", text = prompt))
            }
            state.copy(
                assistantState = AssistantState.THINKING,
                statusMessage = "Zoya is thinking...",
                subtitleText = "Analyzing your vibe... 💭",
                historyTurns = turns
            )
        }

        activeRequestJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val result = geminiService.sendTextTurn(prompt)
            val latency = System.currentTimeMillis() - startTime

            handleGeminiResult(result, latency)
        }
    }

    private fun submitVoiceUtterance(pcmBytes: ByteArray) {
        audioRecorder.stopRecording()

        _uiState.update { state ->
            val turns = state.historyTurns.toMutableList().apply {
                add(TurnItem(id = UUID.randomUUID().toString(), sender = "You", text = "[Voice Audio]"))
            }
            state.copy(
                assistantState = AssistantState.THINKING,
                statusMessage = "Zoya is thinking...",
                subtitleText = "Processing your voice... 💭",
                historyTurns = turns
            )
        }

        activeRequestJob = viewModelScope.launch {
            val startTime = System.currentTimeMillis()
            val result = geminiService.sendAudioTurn(pcmBytes)
            val latency = System.currentTimeMillis() - startTime

            handleGeminiResult(result, latency)
        }
    }

    private fun handleGeminiResult(result: GeminiLiveResult, latencyMs: Long) {
        when (result) {
            is GeminiLiveResult.Success -> {
                val zoyaText = result.text.ifBlank { "Here for you babe!" }

                // Check tool execution
                if (result.executedTool != null) {
                    SoundEffects.playToolExecuted()
                }

                _uiState.update { state ->
                    val turns = state.historyTurns.toMutableList().apply {
                        add(TurnItem(id = UUID.randomUUID().toString(), sender = "Zoya", text = zoyaText, toolTag = result.executedTool))
                    }
                    state.copy(
                        assistantState = AssistantState.SPEAKING,
                        statusMessage = "Zoya is speaking",
                        subtitleText = zoyaText,
                        lastExecutedTool = result.executedTool,
                        sessionLatencyMs = latencyMs,
                        historyTurns = turns,
                        isFlashlightOn = deviceTools.isFlashlightOn()
                    )
                }

                if (result.audioBytes != null && result.audioBytes.isNotEmpty()) {
                    // Stream response audio via AudioTrack
                    audioPlayer.playWavBytes(result.audioBytes, AudioPlayer.DEFAULT_SAMPLE_RATE)
                    
                    // Poll playback state and transition back when done
                    viewModelScope.launch {
                        delay(600) // initial warm up
                        while (audioPlayer.isSpeaking.value) {
                            delay(100)
                        }
                        onSpeakingFinished()
                    }
                } else {
                    // Text-only fallback timing simulation
                    viewModelScope.launch {
                        val duration = (zoyaText.length * 55L).coerceIn(1500L, 5000L)
                        delay(duration)
                        onSpeakingFinished()
                    }
                }
            }
            is GeminiLiveResult.Error -> {
                Log.e(TAG, "Gemini Live error: ${result.message}")
                _uiState.update {
                    it.copy(
                        assistantState = AssistantState.DISCONNECTED,
                        statusMessage = "Connection Error",
                        subtitleText = "Ugh, my connection blinked! Tap to wake me back up. 💅",
                        errorMessage = result.message
                    )
                }
            }
        }
    }

    private fun onSpeakingFinished() {
        if (_uiState.value.assistantState == AssistantState.SPEAKING) {
            if (_uiState.value.isContinuousListening) {
                startListeningSession()
            } else {
                _uiState.update {
                    it.copy(
                        assistantState = AssistantState.DISCONNECTED,
                        statusMessage = "Standby",
                        subtitleText = "Tap the glowing orb to chat again"
                    )
                }
            }
        }
    }

    fun setVoice(voiceName: String) {
        _uiState.update { it.copy(selectedVoice = voiceName) }
        geminiService.selectedVoice = voiceName
    }

    fun setSassLevel(level: Int) {
        _uiState.update { it.copy(sassLevel = level) }
        geminiService.sassLevel = level
    }

    fun toggleContinuousListening() {
        _uiState.update { it.copy(isContinuousListening = !it.isContinuousListening) }
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        _uiState.update { it.copy(isMuted = newMuted) }
        if (newMuted && _uiState.value.assistantState == AssistantState.LISTENING) {
            audioRecorder.stopRecording()
        }
    }

    fun toggleSettingsDialog(open: Boolean) {
        _uiState.update { it.copy(isSettingsOpen = open) }
    }

    fun toggleHistoryDialog(open: Boolean) {
        _uiState.update { it.copy(isHistoryOpen = open) }
    }

    fun clearHistory() {
        _uiState.update { it.copy(historyTurns = emptyList()) }
        geminiService.resetSession()
    }

    override fun onCleared() {
        super.onCleared()
        audioRecorder.stopRecording()
        audioPlayer.stopPlayback()
    }
}
