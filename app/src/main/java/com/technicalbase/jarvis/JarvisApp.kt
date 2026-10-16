package com.technicalbase.jarvis

import android.app.Application
import android.util.Log

class JarvisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i("Jarvis", "Application started")
    }
}
