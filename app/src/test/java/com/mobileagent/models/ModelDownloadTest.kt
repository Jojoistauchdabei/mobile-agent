package com.mobileagent.models

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class DownloadProgressTest {
    @Test
    fun percentIsClampedAndHandlesUnknownTotal() {
        assertEquals(0, ModelDownloadNotifications.progressPercent(0, 0))
        assertEquals(0, ModelDownloadNotifications.progressPercent(10, -1))
        assertEquals(50, ModelDownloadNotifications.progressPercent(50, 100))
        assertEquals(100, ModelDownloadNotifications.progressPercent(100, 100))
        assertEquals(100, ModelDownloadNotifications.progressPercent(200, 100))
    }

    @Test
    fun textFormatsKnownAndUnknownTotals() {
        assertEquals(
            "15 / 100 MB · 15 %",
            ModelDownloadNotifications.progressText(15 * 1_048_576, 100 * 1_048_576),
        )
        assertEquals(
            "12 MB geladen …",
            ModelDownloadNotifications.progressText(12 * 1_048_576, -1),
        )
    }

    @Test
    fun notificationIdsAreStableAndDistinct() {
        assertEquals(
            ModelDownloadNotifications.notificationIdFor("whisper-tiny"),
            ModelDownloadNotifications.notificationIdFor("whisper-tiny"),
        )
        assertTrue(
            ModelDownloadNotifications.notificationIdFor("whisper-tiny") !=
                ModelDownloadNotifications.notificationIdFor("bonsai-1.7b"),
        )
    }
}

class DownloadPolicyTest {
    @Test
    fun networkErrorsAreRetried() {
        assertTrue(ModelDownloadPolicy.shouldRetry(IOException("HTTP 500")))
        assertTrue(ModelDownloadPolicy.shouldRetry(IOException("Unable to resolve host")))
        assertTrue(ModelDownloadPolicy.shouldRetry(IOException("timeout")))
    }

    @Test
    fun validationErrorsAreFinal() {
        assertFalse(ModelDownloadPolicy.shouldRetry(IOException("SHA-256-Prüfung fehlgeschlagen")))
        assertFalse(ModelDownloadPolicy.shouldRetry(IOException("Unvollständiger Download: 1/2 Bytes")))
        assertFalse(ModelDownloadPolicy.shouldRetry(IOException("Atomares Umbenennen der Modelldatei fehlgeschlagen")))
        assertFalse(ModelDownloadPolicy.shouldRetry(IllegalStateException("Kein Download hinterlegt")))
        assertFalse(ModelDownloadPolicy.shouldRetry(CancellationException("abgebrochen")))
    }
}

class DownloadSchedulerTest {
    @Test
    fun requestCarriesModelIdAndTag() {
        val request = ModelDownloadScheduler.requestFor(DefaultModels.whisper)

        assertEquals("whisper-tiny", request.workSpec.input.getString(ModelDownloadContract.KEY_MODEL_ID))
        assertTrue(request.tags.contains(ModelDownloadContract.tagFor("whisper-tiny")))
        assertEquals("model-download-whisper-tiny", ModelDownloadContract.uniqueNameFor("whisper-tiny"))
    }
}
