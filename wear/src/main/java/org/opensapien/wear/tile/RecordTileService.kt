package org.opensapien.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import org.opensapien.wear.WearRecordingService

/**
 * One-tap record tile (swipe from the watch face). Tapping launches WearMainActivity,
 * which starts or stops WearRecordingService. The tile label reflects the live capture
 * state, so it never invites a second tap for something already running.
 */
class RecordTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val clickable = ModifiersBuilders.Clickable.Builder()
            .setId("record")
            .setOnClick(
                ActionBuilders.LaunchAction.Builder()
                    .setAndroidActivity(
                        ActionBuilders.AndroidActivity.Builder()
                            .setPackageName("org.opensapien.wear")
                            .setClassName("org.opensapien.wear.WearMainActivity")
                            .build()
                    )
                    .build()
            )
            .build()

        val recording = WearRecordingService.isRecording.value
        val queued = WearRecordingService.queued.value

        val hint = when {
            recording -> "Tap to stop"
            queued == 1 -> "1 clip waiting"
            queued > 1 -> "$queued clips waiting"
            else -> "Transcribed on phone"
        }

        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(text("OPEN SAPIEN", 11f, MUTED))
            .addContent(text(if (recording) "Recording" else "Record", 22f, if (recording) SIGNAL else ON_GROUND))
            .addContent(text(hint, 12f, if (queued > 0 && !recording) EMBER else MUTED))
            .build()

        val root = LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build()
            )
            .addContent(column)
            .build()

        val tile = TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(
                TimelineBuilders.Timeline.Builder()
                    .addTimelineEntry(
                        TimelineBuilders.TimelineEntry.Builder()
                            .setLayout(
                                LayoutElementBuilders.Layout.Builder().setRoot(root).build()
                            )
                            .build()
                    )
                    .build()
            )
            .build()
        return immediateFuture(tile)
    }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest
    ): ListenableFuture<ResourceBuilders.Resources> =
        immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build()
        )

    private fun text(value: String, sizeSp: Float, color: Int): LayoutElementBuilders.Text =
        LayoutElementBuilders.Text.Builder()
            .setText(value)
            .setFontStyle(
                LayoutElementBuilders.FontStyle.Builder()
                    .setSize(DimensionBuilders.sp(sizeSp))
                    .setColor(ColorBuilders.argb(color))
                    .build()
            )
            .build()

    private fun <T> immediateFuture(value: T): ListenableFuture<T> =
        object : ListenableFuture<T> {
            override fun addListener(listener: Runnable, executor: Executor) =
                executor.execute(listener)
            override fun cancel(mayInterruptIfRunning: Boolean) = false
            override fun isCancelled() = false
            override fun isDone() = true
            override fun get(): T = value
            override fun get(timeout: Long, unit: TimeUnit): T = value
        }

    private companion object {
        const val RESOURCES_VERSION = "1"

        /** Same field-recorder palette as the phone app and watch activity. */
        const val ON_GROUND = 0xFFF2F2EF.toInt()
        const val MUTED = 0xFF9A9AA2.toInt()
        const val SIGNAL = 0xFFE0483C.toInt()
        const val EMBER = 0xFFE8853A.toInt()
    }
}
