package com.mobileagent.actions

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import androidx.core.content.ContextCompat
import android.app.SearchManager
import com.mobileagent.timer.TimerReceiver

class AndroidActions(private val context: Context) {
    fun execute(action: ActionRequest, approved: Boolean): ActionExecutionResult {
        if (!approved) return ActionExecutionResult.Rejected("Aktion wurde nicht bestätigt")
        return when (val validation = ActionValidator.validate(action)) {
            is ActionValidation.Invalid -> ActionExecutionResult.Rejected(validation.message)
            is ActionValidation.Valid -> executeValidated(validation.action)
        }
    }

    private fun executeValidated(action: ActionRequest): ActionExecutionResult = runCatching {
        when (action.kind) {
            ActionKind.OPEN_APP -> openApp(action.target)
            ActionKind.OPEN_URL -> openUrl(action.target)
            ActionKind.WEB_SEARCH -> webSearch(action.target)
            ActionKind.SET_TIMER -> setTimer(action.durationMs ?: 0L)
        }
    }.fold(
        onSuccess = { result -> result },
        onFailure = { error -> ActionExecutionResult.Failed(error.message ?: "Aktion fehlgeschlagen") },
    )

    private fun openApp(target: String): ActionExecutionResult {
        val packageManager = context.packageManager
        val intent = packageManager.getLaunchIntentForPackage(target)
            ?: findAppByLabel(packageManager, target)
            ?: return ActionExecutionResult.Failed("Keine App für '$target' gefunden")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ActionExecutionResult.Success("App geöffnet: $target")
    }

    private fun findAppByLabel(packageManager: PackageManager, target: String): Intent? {
        val normalized = target.trim().lowercase()
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcherIntent, 0)
            .asSequence()
            .firstOrNull { info ->
                info.loadLabel(packageManager).toString().lowercase() == normalized
            }
            ?.activityInfo
            ?.let { packageManager.getLaunchIntentForPackage(it.packageName) }
    }

    private fun openUrl(target: String): ActionExecutionResult {
        val uri = Uri.parse(target)
        context.startActivity(
            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return ActionExecutionResult.Success("Link geöffnet")
    }

    private fun webSearch(query: String): ActionExecutionResult {
        val intent = Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, query)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        return ActionExecutionResult.Success("Websuche gestartet")
    }

    private fun setTimer(durationMs: Long): ActionExecutionResult {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return ActionExecutionResult.Rejected("Benachrichtigungen müssen für Timer freigegeben werden")
        }
        val intent = Intent(context, TimerReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            durationMs.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.setAndAllowWhileIdle(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + durationMs,
            pendingIntent,
        )
        val minutes = durationMs / 60_000.0
        return ActionExecutionResult.Success("Timer für %.1f Minuten gestellt".format(minutes))
    }
}

class AgentAccessibilityService : android.accessibilityservice.AccessibilityService() {
    override fun onAccessibilityEvent(event: android.view.accessibility.AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
}
