package com.clarohome.app

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ClaroApplication : Application() {
    @Inject lateinit var telemetry: AppTelemetry

    override fun onCreate() {
        super.onCreate()
        telemetry.start(this)
    }
}
