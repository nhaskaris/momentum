package com.eliteonetube.momentum

import android.app.Application
import com.eliteonetube.momentum.logic.utils.CrashHandler
import com.eliteonetube.momentum.widget.WidgetSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class MomentumApplication : Application() {
    // Lives as long as the process: background work that isn't tied to any screen
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        CrashHandler.initialize(this)
        WidgetSync.start(this, appScope)
    }
}
