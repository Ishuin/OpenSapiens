package org.opensapien.wear

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

/** Watch palette — mirrors the phone app's field-recorder identity on a black OLED ground. */
private val Ground = Color(0xFF000000)
private val Signal = Color(0xFFE0483C)
private val Ember = Color(0xFFE8853A)
private val OnGround = Color(0xFFF2F2EF)
private val Muted = Color(0xFF9A9AA2)
private val Outline = Color(0xFF2C2C31)

class WearMainActivity : ComponentActivity() {

    private var onPermissionResult: ((Boolean) -> Unit)? = null

    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onPermissionResult?.invoke(granted)
            onPermissionResult = null
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
                val queued by WearRecordingService.queued.collectAsState()
                var micDenied by remember { mutableStateOf(false) }

                Scaffold(timeText = { TimeText() }) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Ground),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            RecordControl(recording = recording) {
                                micDenied = false
                                withMicPermission(
                                    onGranted = { toggleRecording() },
                                    onDenied = { micDenied = true },
                                )
                            }

                            Text(
                                text = if (recording) "Recording" else "Record",
                                color = if (recording) Signal else OnGround,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(top = 12.dp),
                            )

                            Text(
                                text = when {
                                    micDenied -> "Microphone access needed"
                                    recording -> "Audio stays on this watch until it reaches your phone"
                                    queued > 0 -> pendingLabel(queued)
                                    else -> "Transcribed on your phone"
                                },
                                color = when {
                                    micDenied -> Signal
                                    queued > 0 && !recording -> Ember
                                    else -> Muted
                                },
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Runs [onGranted] immediately when the mic is already granted, otherwise asks once and
     * reports the user's answer — a tap never silently does nothing.
     */
    private fun withMicPermission(onGranted: () -> Unit, onDenied: () -> Unit) {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            onGranted()
            return
        }
        onPermissionResult = { result -> if (result) onGranted() else onDenied() }
        micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun toggleRecording() {
        startForegroundService(
            Intent(this, WearRecordingService::class.java)
                .setAction(WearRecordingService.ACTION_TOGGLE),
        )
    }
}

private fun pendingLabel(count: Int): String =
    if (count == 1) "1 clip waiting for your phone" else "$count clips waiting for your phone"

/**
 * The single control on the watch: a lens-like ring whose core is a dot at rest and a square
 * while recording, so state reads at a glance without relying on colour alone.
 */
@Composable
private fun RecordControl(recording: Boolean, onClick: () -> Unit) {
    val coreSize by animateDpAsState(
        targetValue = if (recording) 30.dp else 56.dp,
        animationSpec = tween(220),
        label = "coreSize",
    )
    val coreRadius by animateDpAsState(
        targetValue = if (recording) 8.dp else 28.dp,
        animationSpec = tween(220),
        label = "coreRadius",
    )
    val ringAlpha by animateFloatAsState(
        targetValue = if (recording) 1f else 0.55f,
        animationSpec = tween(220),
        label = "ringAlpha",
    )

    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(CircleShape)
            .border(2.dp, (if (recording) Signal else Outline).copy(alpha = ringAlpha), CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = if (recording) "Stop recording" else "Start recording"
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(coreSize)
                .clip(RoundedCornerShape(coreRadius))
                .background(Signal),
        )
    }
}
