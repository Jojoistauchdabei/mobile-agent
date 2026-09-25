package com.mobileagent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mobileagent.voice.NativeWhisperTranscriber
import com.mobileagent.voice.Pcm16Audio
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WhisperNativeSmokeTest {
    @Test
    fun nativeLibraryLoadsAndTranscribes() {
        assertTrue(NativeWhisperTranscriber.isAvailable())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val model = File(context.filesDir, "models/ggml-tiny.bin")
        assumeTrue(model.isFile && model.length() > 0)
        val silence = Pcm16Audio(ShortArray(16_000), 16_000)
        val text = NativeWhisperTranscriber.transcribe(model.absolutePath, silence)
        assertNotNull(text)
    }
}
