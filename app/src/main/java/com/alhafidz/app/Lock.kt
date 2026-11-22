package com.alhafidz.app

import android.content.Context
import android.os.SystemClock

object Lock {
    @Volatile var locked = true
    private var bgAt = 0L
    private var externalUntil = 0L

    fun onBackground() { bgAt = SystemClock.elapsedRealtime() }

    /** Dipanggil sebelum membuka picker/kamera/kalender agar tidak langsung terkunci saat kembali. */
    fun external() { externalUntil = SystemClock.elapsedRealtime() + 120_000L }

    fun onForeground(c: Context) {
        val t = bgAt
        bgAt = 0L
        val now = SystemClock.elapsedRealtime()
        if (now < externalUntil) { externalUntil = 0L; return }
        if (t == 0L) return
        val lim = Prefs.int(c, K.AUTOLOCK, 60) * 1000L
        if (now - t >= lim) locked = true
    }

    fun unlock() { locked = false }
}
