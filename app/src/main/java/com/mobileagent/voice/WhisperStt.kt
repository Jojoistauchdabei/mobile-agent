package com.mobileagent.voice

import android.content.Context
import com.mobileagent.models.ModelManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

sealed interface SttResult {
    data class Text(val text: String, val language: String) : SttResult
    data class Unavailable(val reason: String) : SttResult
    data class Failed(val message: String) : SttResult
}

interface WhisperTranscriber {
    fun isAvailable(): Boolean
    fun transcribe(modelPath: String, audio: Pcm16Audio): String
}

object NativeWhisperTranscriber : WhisperTranscriber {
    private var loadAttempted = false
    private var loaded = false

    override fun isAvailable(): Boolean {
        if (!loadAttempted) {
            loadAttempted = true
            loaded = runCatching { System.loadLibrary("mobileagent-whisper") }.isSuccess
        }
        return loaded
    }

    override fun transcribe(modelPath: String, audio: Pcm16Audio): String =
        nativeTranscribe(modelPath, audio.samples, audio.sampleRate)

    private external fun nativeTranscribe(
        modelPath: String,
        samples: ShortArray,
        sampleRate: Int,
    ): String
}

class WhisperStt(
    context: Context,
    private val models: ModelManager,
    private val transcriber: WhisperTranscriber = NativeWhisperTranscriber,
) : SpeechToText {
    private val modelPath = models.fileFor(com.mobileagent.models.DefaultModels.whisper)

    override suspend fun transcribe(audio: Pcm16Audio): SttResult = withContext(Dispatchers.IO) {
        currentCoroutineContext().ensureActive()
        if (!modelPath.isFile || modelPath.length() == 0L) {
            return@withContext SttResult.Unavailable("Whisper-Modell fehlt. Bitte im Modell-Download laden.")
        }
        if (!transcriber.isAvailable()) {
            return@withContext SttResult.Unavailable("Whisper-JNI-Bibliothek ist in diesem Build nicht enthalten.")
        }
        try {
            val text = transcriber.transcribe(modelPath.absolutePath, audio).trim()
            currentCoroutineContext().ensureActive()
            SttResult.Text(text, "de")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            SttResult.Failed(error.message ?: "Transkription fehlgeschlagen")
        }
    }
}

interface SpeechToText {
    suspend fun transcribe(audio: Pcm16Audio): SttResult
}
