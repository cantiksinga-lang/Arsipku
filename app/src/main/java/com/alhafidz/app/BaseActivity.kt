package com.alhafidz.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

open class BaseActivity : Activity() {
    open val needsLock: Boolean get() = true
    var inL = 0; var inT = 0; var inR = 0; var inB = 0; var imeB = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        P.init(this)
        @Suppress("DEPRECATION")
        run {
            var flags = window.decorView.systemUiVisibility
            flags = if (P.night) {
                flags and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv() and View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR.inv()
            } else {
                flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }
            window.decorView.systemUiVisibility = flags
        }
        if (Prefs.bool(this, K.SECURE, true)) {
            window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        }
        if (Build.VERSION.SDK_INT >= 30) {
            window.setDecorFitsSystemWindows(false)
        } else {
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        }
    }

    override fun onStart() {
        super.onStart()
        if (needsLock && Lock.locked) LockActivity.show(this)
    }

    @Suppress("DEPRECATION")
    private fun read(i: WindowInsets) {
        if (Build.VERSION.SDK_INT >= 30) {
            val s = i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            inL = s.left; inT = s.top; inR = s.right; inB = s.bottom
            imeB = i.getInsets(WindowInsets.Type.ime()).bottom
        } else {
            inL = i.systemWindowInsetLeft; inT = i.systemWindowInsetTop; inR = i.systemWindowInsetRight
            inB = i.stableInsetBottom
            imeB = if (i.systemWindowInsetBottom > i.stableInsetBottom) i.systemWindowInsetBottom - i.stableInsetBottom else 0
        }
    }

    fun setRoot(v: View, onInsets: () -> Unit) {
        setContentView(v)
        v.filterTouchesWhenObscured = true
        v.setOnApplyWindowInsetsListener { _, ins -> read(ins); onInsets(); ins }
        v.requestApplyInsets()
    }

    /** Buka aktivitas eksternal (picker, kamera, kalender) tanpa memicu kunci otomatis saat kembali. */
    fun launch(i: Intent, rc: Int) {
        Lock.external()
        try { startActivityForResult(i, rc) } catch (e: Exception) { toast("Tidak ada aplikasi yang bisa menangani ini") }
    }
}
