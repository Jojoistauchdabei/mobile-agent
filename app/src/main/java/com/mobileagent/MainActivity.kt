package com.mobileagent

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.mobileagent.actions.ActionExecutionResult
import com.mobileagent.actions.ActionRequest
import com.mobileagent.models.DefaultModels
import com.mobileagent.models.ModelDownloadContract
import com.mobileagent.models.ModelDownloadNotifications
import com.mobileagent.models.ModelDownloadScheduler
import com.mobileagent.models.ModelInfo
import com.mobileagent.models.ModelSpec
import com.mobileagent.voice.AssistantRole
import com.mobileagent.voice.AgentVoiceInteractionSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = (application as AgentApp).agentGraph
        val assistText = intent?.getStringExtra(AgentVoiceInteractionSession.EXTRA_ASSIST_TEXT)
        val autoStartMic = intent?.getBooleanExtra(AgentVoiceInteractionSession.EXTRA_AUTO_START_MIC, false) == true
        setContent { AgentScreen(graph, assistText, autoStartMic) }
    }
}

@Composable
fun AgentScreen(
    graph: AgentGraph,
    assistText: String? = null,
    autoStartMic: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf("Bereit") }
    var transcript by remember { mutableStateOf("") }
    var reply by remember { mutableStateOf("") }
    var route by remember { mutableStateOf<RouteDecision?>(null) }
    var sources by remember { mutableStateOf<List<com.mobileagent.search.SearchResult>>(emptyList()) }
    var pendingAction by remember { mutableStateOf<ActionRequest?>(null) }
    var actionMessage by remember { mutableStateOf("") }
    var modelInfos by remember { mutableStateOf<List<ModelInfo>>(emptyList()) }
    var downloadHint by remember { mutableStateOf("") }
    var pendingDownload by remember { mutableStateOf<ModelSpec?>(null) }
    var layaEnabled by remember { mutableStateOf(graph.router.config.enabled) }
    var actionTriggersEnabled by remember { mutableStateOf(graph.router.config.actionTriggerEnabled) }
    var captureJob by remember { mutableStateOf<Job?>(null) }

    suspend fun runCapture() {
        status = "Aufnahme läuft …"
        val result = graph.captureAndHandle()
        status = if (result.error == null) "Bereit" else "Hinweis"
        transcript = result.transcript
        reply = result.reply
        route = result.route
        sources = result.searchResults
        pendingAction = result.pendingAction
    }

    fun launchCapture() {
        if (captureJob?.isActive == true) return
        captureJob = scope.launch {
            try {
                runCapture()
            } finally {
                captureJob = null
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            launchCapture()
        } else {
            status = "Mikrofon-Berechtigung fehlt"
        }
    }

    suspend fun runText(text: String) {
        status = "Anfrage läuft …"
        val result = graph.handleText(text)
        status = if (result.error == null) "Bereit" else "Hinweis"
        transcript = result.transcript
        reply = result.reply
        route = result.route
        sources = result.searchResults
        pendingAction = result.pendingAction
    }

    LaunchedEffect(Unit) {
        modelInfos = DefaultModels.all.map { graph.models.inspect(it) }
    }

    fun startCapture() {
        if (graph.audioCapture.hasPermission()) {
            launchCapture()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(assistText) {
        val text = assistText?.trim().orEmpty()
        if (text.isNotEmpty()) runText(text)
    }

    LaunchedEffect(autoStartMic) {
        if (autoStartMic) startCapture()
    }

    fun enqueueDownload(spec: ModelSpec) {
        downloadHint = ""
        ModelDownloadScheduler.enqueue(context, spec)
    }

    val downloadPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        pendingDownload?.let(::enqueueDownload)
        pendingDownload = null
    }

    fun downloadModel(spec: ModelSpec) {
        if (spec.downloadUrl == null) {
            downloadHint = "${spec.displayName}: Datei muss separat als ONNX exportiert werden"
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            pendingDownload = spec
            downloadPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            enqueueDownload(spec)
        }
    }

    fun cancelDownload(spec: ModelSpec) {
        ModelDownloadScheduler.cancel(context, spec)
    }

    var isAssistant by remember { mutableStateOf(AssistantRole.isHeld(context)) }
    val assistantRoleAvailable = remember { AssistantRole.isAvailable(context) }

    val assistantRoleLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) {
        isAssistant = AssistantRole.isHeld(context)
    }

    fun requestAssistantRole() {
        AssistantRole.requestIntent(context)?.let(assistantRoleLauncher::launch)
    }

    // Rolle kann sich auch ausserhalb der App aendern (Einstellungen/Systemdialog).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isAssistant = AssistantRole.isHeld(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun refreshModels() {
        scope.launch { modelInfos = DefaultModels.all.map { graph.models.inspect(it) } }
    }

    fun executeAction(action: ActionRequest) {
        val result: ActionExecutionResult = graph.executeApproved(action)
        actionMessage = when (result) {
            is ActionExecutionResult.Success -> result.message
            is ActionExecutionResult.Rejected -> result.message
            is ActionExecutionResult.Failed -> result.message
        }
        pendingAction = null
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingAction?.let(::executeAction)
        } else {
            actionMessage = "Benachrichtigungen wurden nicht freigegeben"
            pendingAction = null
        }
    }

    fun confirmAction(action: ActionRequest) {
        val needsNotificationPermission = action.kind == com.mobileagent.actions.ActionKind.SET_TIMER &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsNotificationPermission) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            executeAction(action)
        }
    }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Mobile Agent", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Lokal: Whisper → Laya (optional) → Bonsai → Tools", style = MaterialTheme.typography.bodyMedium)
                Text(status, style = MaterialTheme.typography.labelLarge)
                Button(
                    onClick = ::startCapture,
                    enabled = captureJob?.isActive != true,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (status == "Aufnahme läuft …") "Aufnahme läuft" else "Sprechen")
                }
                if (transcript.isNotBlank()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Transkript", style = MaterialTheme.typography.labelMedium)
                            Text(transcript)
                        }
                    }
                }
                if (reply.isNotBlank()) {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Antwort", style = MaterialTheme.typography.labelMedium)
                            Text(reply)
                            route?.let {
                                Text(
                                    "Route: ${it.intent} · ${it.source} · ${(it.confidence * 100).toInt()} %",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
                if (sources.isNotEmpty()) {
                    Text("Webquellen", style = MaterialTheme.typography.titleMedium)
                    sources.forEach { source ->
                        Text("• ${source.title}: ${source.snippet}", style = MaterialTheme.typography.bodySmall)
                        source.url?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    }
                }
                if (actionMessage.isNotBlank()) Text(actionMessage, style = MaterialTheme.typography.bodyMedium)
                Text("Router", style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Laya als Action-Trigger")
                    Switch(
                        checked = layaEnabled,
                        onCheckedChange = {
                            layaEnabled = it
                            graph.router.config = graph.router.config.copy(enabled = it)
                        },
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Laya darf Aktionen triggern")
                    Switch(
                        checked = actionTriggersEnabled,
                        onCheckedChange = {
                            actionTriggersEnabled = it
                            graph.router.config = graph.router.config.copy(actionTriggerEnabled = it)
                        },
                    )
                }
                Text(
                    "Ohne exportiertes Laya-ONNX nutzt die App den Heuristik-Fallback. Aktionen bleiben trotzdem bestätigungspflichtig.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text("Laya", style = MaterialTheme.typography.titleMedium)
                modelInfos.forEach { info ->
                    DownloadModelRow(
                        info = info,
                        onDownload = ::downloadModel,
                        onCancel = ::cancelDownload,
                        onSucceeded = ::refreshModels,
                    )
                }
                Text(
                    "Downloads laufen im Hintergrund weiter und melden sich per Benachrichtigung.",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (downloadHint.isNotBlank()) {
                    Text(downloadHint, style = MaterialTheme.typography.bodySmall)
                }
                Text("System-Assistent", style = MaterialTheme.typography.titleMedium)
                AssistantRoleRow(
                    isAssistant = isAssistant,
                    available = assistantRoleAvailable,
                    onRequest = ::requestAssistantRole,
                )
                OutlinedButton(
                    onClick = {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Android-Aktionen / Accessibility öffnen")
                }
                Text(
                    "Websuche und Sprache werden nur nach deiner Eingabe verwendet. Aktionen werden vor der Ausführung bestätigt.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    pendingAction?.let { action ->
        AlertDialog(
            onDismissRequest = { pendingAction = null },
            title = { Text("Aktion bestätigen") },
            text = {
                val duration = action.durationMs?.let { " (${it / 1000} Sekunden)" }.orEmpty()
                Text("${action.kind.name}: ${action.target}$duration")
            },
            confirmButton = { Button(onClick = { confirmAction(action) }) { Text("Ausführen") } },
            dismissButton = { TextButton(onClick = { pendingAction = null }) { Text("Abbrechen") } },
        )
    }
}

@Composable
private fun AssistantRoleRow(
    isAssistant: Boolean,
    available: Boolean,
    onRequest: () -> Unit,
) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (isAssistant) "Mobile Agent ist dein Assistent" else "Als Assistent einrichten",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!isAssistant && available) {
                Button(onClick = onRequest) { Text("Einrichten") }
            }
        }
        Text(
            when {
                isAssistant -> "Lange Doppeltaste auf dem Home-Bildschirm öffnet den Assistenten und startet die Aufnahme. Rolle lässt sich in den Android-Einstellungen unter Apps & Benachrichtigungen &gt; Standard-Apps &gt; Assistenten-App wechseln."
                available -> "Danach startet die lange Doppeltaste auf dem Home-Bildschirm die Sprachaufnahme."
                else -> "Dieses Gerät unterstützt keine Assistenten-Rolle."
            },
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun DownloadModelRow(
    info: ModelInfo,
    onDownload: (ModelSpec) -> Unit,
    onCancel: (ModelSpec) -> Unit,
    onSucceeded: () -> Unit,
) {
    val context = LocalContext.current
    val tag = remember(info.spec.id) { ModelDownloadContract.tagFor(info.spec.id) }
    val workInfos by remember(tag) {
        WorkManager.getInstance(context).getWorkInfosByTagFlow(tag)
    }.collectAsState(initial = null)
    val active = workInfos?.firstOrNull { !it.state.isFinished }
    val failed = workInfos?.any { it.state == WorkInfo.State.FAILED } == true
    val succeeded = workInfos?.any { it.state == WorkInfo.State.SUCCEEDED } == true
    LaunchedEffect(succeeded) {
        if (succeeded) onSucceeded()
    }
    val progress = active?.progress?.let { data ->
        data.getLong(ModelDownloadContract.PROGRESS_DOWNLOADED, 0) to
            data.getLong(ModelDownloadContract.PROGRESS_TOTAL, -1)
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(info.spec.displayName, style = MaterialTheme.typography.bodyMedium)
                Text("${info.status.name} · ${info.file.name}", style = MaterialTheme.typography.bodySmall)
            }
            when {
                active != null -> OutlinedButton(onClick = { onCancel(info.spec) }) { Text("Stopp") }
                info.spec.downloadUrl != null && info.status != com.mobileagent.models.ModelStatus.READY ->
                    OutlinedButton(onClick = { onDownload(info.spec) }) { Text("Laden") }
            }
        }
        if (active != null && progress != null) {
            val (downloaded, total) = progress
            if (total > 0) {
                LinearProgressIndicator(
                    progress = { downloaded.toFloat() / total },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Text(
                ModelDownloadNotifications.progressText(downloaded, total),
                style = MaterialTheme.typography.bodySmall,
            )
        } else if (failed) {
            Text("Download fehlgeschlagen – erneut versuchen", style = MaterialTheme.typography.bodySmall)
        }
    }
}
