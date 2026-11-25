package de.sensorcharts

import android.app.Application
import de.sensorcharts.data.AppContainer

class SensorChartsApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
