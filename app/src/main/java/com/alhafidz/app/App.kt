package com.alhafidz.app

import android.app.Activity
import android.app.Application
import android.os.Bundle

class App : Application() {
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        Reminder.createChannel(this)
        Reminder.schedule(this)
        Vault.clearTemp(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) { if (started++ == 0) Lock.onForeground(this@App) }
            override fun onActivityStopped(a: Activity) {
                if (--started == 0 && !a.isChangingConfigurations) Lock.onBackground()
            }
            override fun onActivityCreated(a: Activity, s: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, s: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
