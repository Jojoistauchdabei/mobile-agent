package com.mobileagent.actions

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.mobileagent.llm.BonsaiLlm

/**
 * Handy-Steuerung Stufe 1 (ohne Root): explizite Intents.
 * Stufe 2 (mit User-Zustimmung): AgentAccessibilityService für Klicks/Scrolls.
 */
class AndroidActions(private val context: Context) {
    fun execute(action: BonsaiLlm.DeviceAction?): List<String> {
        if (action == null) return emptyList()
        return when (action.type) {
            "open_app" -> {
                val q = action.args["query"].orEmpty()
                // MVP: generischer VIEW-Intent; App-spezifisches Mapping folgt.
                val intent = Intent(Intent.ACTION_VIEW).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                try {
                    context.startActivity(intent)
                    listOf("Intent ausgeführt für: $q")
                } catch (e: Exception) {
                    listOf("Aktion fehlgeschlagen: ${e.message}")
                }
            }
            else -> listOf("Unbekannte Aktion: ${action.type}")
        }
    }
}

class AgentAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
