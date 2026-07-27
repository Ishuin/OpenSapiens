package org.opensapien.wear.tile

import androidx.wear.tiles.TileService

/**
 * One-tap record tile (swipe from watch face). Tapping launches
 * WearRecordingService toggle via a LoadAction → launch intent.
 *
 * TODO: implement onTileRequest with a single circular Record/Stop button
 * (tiles-material Button + Clickable LaunchAction to WearMainActivity, or
 * ActionBuilders.LoadAction + service toggle in onTileRequest).
 */
class RecordTileService : TileService()
