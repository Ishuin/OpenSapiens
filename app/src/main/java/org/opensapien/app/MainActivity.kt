package org.opensapien.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import org.opensapien.core.data.OpenSapienDb
import org.opensapien.core.recording.RecordingService
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(onRecordClick: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { OpenSapienDb.get(context).transcripts() }
    val transcripts by dao.observeAll().collectAsState(initial = emptyList())
    val recState by RecordingService.state.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("open_sapien") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onRecordClick) {
                Text(if (recState is RecordingService.State.Recording) "⏹" else "●")
            }
        },
    ) { padding ->
        val live = recState
        Column(Modifier.padding(padding)) {
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
                            Text(DateFormat.getDateTimeInstance().format(Date(t.createdAt)))
                        },
                    )
                }
            }
        }
    }
}