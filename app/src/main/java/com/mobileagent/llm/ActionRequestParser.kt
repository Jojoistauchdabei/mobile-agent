package com.mobileagent.llm

import com.mobileagent.actions.ActionKind
import com.mobileagent.actions.ActionRequest
import org.json.JSONObject

object ActionRequestParser {
    fun parse(text: String): ActionRequest? {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val json = runCatching { JSONObject(text.substring(start, end + 1)) }.getOrNull() ?: return null
        val name = json.optString("name", json.optString("action")).uppercase()
        val kind = when (name) {
            "OPEN_APP" -> ActionKind.OPEN_APP
            "OPEN_URL" -> ActionKind.OPEN_URL
            "WEB_SEARCH" -> ActionKind.WEB_SEARCH
            "SET_TIMER" -> ActionKind.SET_TIMER
            else -> return null
        }
        val target = json.optString("target", json.optString("query", json.optString("url")))
        if (target.isBlank()) return null
        return ActionRequest(kind, target, json.optLong("durationMs", 0L).takeIf { it > 0 })
    }
}
