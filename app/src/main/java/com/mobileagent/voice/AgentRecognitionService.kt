package com.mobileagent.voice

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionService
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.mobileagent.models.ModelManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Spracheingabe fuer Tastaturen und Systemdialoge: Die Tastatur ruft diesen Dienst
 * ueber das Mikrofon-Symbol auf, hier wird lokal aufgenommen (AudioCapture) und mit
 * Whisper transkribiert. Ohne Whisper-Modell bzw. JNI-Bibliothek wird ein Fehler
 * gemeldet statt eine erfundene Transkription zu liefern.
 */
class AgentRecognitionService : RecognitionService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var listening: Job? = null

    override fun onStartListening(intent: Intent?, callback: RecognitionService.Callback) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            runCatching { callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) }
            return
        }
        val stt = WhisperStt(applicationContext, ModelManager(applicationContext))
        val capture = AudioCapture(applicationContext)
        listening?.cancel()
        listening = scope.launch {
            announce(callback)
            when (val audio = capture.capture()) {
                is AudioCaptureResult.Captured -> {
                    runCatching { callback.endOfSpeech() }
                    when (val result = stt.transcribe(audio.audio)) {
                        is SttResult.Text -> runCatching {
                            callback.results(Bundle().apply {
                                putStringArray(KEY_RESULTS, arrayOf(result.text))
                            })
                        }
                        is SttResult.Failed -> runCatching {
                            callback.error(SpeechRecognizer.ERROR_SERVER)
                        }
                        is SttResult.Unavailable -> runCatching {
                            callback.error(SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT)
                        }
                    }
                }
                is AudioCaptureResult.NoSpeech -> runCatching {
                    callback.error(SpeechRecognizer.ERROR_NO_MATCH)
                }
                is AudioCaptureResult.PermissionRequired -> runCatching {
                    callback.error(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS)
                }
                is AudioCaptureResult.Failed -> runCatching {
                    callback.error(SpeechRecognizer.ERROR_CLIENT)
                }
            }
        }
    }

    override fun onCancel(callback: RecognitionService.Callback) {
        listening?.cancel()
        listening = null
    }

    override fun onStopListening(callback: RecognitionService.Callback) {
        listening?.cancel()
        listening = null
    }

    override fun onDestroy() {
        listening?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun announce(callback: RecognitionService.Callback) {
        runCatching { callback.readyForSpeech(Bundle()) }
        runCatching { callback.beginningOfSpeech() }
    }

    companion object {
        /** Ergebnis-Bundle-Schlüssel des Frameworks (Konstante ist @hide). */
        const val KEY_RESULTS = "results"

        fun resultBundle(text: String): Bundle = Bundle().apply {
            putStringArray(KEY_RESULTS, arrayOf(text))
        }
    }
}
