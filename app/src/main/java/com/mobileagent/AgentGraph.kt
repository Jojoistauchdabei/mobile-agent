package com.mobileagent

import android.content.Context
import com.mobileagent.actions.ActionExecutionResult
import com.mobileagent.actions.ActionRequest
import com.mobileagent.actions.AndroidActions
import com.mobileagent.llm.BonsaiLlm
import com.mobileagent.llm.LanguageModel
import com.mobileagent.llm.LlmRequest
import com.mobileagent.models.ModelManager
import com.mobileagent.router.LayaRouter
import com.mobileagent.search.DuckDuckGoSearch
import com.mobileagent.search.SearchClient
import com.mobileagent.search.SearchResult
import com.mobileagent.voice.AudioCapture
import com.mobileagent.voice.AudioCaptureResult
import com.mobileagent.voice.Pcm16Audio
import com.mobileagent.voice.SpeechToText
import com.mobileagent.voice.Speaker
import com.mobileagent.voice.SttResult
import com.mobileagent.voice.WhisperStt
import kotlinx.coroutines.CancellationException

class AgentGraph(
    val models: ModelManager,
    val audioCapture: AudioCapture,
    val stt: SpeechToText,
    val router: LayaRouter,
    val llm: LanguageModel,
    val search: SearchClient,
    val actions: AndroidActions,
    val speaker: Speaker,
) {
    companion object {
        fun create(context: Context): AgentGraph {
            val app = context.applicationContext
            val models = ModelManager(app)
            return AgentGraph(
                models = models,
                audioCapture = AudioCapture(app),
                stt = WhisperStt(app, models),
                router = LayaRouter(app),
                llm = BonsaiLlm(models),
                search = DuckDuckGoSearch(),
                actions = AndroidActions(app),
                speaker = Speaker(app),
            )
        }
    }

    suspend fun handleUtterance(audio: Pcm16Audio): AgentResult {
        val result = when (val sttResult = stt.transcribe(audio)) {
            is SttResult.Text -> handleText(sttResult.text)
            is SttResult.Unavailable -> unavailable(sttResult.reason)
            is SttResult.Failed -> unavailable(sttResult.message)
        }
        speaker.speak(result.reply)
        return result
    }

    suspend fun captureAndHandle(): AgentResult = when (val result = audioCapture.capture()) {
        is AudioCaptureResult.Captured -> handleUtterance(result.audio)
        is AudioCaptureResult.PermissionRequired -> unavailable("Mikrofon-Berechtigung fehlt")
        is AudioCaptureResult.NoSpeech -> unavailable("Keine Sprache erkannt")
        is AudioCaptureResult.Failed -> unavailable(result.message)
    }

    suspend fun handleText(text: String): AgentResult {
        val route = router.route(text)
        if (route.blocked) {
            return AgentResult(text, route, "Diese Anfrage wird nicht ausgeführt.")
        }
        val searchResults = if (route.needsSearch) {
            try {
                search.search(text, 5)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                return AgentResult(
                    transcript = text,
                    route = route,
                    reply = "Die Websuche ist gerade nicht verfügbar.",
                    error = error.message,
                )
            }
        } else {
            emptyList()
        }
        val output = llm.generate(
            LlmRequest(
                text = text,
                route = route,
                searchContext = searchResults.map { result ->
                    buildString {
                        append(result.title)
                        if (result.url != null) append(" | ${result.url}")
                        append(": ${result.snippet}")
                    }
                },
            ),
        )
        return AgentResult(
            transcript = text,
            route = route,
            reply = output.reply,
            searchResults = searchResults,
            pendingAction = output.action.takeIf { route.needsAction },
            error = output.error,
        )
    }

    fun executeApproved(action: ActionRequest): ActionExecutionResult =
        actions.execute(action, approved = true)

    private fun unavailable(message: String): AgentResult = AgentResult(
        transcript = "",
        route = RouteDecision("unavailable", false, false, false, 0.0, "runtime"),
        reply = message,
        error = message,
    )
}

data class AgentResult(
    val transcript: String,
    val route: RouteDecision,
    val reply: String,
    val searchResults: List<SearchResult> = emptyList(),
    val pendingAction: ActionRequest? = null,
    val executedActions: List<String> = emptyList(),
    val error: String? = null,
)
