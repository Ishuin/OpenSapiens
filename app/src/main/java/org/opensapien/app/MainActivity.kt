package org.opensapien.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.opensapien.app.ui.ArchiveScreen
import org.opensapien.app.ui.ModelsScreen
import org.opensapien.app.ui.RecordScreen
import org.opensapien.app.ui.TranscriptDetail
import org.opensapien.app.ui.theme.OpenSapienTheme
import org.opensapien.core.data.OpenSapienDb
import org.opensapien.core.data.Transcript
import org.opensapien.core.data.TranscriptFileStore
import org.opensapien.core.recording.RecordingService
import org.opensapien.core.transcription.ModelManager

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val autoRecord = intent.getBooleanExtra(EXTRA_AUTO_RECORD, false)
        setContent {
            OpenSapienTheme {
                OpenSapienApp(autoRecord = autoRecord)
            }
        }
    }

    companion object {
        const val EXTRA_AUTO_RECORD = "autoRecord"
    }
}

private enum class Tab(val label: String) { Record("Record"), Archive("Archive"), Models("Models") }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OpenSapienApp(autoRecord: Boolean) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val modelManager = remember { ModelManager(context) }
    val fileStore = remember { TranscriptFileStore(context) }
    val dao = remember { OpenSapienDb.get(context).transcripts() }

    var tab by remember { mutableStateOf(Tab.Record) }
    var openTranscript by remember { mutableStateOf<Transcript?>(null) }

    // ------------------------------------------------------------ recording
    val recState by RecordingService.state.collectAsState()
    val level by RecordingService.level.collectAsState()

    var micGranted by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        micGranted = granted
        if (granted) startRecording(context)
    }

    // ------------------------------------------------------------- models
    var modelsRevision by remember { mutableStateOf(0) }
    val installedIds = remember(modelsRevision) {
        modelManager.installedModels().map { it.id }.toSet()
    }
    var activeId by remember(modelsRevision) { mutableStateOf(modelManager.activeModelId) }
    var downloadingId by remember { mutableStateOf<String?>(null) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var modelError by remember { mutableStateOf<String?>(null) }
    val modelReady = activeId in installedIds

    // ------------------------------------------------------------- archive
    val transcripts by remember { dao.observeAll() }
        .collectAsState(initial = emptyList())

    LaunchedEffect(autoRecord) {
        if (autoRecord && micGranted && recState !is RecordingService.State.Recording) {
            startRecording(context)
        }
    }

    val detail = openTranscript
    if (detail != null) {
        val text = remember(detail.id) { fileStore.read(detail.fileName).orEmpty() }
        BackHandler { openTranscript = null }
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(detail.title) },
                    navigationIcon = {
                        IconButton(onClick = { openTranscript = null }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to archive",
                            )
                        }
                    },
                )
            },
        ) { pad ->
            TranscriptDetail(
                transcript = detail,
                text = text,
                onDelete = {
                    scope.launch {
                        fileStore.delete(detail.fileName)
                        dao.delete(detail.id)
                        openTranscript = null
                    }
                },
                modifier = Modifier.padding(pad),
            )
        }
        return
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(tab.label) }) },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { t ->
                    NavigationBarItem(
                        selected = tab == t,
                        onClick = { tab = t },
                        icon = {},
                        label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        val inner = Modifier.padding(pad)
        when (tab) {
            Tab.Record -> RecordScreen(
                state = recState,
                level = level,
                modelReady = modelReady,
                onToggleRecord = {
                    if (recState is RecordingService.State.Recording) {
                        stopRecording(context)
                    } else if (micGranted) {
                        startRecording(context)
                    } else {
                        micPermission.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onOpenModels = { tab = Tab.Models },
                modifier = inner,
            )

            Tab.Archive -> ArchiveScreen(
                transcripts = transcripts,
                onOpen = { openTranscript = it },
                modifier = inner,
            )

            Tab.Models -> ModelsScreen(
                models = ModelManager.CATALOG,
                activeId = activeId,
                installedIds = installedIds,
                downloadingId = downloadingId,
                progress = downloadProgress,
                errorMessage = modelError,
                isCapable = modelManager::isDeviceCapable,
                onSelect = {
                    modelManager.activeModelId = it.id
                    activeId = it.id
                },
                onDownload = { model ->
                    modelError = null
                    downloadingId = model.id
                    downloadProgress = 0f
                    scope.launch {
                        val result = modelManager.install(model) { downloadProgress = it }
                        downloadingId = null
                        result
                            .onSuccess {
                                modelManager.activeModelId = model.id
                                modelsRevision++
                                activeId = model.id
                            }
                            .onFailure { modelError = it.message ?: "Download failed" }
                    }
                },
                onDelete = { model ->
                    modelManager.delete(model)
                    modelsRevision++
                },
                modifier = inner,
            )
        }
    }
}

private fun startRecording(context: android.content.Context) {
    context.startForegroundService(
        Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_START),
    )
}

private fun stopRecording(context: android.content.Context) {
    context.startService(
        Intent(context, RecordingService::class.java).setAction(RecordingService.ACTION_STOP),
    )
}
