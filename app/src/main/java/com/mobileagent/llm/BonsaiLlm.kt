package com.mobileagent.llm

import android.content.Context
import com.mobileagent.RouteDecision

/**
 * Bonsai 1.7B (Ternary, Q2_0 GGUF, 442 MB) via llama.cpp.
 *
 * WICHTIG: Q2_0 läuft NICHT mit mainline-llama.cpp, sondern mit dem Prism-Fork:
 * https://github.com/PrismML-Eng/llama.cpp (Branch `prism`, default).
 * JNI-Wrapper (llama-android) muss gegen diesen Fork gebaut werden.
 * Default-Datei: Ternary-Bonsai-1.7B-Q2_0.gguf, 32k Kontext, Tool-Calling-Template (Qwen3).
 */
class BonsaiLlm(private val context: Context) {
    // TODO: JNI anbinden (llama.cpp Android lib + Prism-Q2_0-Kernel).

    data class LlmOutput(val reply: String, val action: DeviceAction? = null)
    data class DeviceAction(val type: String, val args: Map<String, String> = emptyMap())

    fun generate(userText: String, route: RouteDecision, snippets: List<String>): LlmOutput {
        val ctx = if (snippets.isEmpty()) "" else "\nRecherche:\n" + snippets.joinToString("\n- ")
        return when (route.intent) {
            "device_action" -> LlmOutput(
                reply = "Verstanden, ich führe das auf dem Gerät aus.",
                action = DeviceAction("open_app", mapOf("query" to userText)),
            )
            "search" -> LlmOutput(reply = "Hier was ich gefunden habe:$ctx")
            else -> LlmOutput(reply = "(Bonsai-Platzhalter) Du sagtest: $userText$ctx")
        }
    }
}
