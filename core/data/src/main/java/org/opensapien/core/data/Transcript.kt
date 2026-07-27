package org.opensapien.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room index row for a transcript. The plain-text file under `open_sapien/`
 * is the source of truth; this row exists for fast listing/search and sync state.
 */
@Entity(tableName = "transcripts")
data class Transcript(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** File name (timestamp-based, e.g. 2026-07-27_09-14-33.md) relative to open_sapien/. */
    val fileName: String,
    val title: String,
    /** Epoch millis when recording started. */
    val createdAt: Long,
    /** Recording duration in millis. */
    val durationMs: Long,
    /** First ~200 chars of text for list previews. */
    val preview: String,
    /** Where the capture came from. */
    val source: Source = Source.PHONE,
    val syncState: SyncState = SyncState.PENDING,
    /** Drive file id once uploaded; null until then. */
    val driveFileId: String? = null,
) {
    enum class Source { PHONE, WIDGET, WEAR }
    enum class SyncState { PENDING, SYNCED, FAILED, LOCAL_ONLY }
}
