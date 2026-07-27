package org.opensapien.app.widget

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
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
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import org.opensapien.app.MainActivity
import org.opensapien.core.recording.RecordingService
import org.opensapien.core.transcription.ModelManager

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
            Text(
                text = if (recording) "⏹ Recording…" else "● Record",
                style = TextStyle(color = ColorProvider(Color.White)),
            )
        }
    }
}

class ToggleRecordingAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val recording = RecordingService.state.value is RecordingService.State.Recording
        val micGranted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val modelReady = ModelManager(context).isInstalled

        when {
            recording -> RecordingService.toggle(context, "WIDGET")
            micGranted && modelReady ->
                try {
                    RecordingService.toggle(context, "WIDGET")
                } catch (t: Exception) {
                    // e.g. ForegroundServiceStartNotAllowedException on some OEMs —
                    // fall back to opening the app which starts recording itself.
                    launchApp(context, autoRecord = true)
                }
            else -> launchApp(context, autoRecord = false) // needs one-time setup in app
        }
        RecordWidget().update(context, glanceId)
    }

    private fun launchApp(context: Context, autoRecord: Boolean) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_AUTO_RECORD, autoRecord),
        )
    }
}

class RecordWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RecordWidget()
}
