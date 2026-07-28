package org.opensapien.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opensapien.app.ui.components.LevelMeter
import org.opensapien.app.ui.theme.CounterLargeStyle
import org.opensapien.core.recording.RecordingService
import java.util.concurrent.TimeUnit

/**
 * Home screen: one job — start and stop a recording, and show what the microphone is hearing.
 *
 * Everything on screen is live state. The elapsed clock, the meter, and the partial transcript
 * all come from the recording service, so the screen is never "pretending" to record.
 */
@Composable
fun RecordScreen(
    state: RecordingService.State,
    level: Float,
    modelReady: Boolean,
    onToggleRecord: () -> Unit,
    onOpenModels: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val recording = state as? RecordingService.State.Recording
    val isRecording = recording != null

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ElapsedClock(recording?.startedAt)

        Spacer(Modifier.height(24.dp))

        LevelMeter(level = level, active = isRecording)

        Spacer(Modifier.height(32.dp))

        RecordButton(
            isRecording = isRecording,
            enabled = modelReady || isRecording,
            onClick = onToggleRecord,
        )

        Spacer(Modifier.height(16.dp))

        when {
            !modelReady && !isRecording -> SetupNotice(onOpenModels)
            state is RecordingService.State.Error -> ErrorNotice(state.message)
            else -> StatusLine(isRecording)
        }

        Spacer(Modifier.height(24.dp))

        if (isRecording) LiveTranscript(recording.liveText)
    }
}

@Composable
private fun ElapsedClock(startedAt: Long?) {
    val elapsed = rememberElapsed(startedAt)
    Text(
        text = elapsed,
        style = CounterLargeStyle,
        color = if (startedAt != null) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = Modifier.semantics {
            contentDescription = if (startedAt != null) "Recording, $elapsed elapsed" else "Not recording"
            liveRegion = LiveRegionMode.Polite
        },
    )
}

@Composable
private fun RecordButton(isRecording: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .size(96.dp)
            .semantics {
                contentDescription = if (isRecording) "Stop recording" else "Start recording"
            },
        shape = androidx.compose.foundation.shape.CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRecording) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.primary
            },
        ),
    ) {
        Text(
            text = if (isRecording) "Stop" else "Record",
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun StatusLine(isRecording: Boolean) {
    Text(
        text = if (isRecording) "Listening — audio never leaves this phone" else "Ready",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun SetupNotice(onOpenModels: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "No speech model installed yet",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onOpenModels) { Text("Choose a model") }
    }
}

@Composable
private fun ErrorNotice(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive },
    )
}

@Composable
private fun LiveTranscript(text: String) {
    if (text.isBlank()) {
        Text(
            text = "Words will appear here as you speak.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** Formats elapsed time as m:ss (or h:mm:ss past an hour), ticking once a second. */
@Composable
private fun rememberElapsed(startedAt: Long?): String {
    val now = androidx.compose.runtime.remember(startedAt) {
        androidx.compose.runtime.mutableLongStateOf(System.currentTimeMillis())
    }
    androidx.compose.runtime.LaunchedEffect(startedAt) {
        if (startedAt == null) return@LaunchedEffect
        while (true) {
            now.longValue = System.currentTimeMillis()
            kotlinx.coroutines.delay(250)
        }
    }
    if (startedAt == null) return "0:00"
    val ms: Long = (now.longValue - startedAt).coerceAtLeast(0L)
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
