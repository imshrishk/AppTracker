package com.apptracker

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.apptracker.monitor.AccessibilityWatchdog
import com.apptracker.monitor.AppOpsRealtimeMonitor
import com.apptracker.worker.DataRefreshWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class AppTrackerApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var appOpsRealtimeMonitor: AppOpsRealtimeMonitor

    @Inject
    lateinit var accessibilityWatchdog: AccessibilityWatchdog

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        appOpsRealtimeMonitor.start()
        accessibilityWatchdog.start()
        DataRefreshWorker.enqueue(this)
    }
}
