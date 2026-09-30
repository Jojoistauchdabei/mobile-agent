package com.mobileagent.voice

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

/** Registrierung als System-Assistent (Holding- oder Home-Button, Sprachbefehle). */
object AssistantRole {
    fun isAvailable(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)
            ?.isRoleAvailable(RoleManager.ROLE_ASSISTANT) == true

    fun isHeld(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java)
            ?.isRoleHeld(RoleManager.ROLE_ASSISTANT) == true

    fun requestIntent(context: Context): Intent? =
        context.getSystemService(RoleManager::class.java)
            ?.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT)
}
