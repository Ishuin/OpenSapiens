package org.opensapien.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var modelsChanged by remember { mutableStateOf(0) }
    val modelReady = remember(modelsChanged) { modelManager.isInstalled }
    var showModels by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("open_sapien") },
                actions = {
                    if (modelReady) {
                        TextButton(onClick = { showModels = !showModels }) {
                            Text(if (showModels) "Done" else "Models")
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (modelReady && !showModels) {
                FloatingActionButton(onClick = onRecordClick) {
                    Text(if (recState is RecordingService.State.Recording) "⏹" else "●")
                }
            }
        },
    ) { padding ->
        val live = recState
        Column(Modifier.padding(padding)) {
            if (!modelReady || showModels) {
                if (!modelReady) {
                    Text(
                        "One-time setup: pick an offline speech model to download. " +
                            "After this, transcription never touches the network.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                ModelPicker(modelManager) { modelsChanged++ }
            } else {
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
}

/**
 * Model catalog: shows each model's size + quality up front so the user can
 * choose before downloading. Installed models can be switched to or deleted
 * (and re-downloaded later).
 */
@Composable
fun ModelPicker(modelManager: ModelManager, onChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var tick by remember { mutableStateOf(0) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    fun changed() {
        tick++
        onChanged()
    }

    Column(Modifier.verticalScroll(rememberScrollState())) {
        ModelManager.CATALOG.forEach { model ->
            // `tick` forces re-read of install/active state after actions.
            val installed = remember(tick) { modelManager.isInstalled(model) }
            val active = remember(tick) { modelManager.activeModel.id == model.id }
            val sizeLabel =
                if (model.sizeMb >= 1000) "%.1f GB".format(model.sizeMb / 1000f)
                else "${model.sizeMb} MB"

            ListItem(
                headlineContent = { Text("${model.displayName}  ·  $sizeLabel") },
                overlineContent = {
                    Text(
                        when {
                            active && installed -> "ACTIVE"
                            installed -> "Downloaded"
                            else -> "Not downloaded"
                        },
                    )
                },
                supportingContent = {
                    Column {
                        Text(model.quality, style = MaterialTheme.typography.bodySmall)
                        if (downloadingId == model.id) {
                            LinearProgressIndicator(
                                progress = { progress / 100f },
                                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            )
                            Text("$progress%", style = MaterialTheme.typography.labelMedium)
                        } else {
                            Row {
                                if (!installed) {
                                    Button(
                                        enabled = downloadingId == null,
                                        onClick = {
                                            error = null
                                            progress = 0
                                            downloadingId = model.id
                                            scope.launch {
                                                runCatching {
                                                    modelManager.install(model) { p -> progress = p }
                                                }.onSuccess {
                                                    modelManager.activeModel = model
                                                }.onFailure {
                                                    error = it.message ?: "download failed"
                                                }
                                                downloadingId = null
                                                changed()
                                            }
                                        },
                                    ) { Text("Download") }
                                } else {
                                    if (!active) {
                                        Button(onClick = {
                                            modelManager.activeModel = model
                                            changed()
                                        }) { Text("Use") }
                                    }
                                    TextButton(
                                        enabled = downloadingId == null,
                                        onClick = {
                                            modelManager.delete(model)
                                            changed()
                                        },
                                    ) { Text("Delete") }
                                }
                            }
                        }
                    }
                },
            )
            HorizontalDivider()
        }
        error?.let {
            Text(
                it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
}
