package org.opensapien.app

import android.app.Application
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.opensapien.app.widget.RecordWidget
import org.opensapien.core.recording.RecordingService
import org.opensapien.core.sync.BackupSyncWorker

class OpenSapienApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        BackupSyncWorker.schedule(this)
        // Keep home-screen widgets in lockstep with recorder state.
        appScope.launch {
            RecordingService.state.collect {
                runCatching { RecordWidget().updateAll(this@OpenSapienApp) }
            }
        }
    }
}
