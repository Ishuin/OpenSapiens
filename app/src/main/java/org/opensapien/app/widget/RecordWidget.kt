package org.opensapien.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.action.clickable
import org.opensapien.core.recording.RecordingService

/** Home/lock-screen widget: one tap toggles the foreground recorder. */
class RecordWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            val recording = RecordingService.state.value is RecordingService.State.Recording
            WidgetContent(recording)
        }
    }

    @Composable
    private fun WidgetContent(recording: Boolean) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(ColorProvider(if (recording) Color(0xFFB3261E) else Color(0xFF1C1B1F)))
                .padding(8.dp)
                .clickable(actionRunCallback<ToggleRecordingAction>()),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (recording) "⏹ Recording…" else "● Record")
        }
    }
}

class ToggleRecordingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        RecordingService.toggle(context, "WIDGET")
        RecordWidget().update(context, glanceId)
    }
}

class RecordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecordWidget()
}
