package org.opensapien.wear

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

class WearMainActivity : ComponentActivity() {

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) toggleRecording()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Opportunistic flush of any audio queued while the phone was unreachable.
        runCatching {
            startService(
                Intent(this, WearRecordingService::class.java)
                    .setAction(WearRecordingService.ACTION_FLUSH),
            )
        }
        setContent {
            MaterialTheme {
                val recording by WearRecordingService.isRecording.collectAsState()
                Scaffold(timeText = { TimeText() }) {
                    Button(
                        onClick = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Text(if (recording) "⏹ Stop" else "● Record")
                    }
                }
            }
        }
    }

    private fun toggleRecording() {
        startForegroundService(
            Intent(this, WearRecordingService::class.java)
                .setAction(WearRecordingService.ACTION_TOGGLE),
        )
    }
}
