package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * High-performance, low-latency audio player for streaming PCM audio from Gemini Live.
 * Supports real-time RMS visualizer feedback and immediate interrupt cancellation.
 */
class AudioPlayer {
    companion object {
        private const val TAG = "ZoyaAudioPlayer"
        const val DEFAULT_SAMPLE_RATE = 24000
    }

    private var audioTrack: AudioTrack? = null
    private val isPlaying = AtomicBoolean(false)
    private val chunkQueue = LinkedBlockingQueue<ByteArray>()
    private var playbackJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    var onPlaybackFinished: (() -> Unit)? = null

    fun startPlayback(sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        stopPlayback()
        isPlaying.set(true)
        _isSpeaking.value = true
        chunkQueue.clear()

        val minBufferSize = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = (minBufferSize * 2).coerceAtLeast(8192)

        try {
            audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            audioTrack?.play()

            playbackJob = scope.launch {
                val shortBuffer = ShortArray(2048)
                while (isPlaying.get()) {
                    val chunk = chunkQueue.poll(150, java.util.concurrent.TimeUnit.MILLISECONDS)
                    if (chunk != null) {
                        val track = audioTrack ?: break
                        var offset = 0
                        while (offset < chunk.size && isPlaying.get()) {
                            val bytesToWrite = minOf(chunk.size - offset, shortBuffer.size * 2)
                            var sumSquares = 0.0
                            val shortsCount = bytesToWrite / 2

                            for (i in 0 until shortsCount) {
                                val byteIdx = offset + i * 2
                                val sample = if (byteIdx + 1 < chunk.size) {
                                    ((chunk[byteIdx + 1].toInt() shl 8) or (chunk[byteIdx].toInt() and 0xFF)).toShort()
                                } else {
                                    0.toShort()
                                }
                                shortBuffer[i] = sample
                                sumSquares += sample * sample
                            }

                            // Calculate RMS volume normalized to 0.0 - 1.0
                            val rms = sqrt(sumSquares / shortsCount.coerceAtLeast(1))
                            val normalized = (rms / 12000.0).toFloat().coerceIn(0f, 1f)
                            _amplitude.value = normalized

                            track.write(shortBuffer, 0, shortsCount)
                            offset += bytesToWrite
                        }
                    } else {
                        // Queue is empty
                        if (isPlaying.get() && chunkQueue.isEmpty()) {
                            _amplitude.value = 0f
                        }
                    }
                }
                _amplitude.value = 0f
                _isSpeaking.value = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing AudioTrack: ${e.message}", e)
            stopPlayback()
        }
    }

    fun queueAudio(data: ByteArray) {
        if (isPlaying.get()) {
            chunkQueue.offer(data)
        }
    }

    /**
     * Instantly halts playback and flushes queued audio buffers on user interruption.
     */
    fun stopPlayback() {
        isPlaying.set(false)
        _isSpeaking.value = false
        _amplitude.value = 0f
        chunkQueue.clear()

        try {
            playbackJob?.cancel()
            playbackJob = null
            audioTrack?.apply {
                if (playState == AudioTrack.PLAYSTATE_PLAYING) {
                    pause()
                    flush()
                    stop()
                }
                release()
            }
            audioTrack = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing audio track: ${e.message}")
        }
    }

    fun playWavBytes(wavData: ByteArray, sampleRate: Int = DEFAULT_SAMPLE_RATE) {
        // If WAV header is present (starts with RIFF), strip the 44-byte header
        val pcmData = if (wavData.size > 44 &&
            wavData[0] == 'R'.code.toByte() &&
            wavData[1] == 'I'.code.toByte() &&
            wavData[2] == 'F'.code.toByte() &&
            wavData[3] == 'F'.code.toByte()
        ) {
            wavData.copyOfRange(44, wavData.size)
        } else {
            wavData
        }

        startPlayback(sampleRate)
        queueAudio(pcmData)
    }
}
