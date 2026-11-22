package com.alhafidz.app

import android.app.Application

class GarasiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Repo.init(this)
        Notif.ensureChannel(this)
        Notif.schedule(this)
    }
}
