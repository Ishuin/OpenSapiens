package org.opensapien.app.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.opensapien.core.transcription.ModelManager
import kotlin.math.roundToInt

/**
 * Model library: pick which speech model runs on this device, download it, or remove it.
 *
 * Each row states size and accuracy up front, because a 500 MB download over mobile data is a
 * decision the user should be able to make before tapping, not after.
 */
@Composable
fun ModelsScreen(
    models: List<ModelManager.AsrModel>,
    activeId: String,
    installedIds: Set<String>,
    downloadingId: String?,
    progress: Float,
    errorMessage: String?,
    isCapable: (ModelManager.AsrModel) -> Boolean,
    onSelect: (ModelManager.AsrModel) -> Unit,
    onDownload: (ModelManager.AsrModel) -> Unit,
    onDelete: (ModelManager.AsrModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = "Models run entirely on this phone. Download one over Wi-Fi, then " +
                    "transcription works with no network at all.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (errorMessage != null) {
            item {
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }

        items(models, key = { it.id }) { model ->
            ModelRow(
                model = model,
                isActive = model.id == activeId,
                isInstalled = model.id in installedIds,
                isDownloading = model.id == downloadingId,
                progress = progress,
                capable = isCapable(model),
                anyDownloadInFlight = downloadingId != null,
                onSelect = { onSelect(model) },
                onDownload = { onDownload(model) },
                onDelete = { onDelete(model) },
            )
        }
    }
}

@Composable
private fun ModelRow(
    model: ModelManager.AsrModel,
    isActive: Boolean,
    isInstalled: Boolean,
    isDownloading: Boolean,
    progress: Float,
    capable: Boolean,
    anyDownloadInFlight: Boolean,
    onSelect: () -> Unit,
    onDownload: () -> Unit,
    onDelete: () -> Unit,
) {
    val selectable = isInstalled && !isActive
    val label = buildString {
        append(model.displayName)
        append(", ${model.sizeMb} megabytes, ${model.languages}")
        if (isActive) append(", currently in use")
        if (!capable) append(", may run slowly on this device")
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (isActive) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            )
            .then(
                if (selectable) {
                    Modifier.clickable(role = Role.RadioButton, onClick = onSelect)
                } else {
                    Modifier
                }
            )
            .padding(16.dp)
            .semantics { contentDescription = label },
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(model.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "${model.sizeMb} MB",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = model.tagline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = model.languages,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!capable) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "This phone has limited memory — expect slower than real-time results.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(12.dp))

        when {
            isDownloading -> DownloadProgress(progress)

            !isInstalled -> TextButton(
                onClick = onDownload,
                enabled = !anyDownloadInFlight,
            ) { Text("Download") }

            else -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isActive) "In use" else "Installed — tap to use",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete) { Text("Delete") }
            }
        }
    }
}

@Composable
private fun DownloadProgress(progress: Float) {
    val pct = (progress.coerceIn(0f, 1f) * 100).roundToInt()
    Column(Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = "Downloading, $pct percent" },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Downloading… $pct%",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
