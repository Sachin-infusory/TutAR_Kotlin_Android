package com.infusory.tutarapp

import android.app.Application


class TutarAppActivity : Application() {

    override fun onCreate() {
        super.onCreate()

        // Initialize any required libraries or configurations here
        initializeApp()
    }

    private fun initializeApp() {
        // Initialize crash reporting, analytics, etc.
        // Example: Firebase.initialize(this)
        // Example: Timber.plant(Timber.DebugTree())
    }
}