package de.sensorcharts.data

import android.content.Context

/** Simple manual dependency container, created once per process. */
class AppContainer(context: Context) {
    val settings = SettingsRepository(context.applicationContext)
    val api = PostgrestClient()
}
