package com.mobileagent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AgentScreen() }
    }
}

@Composable
fun AgentScreen() {
    var log by remember { mutableStateOf("Bereit. Modelle werden beim ersten Start geladen.\n") }
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Mobile Agent (lokal)", style = MaterialTheme.typography.headlineSmall)
                Text("Whisper → Laya-Router → Bonsai-1.7B → Tools", style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { log += "TODO: Mic starten (RECORD_AUDIO erfragen).\n" }) {
                    Text("Sprechen")
                }
                Text(log, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
