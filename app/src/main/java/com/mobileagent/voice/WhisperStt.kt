package com.mobileagent.voice

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/**
 * Whisper STT via whisper.cpp (ggml-tiny.bin Default, ~75 MB, DE+EN).
 * Download: scripts/setup-models.sh
 * TODO: JNI (whisper.cpp Android-Beispiel) + 16kHz AudioRecord-Pipeline.
 */
class WhisperStt(private val context: Context) {
    fun transcribe(pcm16k: ShortArray): String {
        // Platzhalter bis JNI steht:
        return ""
    }
}

class VoiceService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
