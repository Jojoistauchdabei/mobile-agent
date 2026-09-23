package com.mobileagent

import android.content.Context
import com.mobileagent.actions.AndroidActions
import com.mobileagent.llm.BonsaiLlm
import com.mobileagent.router.LayaRouter
import com.mobileagent.search.DuckDuckGoSearch
import com.mobileagent.voice.WhisperStt

/** Verdrahtet die lokale Pipeline: STT → Router → LLM → Tools → TTS. */
class AgentGraph(
    val stt: WhisperStt,
    val router: LayaRouter,
    val llm: BonsaiLlm,
    val search: DuckDuckGoSearch,
    val actions: AndroidActions,
) {
    companion object {
        fun create(context: Context): AgentGraph {
            val app = context.applicationContext
            return AgentGraph(
                stt = WhisperStt(app),
                router = LayaRouter(app),
                llm = BonsaiLlm(app),
                search = DuckDuckGoSearch(),
                actions = AndroidActions(app),
            )
        }
    }

    /** Ein Sprachbefehl, komplett lokal (Suche ist der einzige Netz-Call). */
    suspend fun handleUtterance(audioPcm16k: ShortArray): AgentResult {
        val text = stt.transcribe(audioPcm16k)
        val route = router.route(text)
        if (route.blocked) return AgentResult(text, route, "Ich helfe dabei nicht.", emptyList())

        val contextSnippets = if (route.needsSearch) search.search(text, 5) else emptyList()
        val llmOut = llm.generate(text, route, contextSnippets)

        val executed = if (route.needsAction) actions.execute(llmOut.action) else emptyList()
        return AgentResult(text, route, llmOut.reply, executed)
    }
}

data class AgentResult(
    val transcript: String,
    val route: RouteDecision,
    val reply: String,
    val executedActions: List<String>,
)
