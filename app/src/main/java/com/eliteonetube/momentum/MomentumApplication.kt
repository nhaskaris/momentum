package com.eliteonetube.momentum

import android.app.Application
import com.eliteonetube.momentum.logic.utils.CrashHandler

class MomentumApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashHandler.initialize(this)
    }
}
