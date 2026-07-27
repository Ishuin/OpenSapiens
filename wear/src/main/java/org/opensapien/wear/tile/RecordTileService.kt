package org.opensapien.wear.tile

import androidx.wear.protolayout.ActionBuilders
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

/**
 * One-tap record tile (swipe from watch face). Tapping launches
 * WearMainActivity, which starts/stops WearRecordingService.
 *
 * TODO: richer UI (tiles-material button, live recording state via
 * ActionBuilders.LoadAction instead of an activity launch).
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

        val root = LayoutElementBuilders.Box.Builder()
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder().setClickable(clickable).build()
            )
            .addContent(
                LayoutElementBuilders.Text.Builder().setText("● Record").build()
            )
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
    }
}
