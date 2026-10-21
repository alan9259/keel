package com.therecalibrationyears.keel

import android.app.Application

class KeelApplication : Application() {
    /** The app's single object graph (manual DI, plan §1.2). */
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.controller.bootstrap()
    }
}
