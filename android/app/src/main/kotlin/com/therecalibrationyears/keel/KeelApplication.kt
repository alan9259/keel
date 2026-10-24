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
        ReminderTopUpWorker.schedule(this)
        // iOS `scenePhase` .active: each time Keel comes to the foreground, the first time too
        // (the process may have started in the background, so launch work alone isn't her
        // opening Keel). The lock is checked first, so the gate is up before anything behind it
        // does work.
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                container.controller.lock.applyForeground()
                container.controller.onForeground()
            }

            // iOS `.background`: note when she left; a return after the grace window locks.
            override fun onStop(owner: LifecycleOwner) = container.controller.lock.markBackgrounded()
        })
    }
}
