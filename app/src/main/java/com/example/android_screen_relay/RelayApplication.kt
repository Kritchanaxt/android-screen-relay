package com.example.android_screen_relay

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import android.util.Log

class RelayApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleListener())
    }
}

class AppLifecycleListener : DefaultLifecycleObserver {
    override fun onStart(owner: LifecycleOwner) {
        Log.d("AppLifecycle", "App moved to foreground")
        // Notify Service/WebSocket if needed
    }

    override fun onStop(owner: LifecycleOwner) {
        Log.d("AppLifecycle", "App moved to background")
        // Notify Service/WebSocket if needed
    }
}
