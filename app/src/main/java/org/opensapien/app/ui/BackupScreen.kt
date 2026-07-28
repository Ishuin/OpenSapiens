package org.opensapien.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Backup: mirror finished transcripts into a folder the user picks.
 *
 * There is no account to sign into and no proprietary cloud SDK. The system
 * folder picker already lists every provider installed on the phone — Google
 * Drive, OneDrive, an SD card, a NAS — so "connect Drive" is really "point at
 * the Drive folder you already have". Audio never leaves the device; only the
 * text does, and only after the user has chosen a destination.
 */
@Composable
fun BackupScreen(
    folderLabel: String?,
    unmeteredOnly: Boolean,
    lastSyncLabel: String?,
    onChooseFolder: () -> Unit,
    onUnlink: () -> Unit,
    onUnmeteredChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val linked = folderLabel != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text(
            text = "Recordings are deleted the moment they become text. " +
                "Backup copies that text — and nothing else — into a folder you choose.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Text("Destination", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            text = folderLabel ?: "No folder chosen",
            style = MaterialTheme.typography.bodyLarge,
            color = if (linked) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Pick your Google Drive folder here to sync with Drive — it appears in " +
                "the picker on any phone with the Drive app installed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = onChooseFolder) {
                Text(if (linked) "Change folder" else "Choose folder")
            }
            if (linked) {
                TextButton(onClick = onUnlink) { Text("Unlink") }
            }
        }

        Spacer(Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Only on Wi-Fi", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Wait for an unmetered network before uploading.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = unmeteredOnly, onCheckedChange = onUnmeteredChange)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSyncNow,
            enabled = linked,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Back up now")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = lastSyncLabel
                ?: if (linked) {
                    "New transcripts back up on their own every few hours."
                } else {
                    "Choose a folder to turn backup on."
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
