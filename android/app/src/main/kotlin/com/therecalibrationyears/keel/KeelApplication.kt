package com.therecalibrationyears.keel

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

class KeelApplication : Application() {
    /** The app's single object graph (manual DI, plan §1.2). */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.controller.bootstrap()
        // iOS `scenePhase` .active: each time Keel comes to the foreground (not the first
        // start, which bootstrap covers).
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            private var started = false
            override fun onStart(owner: LifecycleOwner) {
                if (started) container.controller.onForeground()
                started = true
            }
        })
    }
}
