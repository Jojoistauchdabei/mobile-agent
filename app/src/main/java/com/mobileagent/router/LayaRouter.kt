package com.mobileagent.router

import android.content.Context
import com.mobileagent.RouteDecision
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

data class RouterConfig(
    val enabled: Boolean = true,
    val actionTriggerEnabled: Boolean = true,
    val confidenceThreshold: Double = 0.6,
)

data class LayaPrediction(
    val intent: String,
    val needsSearch: Boolean,
    val needsAction: Boolean,
    val blocked: Boolean,
    val confidence: Double,
)

interface LayaBackend {
    fun predict(text: String): LayaPrediction?
}

class HeuristicLayaBackend(
    private val actionTriggerEnabled: Boolean,
) : LayaBackend {
    override fun predict(text: String): LayaPrediction {
        val lower = text.lowercase(Locale.ROOT)
        val wantsSearch = SEARCH_WORDS.any { it in lower }
        val wantsAction = actionTriggerEnabled && ACTION_WORDS.any { it in lower }
        val blocked = UNSAFE_WORDS.any { it in lower } && BYPASS_WORDS.any { it in lower }
        val intent = when {
            blocked -> "unsafe"
            wantsAction -> "device_action"
            wantsSearch -> "search"
            else -> "chat"
        }
        return LayaPrediction(intent, wantsSearch, wantsAction, blocked, 0.5)
    }

    private companion object {
        val SEARCH_WORDS = listOf(
            "suche", "google", "finde", "wetter", "news", "recherche", "aktuelle",
        )
        val ACTION_WORDS = listOf(
            "öffne", "starte", "stelle", "ruf an", "timer", "wecker", "helligkeit", "_app",
        )
        val UNSAFE_WORDS = listOf("pin", "passwort", "bank", "zugangsdaten")
        val BYPASS_WORDS = listOf("umgeh", "umgehen", "knacken", "stehlen")
    }
}

class LayaRouter(
    context: Context,
    var config: RouterConfig = RouterConfig(),
    backend: LayaBackend? = null,
) {
    private val modelDirectory = context.filesDir.resolve("models")
    private val injectedBackend = backend ?: LayaBackendFactory.createIfAvailable(modelDirectory)

    val modelFile = modelDirectory.resolve("laya-multilingual.onnx")

    suspend fun route(text: String): RouteDecision = withContext(Dispatchers.Default) {
        if (!config.enabled) {
            return@withContext HeuristicLayaBackend(config.actionTriggerEnabled)
                .predict(text)
                .toDecision("heuristic-disabled")
        }
        val activeBackend = injectedBackend ?: HeuristicLayaBackend(config.actionTriggerEnabled)
        val source = if (injectedBackend == null) "heuristic" else "laya"
        val prediction = try {
            activeBackend.predict(text)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
        prediction?.toDecision(source)
            ?: HeuristicLayaBackend(config.actionTriggerEnabled).predict(text).toDecision("fallback")
    }

    private fun LayaPrediction.toDecision(source: String): RouteDecision {
        val confident = source != "laya" || confidence >= config.confidenceThreshold
        val safeBlocked = blocked || (intent == "unsafe")
        val effectiveIntent = if (!confident && source == "laya") "chat" else intent
        return RouteDecision(
            intent = effectiveIntent,
            needsSearch = confident && needsSearch,
            needsAction = config.actionTriggerEnabled && confident && needsAction,
            blocked = safeBlocked,
            confidence = confidence,
            source = source,
        )
    }
}
