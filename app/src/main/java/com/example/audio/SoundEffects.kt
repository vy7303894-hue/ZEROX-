package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Generates low-latency sci-fi synthesized sound effects directly using PCM audio synthesis.
 */
object SoundEffects {
    private val scope = CoroutineScope(Dispatchers.Default)
    private const val SAMPLE_RATE = 24000

    fun playConnect() {
        scope.launch {
            playTones(listOf(523.25 to 60, 659.25 to 60, 783.99 to 80, 1046.50 to 140))
        }
    }

    fun playDisconnect() {
        scope.launch {
            playTones(listOf(783.99 to 70, 659.25 to 70, 523.25 to 120))
        }
    }

    fun playListeningStart() {
        scope.launch {
            playChirp(440.0, 880.0, 90)
        }
    }

    fun playToolExecuted() {
        scope.launch {
            playTones(listOf(880.0 to 50, 1320.0 to 90))
        }
    }

    fun playInterrupt() {
        scope.launch {
            playChirp(600.0, 300.0, 50)
        }
    }

    private fun playChirp(startFreq: Double, endFreq: Double, durationMs: Int) {
        try {
            val numSamples = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
            val buffer = ShortArray(numSamples)
            for (i in 0 until numSamples) {
                val t = i.toDouble() / SAMPLE_RATE
                val progress = i.toDouble() / numSamples
                val freq = startFreq + (endFreq - startFreq) * progress
                val envelope = sin(PI * progress) // smooth window
                val sample = (sin(2 * PI * freq * t) * envelope * Short.MAX_VALUE * 0.35).toInt()
                buffer[i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
            }
            playPcmBuffer(buffer)
        } catch (_: Exception) {}
    }

    private fun playTones(tones: List<Pair<Double, Int>>) {
        try {
            var totalSamples = 0
            tones.forEach { totalSamples += (SAMPLE_RATE * (it.second / 1000.0)).toInt() }
            val buffer = ShortArray(totalSamples)
            var offset = 0

            tones.forEach { (freq, durationMs) ->
                val count = (SAMPLE_RATE * (durationMs / 1000.0)).toInt()
                for (i in 0 until count) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val decay = exp(-3.0 * i / count)
                    val sample = (sin(2 * PI * freq * t) * decay * Short.MAX_VALUE * 0.4).toInt()
                    buffer[offset + i] = sample.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                offset += count
            }
            playPcmBuffer(buffer)
        } catch (_: Exception) {}
    }

    private fun playPcmBuffer(buffer: ShortArray) {
        val minBufferSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(buffer.size.coerceAtLeast(minBufferSize) * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(buffer, 0, buffer.size)
        track.play()
        track.setNotificationMarkerPosition(buffer.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack?) {
                track.release()
            }
            override fun onPeriodicNotification(t: AudioTrack?) {}
        })
    }
}
