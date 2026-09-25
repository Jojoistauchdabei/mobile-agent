package com.mobileagent.voice

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class Speaker(context: Context) : TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context.applicationContext, this)
    @Volatile
    private var ready = false

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) {
            engine.language = Locale.GERMAN
            engine.setSpeechRate(1.0f)
        }
    }

    fun speak(text: String) {
        if (ready && text.isNotBlank()) {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "mobile-agent-response")
        }
    }

    fun stop() {
        engine.stop()
    }

    fun close() {
        ready = false
        engine.stop()
        engine.shutdown()
    }
}
