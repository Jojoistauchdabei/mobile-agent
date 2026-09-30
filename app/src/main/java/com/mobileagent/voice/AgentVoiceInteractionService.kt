package com.mobileagent.voice

import android.app.role.RoleManager
import android.content.Context
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService

/**
 * Registrierung als System-Assistent: das System bindet diesen Service, wenn die
 * Rolle per [RoleManager.ROLE_ASSISTANT] vergeben wurde (z. B. lange Doppeltaste
 * auf dem Home-Bildschirm).
 */
class AgentVoiceInteractionService : VoiceInteractionService() {
    override fun onReady() {
        super.onReady()
    }
}

/** Erzeugt die Session, die [AgentVoiceInteractionService] fuer den System bindet. */
class AgentVoiceInteractionSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession =
        AgentVoiceInteractionSession(this)
}

class AgentVoiceInteractionSession(context: Context) : VoiceInteractionSession(context) {

    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        val assistText = args?.getString(EXTRA_ASSIST_TEXT)?.trim().orEmpty()
        startAssistantActivity(
            android.content.Intent(context, com.mobileagent.MainActivity::class.java).apply {
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                if (assistText.isNotEmpty()) {
                    putExtra(EXTRA_ASSIST_TEXT, assistText)
                } else {
                    putExtra(EXTRA_AUTO_START_MIC, true)
                }
            },
        )
    }

    companion object {
        const val EXTRA_ASSIST_TEXT = "android.intent.extra.ASSIST_TEXT"
        const val EXTRA_AUTO_START_MIC = "com.mobileagent.EXTRA_AUTO_START_MIC"
    }
}
