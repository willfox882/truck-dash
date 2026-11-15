package com.truckdash

import android.app.Application
import com.truckdash.data.AppGraph

class TruckDashApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)
    }
}
