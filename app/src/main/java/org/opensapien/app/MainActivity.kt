package org.opensapien.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.opensapien.core.data.OpenSapienDb
import org.opensapien.core.recording.RecordingService
import org.opensapien.core.transcription.ModelManager
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) RecordingService.toggle(this, "PHONE")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                HomeScreen(onRecordClick = {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                })
            }
        }
        // Widget fallback path: user tapped Record on the widget but direct
        // service start wasn't possible — start via the foreground activity.
        if (intent.getBooleanExtra(EXTRA_AUTO_RECORD, false) &&
            ModelManager(this).isInstalled &&
            RecordingService.state.value !is RecordingService.State.Recording
        ) {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    companion object {
        const val EXTRA_AUTO_RECORD = "autoRecord"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onRecordClick: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { OpenSapienDb.get(context).transcripts() }
    val transcripts by dao.observeAll().collectAsState(initial = emptyList())
    val recState by RecordingService.state.collectAsState()

    val modelManager = remember { ModelManager(context) }
    var modelReady by remember { mutableStateOf(modelManager.isInstalled) }
    var downloadProgress by remember { mutableStateOf<Int?>(null) }
    var downloadError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { TopAppBar(title = { Text("open_sapien") }) },
        floatingActionButton = {
            if (modelReady) {
                FloatingActionButton(onClick = onRecordClick) {
                    Text(if (recState is RecordingService.State.Recording) "⏹" else "●")
                }
            }
        },
    ) { padding ->
        val live = recState
        Column(Modifier.padding(padding)) {
            if (!modelReady) {
                val progress = downloadProgress
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "One-time setup: download the offline speech model " +
                            "(~${ModelManager.MODEL_SIZE_MB} MB). " +
                            "After this, transcription never touches the network.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (progress == null) {
                        Button(
                            onClick = {
                                downloadError = null
                                downloadProgress = 0
                                scope.launch {
                                    runCatching { modelManager.install { p -> downloadProgress = p } }
                                        .onSuccess { modelReady = true }
                                        .onFailure {
                                            downloadProgress = null
                                            downloadError = it.message ?: "download failed"
                                        }
                                }
                            },
                            modifier = Modifier.padding(top = 12.dp),
                        ) { Text("Download model") }
                    } else {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        )
                        Text("$progress%", style = MaterialTheme.typography.labelMedium)
                    }
                    downloadError?.let {
                        Text(
                            it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
            if (live is RecordingService.State.Error) {
                Text(
                    text = live.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            if (live is RecordingService.State.Recording) {
                Text(
                    text = live.liveText.ifBlank { "Listening…" },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Top,
            ) {
                items(transcripts, key = { it.id }) { t ->
                    ListItem(
                        headlineContent = { Text(t.title) },
                        supportingContent = { Text(t.preview, maxLines = 2) },
                        overlineContent = {
                            Text(
                                DateFormat.getDateTimeInstance().format(Date(t.createdAt)) +
                                    "  ·  " + t.source.name,
                            )
                        },
                    )
                }
            }
        }
    }
}
