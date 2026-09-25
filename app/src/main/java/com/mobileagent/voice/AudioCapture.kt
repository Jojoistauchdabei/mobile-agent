package com.mobileagent.voice

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max
import kotlin.math.sqrt

data class Pcm16Audio(
    val samples: ShortArray,
    val sampleRate: Int,
) {
    val durationMs: Long
        get() = samples.size.toLong() * 1_000L / sampleRate
}

sealed interface AudioCaptureResult {
    data class Captured(val audio: Pcm16Audio) : AudioCaptureResult
    data object PermissionRequired : AudioCaptureResult
    data object NoSpeech : AudioCaptureResult
    data class Failed(val message: String) : AudioCaptureResult
}

class AudioCapture(
    private val context: Context,
    private val sampleRate: Int = 16_000,
    private val maxDurationMs: Long = 15_000,
) {
    private val active = AtomicBoolean(false)

    fun hasPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    suspend fun capture(): AudioCaptureResult = withContext(Dispatchers.IO) {
        if (!active.compareAndSet(false, true)) {
            return@withContext AudioCaptureResult.Failed("Es läuft bereits eine Aufnahme")
        }
        try {
            captureInternal()
        } finally {
            active.set(false)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun captureInternal(): AudioCaptureResult {
        if (!hasPermission()) return AudioCaptureResult.PermissionRequired

        val minimumBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        if (minimumBuffer <= 0) return AudioCaptureResult.Failed("Audioformat wird nicht unterstützt")

        val bufferSize = max(minimumBuffer, sampleRate / 5 * 2)
        val recorder = try {
            AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(sampleRate)
                        .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                        .build(),
                )
                .setBufferSizeInBytes(bufferSize)
                .build()
        } catch (error: Throwable) {
            return AudioCaptureResult.Failed(error.message ?: "Audio konnte nicht geöffnet werden")
        }

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return AudioCaptureResult.Failed("AudioRecord konnte nicht initialisiert werden")
        }

        val cancellationHandle = currentCoroutineContext()[Job]?.invokeOnCompletion { cause ->
            if (cause is CancellationException) runCatching { recorder.stop() }
        }
        val samples = ArrayList<Short>(sampleRate.toInt() * (maxDurationMs / 1_000L).toInt())
        val block = ShortArray(max(minimumBuffer / 2, sampleRate / 10))
        var voicedMs = 0L
        var silentMs = 0L
        val startedAt = SystemClock.elapsedRealtime()

        try {
            recorder.startRecording()
            while (SystemClock.elapsedRealtime() - startedAt < maxDurationMs) {
                val count = recorder.read(block, 0, block.size, AudioRecord.READ_BLOCKING)
                if (count < 0) return AudioCaptureResult.Failed("AudioRecord hat einen Lesefehler gemeldet")
                if (count == 0) continue
                var energy = 0.0
                for (index in 0 until count) {
                    val value = block[index].toDouble()
                    energy += value * value
                }
                val rms = sqrt(energy / count).toFloat()
                if (rms >= 350f) {
                    voicedMs += count * 1_000L / sampleRate
                    silentMs = 0
                } else {
                    silentMs += count * 1_000L / sampleRate
                }
                for (index in 0 until count) samples += block[index]
                if (voicedMs >= 180L && silentMs >= 700L) break
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: SecurityException) {
            return AudioCaptureResult.PermissionRequired
        } catch (error: Throwable) {
            return AudioCaptureResult.Failed(error.message ?: "Aufnahme fehlgeschlagen")
        } finally {
            cancellationHandle?.dispose()
            runCatching { recorder.stop() }
            recorder.release()
        }

        return if (voicedMs < 180L || samples.isEmpty()) {
            AudioCaptureResult.NoSpeech
        } else {
            AudioCaptureResult.Captured(Pcm16Audio(samples.toShortArray(), sampleRate))
        }
    }
}
