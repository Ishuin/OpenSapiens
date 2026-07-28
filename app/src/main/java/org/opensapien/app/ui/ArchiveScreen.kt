package org.opensapien.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.opensapien.app.ui.theme.CounterStyle
import org.opensapien.core.data.Transcript
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Archive: every transcript this device has captured, newest first.
 *
 * The list is deliberately text-forward — a transcript is words, so the preview shows words
 * rather than a card full of chrome.
 */
@Composable
fun ArchiveScreen(
    transcripts: List<Transcript>,
    onOpen: (Transcript) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (transcripts.isEmpty()) {
        EmptyArchive(modifier)
        return
    }

    LazyColumn(modifier.fillMaxSize()) {
        items(transcripts, key = { it.id }) { t ->
            TranscriptRow(t, onClick = { onOpen(t) })
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        }
    }
}

@Composable
private fun TranscriptRow(t: Transcript, onClick: () -> Unit) {
    val date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
        .format(Date(t.createdAt))
    val duration = formatDuration(t.durationMs)

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .semantics {
                contentDescription = "${t.title}, recorded $date, $duration long"
            },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = t.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = duration,
                style = CounterStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = date + sourceSuffix(t.source),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (t.preview.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = t.preview,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyArchive(modifier: Modifier) {
    Column(
        modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Nothing recorded yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Recordings you make will be listed here. Audio is transcribed and then " +
                "deleted — only the text is kept.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Full transcript view with the option to remove it from the device. */
@Composable
fun TranscriptDetail(
    transcript: Transcript,
    text: String,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            text = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.SHORT)
                .format(Date(transcript.createdAt)) + sourceSuffix(transcript.source),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(16.dp))

        Text(
            text = text.ifBlank { "This transcript is empty." },
            style = MaterialTheme.typography.bodyLarge,
        )

        Spacer(Modifier.height(32.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onDelete) {
            Text("Delete transcript", color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun sourceSuffix(source: Transcript.Source): String = when (source) {
    Transcript.Source.PHONE -> ""
    Transcript.Source.WIDGET -> " · from widget"
    Transcript.Source.WEAR -> " · from watch"
}

private fun formatDuration(ms: Long): String {
    val h = TimeUnit.MILLISECONDS.toHours(ms)
    val m = TimeUnit.MILLISECONDS.toMinutes(ms) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(ms) % 60
    return if (h > 0) String.format("%d:%02d:%02d", h, m, s) else String.format("%d:%02d", m, s)
}
