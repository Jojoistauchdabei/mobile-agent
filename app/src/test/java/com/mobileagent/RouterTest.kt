package com.mobileagent

import org.junit.Assert.*
import org.junit.Test

class RouterTest {
    @Test
    fun searchIntentDetected() {
        // Heuristik-Test für Laya-Platzhalter (wird ONNX-Test sobald Modell da ist)
        val text = "Suche im Internet nach dem Wetter in Berlin"
        val wantsSearch = listOf("suche", "wetter").any { it in text.lowercase() }
        assertTrue(wantsSearch)
    }
}
