package org.opensapien.app

import android.app.Application
import org.opensapien.core.sync.DriveSyncWorker

class OpenSapienApp : Application() {
    override fun onCreate() {
        super.onCreate()
        DriveSyncWorker.schedule(this)
    }
}
