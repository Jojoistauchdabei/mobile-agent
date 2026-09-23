package com.mobileagent.router

import android.content.Context
import com.mobileagent.RouteDecision

/**
 * Laya = System-1 Entscheidungsmodell (https://huggingface.co/convaiinnovations/laya).
 * ModernBERT/mmBERT, ONNX. Kein Text-Generator: beantwortet typisierte Fragen
 * in einem Forward-Pass (~33 ms) mit kalibrierten Wahrscheinlichkeiten.
 *
 * Nutzung OPTIONAL (per [RouterConfig]): wenn deaktiviert, läuft die Pipeline
 * ohne Laya (LLM entscheidet direkt). Wenn aktiviert, triggert Laya Actions:
 * intent=choice(chat|search|device_action|unsafe), dazu noul-Fragen
 * (needs_search, needs_action, blocked) + score(urgency).
 */
data class RouterConfig(
    val enabled: Boolean = true,
    val actionTriggerEnabled: Boolean = true,
    val confidenceThreshold: Double = 0.6,
)

class LayaRouter(
    private val context: Context,
    var config: RouterConfig = RouterConfig(),
) {
    // TODO: ONNX Runtime Mobile Session mit laya-multilingual.onnx laden.
    // Modell-Download siehe scripts/setup-models.sh (322M, Apache-2.0).

    fun route(text: String): RouteDecision {
        if (!config.enabled) {
            // Bypass: kein Laya-Pflicht – LLM bekommt alles als chat + darf selbst Tools wählen.
            return RouteDecision("chat", needsSearch = false, needsAction = false, blocked = false, 0.0)
        }
        // Heuristik-Platzhalter bis ONNX integriert ist (gleiche Fragen wie Laya):
        val lower = text.lowercase()
        val wantsSearch = listOf("suche", "google", "finde", "wetter", "news", "wer ", "was ", "wann ")
            .any { it in lower }
        val wantsAction = config.actionTriggerEnabled && listOf("öffne", "starte", "stelle", "ruf an", "timer", "wecker", "helligkeit")
            .any { it in lower }
        val blocked = listOf("pin", "passwort", "bank").any { it in lower } && "umgeh" in lower
        val intent = when {
            blocked -> "unsafe"
            wantsAction -> "device_action"
            wantsSearch -> "search"
            else -> "chat"
        }
        return RouteDecision(intent, wantsSearch, wantsAction, blocked, 0.5)
    }
}
