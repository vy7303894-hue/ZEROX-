package com.example.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Captures microphone PCM16 audio at 16kHz for streaming to Gemini Live API.
 * Emits real-time RMS visualizer data and supports automatic Voice Activity Detection (VAD).
 */
class AudioRecorder {
    companion object {
        private const val TAG = "ZoyaAudioRecorder"
        const val SAMPLE_RATE = 16000
        const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CHUNK_SIZE_SAMPLES = 1024 // ~64ms chunks
        private const val SILENCE_THRESHOLD_RMS = 650.0 // RMS threshold for voice activity
        private const val SILENCE_TIMEOUT_CHUNKS = 24 // ~1.5s silence triggers auto-finish
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _isRecordingFlow = MutableStateFlow(false)
    val isRecordingFlow: StateFlow<Boolean> = _isRecordingFlow.asStateFlow()

    var onAudioChunk: ((ByteArray) -> Unit)? = null
    var onSilenceDetected: ((ByteArray) -> Unit)? = null

    private val capturedAudioBuffer = ByteArrayOutputStream()
    private var hasDetectedVoice = false
    private var silentChunkCount = 0

    @SuppressLint("MissingPermission")
    fun startRecording(autoVad: Boolean = true) {
        if (isRecording.get()) return

        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufferSize = (minBufferSize * 2).coerceAtLeast(CHUNK_SIZE_SAMPLES * 4)

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord initialization failed")
                return
            }

            audioRecord?.startRecording()
            isRecording.set(true)
            _isRecordingFlow.value = true
            hasDetectedVoice = false
            silentChunkCount = 0
            capturedAudioBuffer.reset()

            recordJob = scope.launch {
                val shortBuffer = ShortArray(CHUNK_SIZE_SAMPLES)
                val byteChunk = ByteArray(CHUNK_SIZE_SAMPLES * 2)

                while (isRecording.get()) {
                    val readShorts = audioRecord?.read(shortBuffer, 0, CHUNK_SIZE_SAMPLES) ?: -1
                    if (readShorts > 0) {
                        var sumSquares = 0.0
                        for (i in 0 until readShorts) {
                            val sample = shortBuffer[i]
                            byteChunk[i * 2] = (sample.toInt() and 0xFF).toByte()
                            byteChunk[i * 2 + 1] = ((sample.toInt() shr 8) and 0xFF).toByte()
                            sumSquares += sample * sample
                        }

                        // Calculate RMS amplitude normalized to 0.0 - 1.0
                        val rms = sqrt(sumSquares / readShorts)
                        val normalized = (rms / 8000.0).toFloat().coerceIn(0f, 1f)
                        _amplitude.value = normalized

                        val actualBytes = byteChunk.copyOf(readShorts * 2)
                        capturedAudioBuffer.write(actualBytes)
                        onAudioChunk?.invoke(actualBytes)

                        // Simple Voice Activity & Silence Detection
                        if (autoVad) {
                            if (rms > SILENCE_THRESHOLD_RMS) {
                                hasDetectedVoice = true
                                silentChunkCount = 0
                            } else if (hasDetectedVoice) {
                                silentChunkCount++
                                if (silentChunkCount >= SILENCE_TIMEOUT_CHUNKS) {
                                    Log.d(TAG, "Silence detected after speech, completing utterance")
                                    val fullBuffer = capturedAudioBuffer.toByteArray()
                                    onSilenceDetected?.invoke(fullBuffer)
                                    hasDetectedVoice = false
                                    silentChunkCount = 0
                                    capturedAudioBuffer.reset()
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting AudioRecord: ${e.message}", e)
            stopRecording()
        }
    }

    fun stopRecording(): ByteArray {
        isRecording.set(false)
        _isRecordingFlow.value = false
        _amplitude.value = 0f

        try {
            recordJob?.cancel()
            recordJob = null
            audioRecord?.apply {
                if (recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    stop()
                }
                release()
            }
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioRecord: ${e.message}")
        }

        val data = capturedAudioBuffer.toByteArray()
        capturedAudioBuffer.reset()
        return data
    }
}
