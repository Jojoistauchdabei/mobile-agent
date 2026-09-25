package com.mobileagent.actions

import java.net.URI

enum class ActionKind {
    OPEN_APP,
    OPEN_URL,
    WEB_SEARCH,
    SET_TIMER,
}

data class ActionRequest(
    val kind: ActionKind,
    val target: String,
    val durationMs: Long? = null,
) {
    fun normalized(): ActionRequest = copy(
        kind = kind,
        target = target.trim(),
        durationMs = durationMs,
    )
}

sealed interface ActionExecutionResult {
    data class Success(val message: String) : ActionExecutionResult
    data class Rejected(val message: String) : ActionExecutionResult
    data class Failed(val message: String) : ActionExecutionResult
}

object ActionValidator {
    fun validate(action: ActionRequest): ActionValidation {
        val normalized = action.normalized()
        return when (normalized.kind) {
            ActionKind.OPEN_APP -> {
                if (normalized.target.length in 2..120) ActionValidation.Valid(normalized)
                else ActionValidation.Invalid("Ungültiges App-Ziel")
            }
            ActionKind.OPEN_URL -> {
                val uri = runCatching { URI(normalized.target) }.getOrNull()
                val scheme = uri?.scheme?.lowercase()
                if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) {
                    ActionValidation.Valid(normalized)
                } else {
                    ActionValidation.Invalid("Nur HTTPS- oder HTTP-Links sind erlaubt")
                }
            }
            ActionKind.WEB_SEARCH -> {
                if (normalized.target.length in 2..240) ActionValidation.Valid(normalized)
                else ActionValidation.Invalid("Ungültige Suchanfrage")
            }
            ActionKind.SET_TIMER -> {
                val duration = normalized.durationMs
                if (duration != null && duration in 1_000L..86_400_000L) ActionValidation.Valid(normalized)
                else ActionValidation.Invalid("Timer-Dauer muss zwischen 1 Sekunde und 24 Stunden liegen")
            }
        }
    }
}

sealed interface ActionValidation {
    data class Valid(val action: ActionRequest) : ActionValidation
    data class Invalid(val message: String) : ActionValidation
}
