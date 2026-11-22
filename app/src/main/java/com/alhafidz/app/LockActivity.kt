package com.alhafidz.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.CountDownTimer
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import java.security.MessageDigest

/** mode 0 = buka kunci, 1 = buat PIN pertama kali, 2 = ubah PIN (verifikasi PIN lama dulu) */
class LockActivity : BaseActivity() {
    override val needsLock: Boolean get() = false

    private var mode = 0
    private var stage = 0
    private var first: String? = null
    private val pin = StringBuilder()
    private var busy = false
    private var bioTried = false
    private var timer: CountDownTimer? = null
    private var cancel: CancellationSignal? = null
    private lateinit var dots: LinearLayout
    private lateinit var title: TextView
    private lateinit var msg: TextView

    companion object {
        private var showing = false
        fun show(a: Activity) {
            if (showing) return
            a.startActivity(Intent(a, LockActivity::class.java))
            a.overridePendingTransition(0, 0)
        }
        fun change(a: Activity) {
            a.startActivity(Intent(a, LockActivity::class.java).putExtra("mode", 2))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showing = true
        mode = intent.getIntExtra("mode", 0)
        if (mode == 0 && !Prefs.hasPin(this)) mode = 1
        build()
        refreshTitle()
    }

    override fun onDestroy() { super.onDestroy(); showing = false; timer?.cancel() }

    override fun onStart() {
        super.onStart()
        bioTried = false
        checkLockout()
    }

    override fun onResume() {
        super.onResume()
        if (mode == 0 && !bioTried && canBio() && Prefs.bool(this, K.BIO, true) && !isLockedOut()) {
            bioTried = true
            startBio()
        }
    }

    override fun onPause() { super.onPause(); cancel?.cancel() }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (mode == 0 || mode == 1 && !Prefs.hasPin(this)) moveTaskToBack(true) else finish()
    }

    private fun build() {
        val root = FrameLayout(this)
        root.background = pageBg()
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.gravity = Gravity.CENTER_HORIZONTAL

        val ic = ImageView(this)
        ic.setImageResource(R.drawable.ic_shield)
        col.addView(ic, lp(64, 72, t = 8))
        col.addView(tv("ArsipKu", 24f, P.text, 2), lp(WRAP, WRAP, t = 14))
        title = tv("", 15f, P.sub)
        col.addView(title, lp(WRAP, WRAP, t = 6))

        dots = LinearLayout(this)
        dots.gravity = Gravity.CENTER
        for (i in 0 until 6) {
            val d = View(this)
            dots.addView(d, lp(14, 14, l = 8, r = 8))
        }
        col.addView(dots, lp(WRAP, WRAP, t = 26))
        msg = tv("", 13f, P.danger, 1)
        msg.gravity = Gravity.CENTER
        col.addView(msg, lp(MATCH, 36, t = 10, l = 24, r = 24))
        updateDots()

        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
        for (r in 0 until 4) {
            val row = LinearLayout(this)
            row.orientation = LinearLayout.HORIZONTAL
            row.gravity = Gravity.CENTER_HORIZONTAL
            for (cI in 0 until 3) row.addView(key(keys[r * 3 + cI]), lp(76, 76, l = 10, r = 10, b = 14))
            col.addView(row, lp(MATCH, WRAP))
        }
        col.addView(tv("\u00A9 2026 Rizal Alhafidz", 12f, P.sub), lp(WRAP, WRAP, t = 10))

        root.addView(col, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        setRoot(root) { root.setPadding(inL, inT, inR, inB) }
    }

    private fun key(k: String): View {
        val label = when (k) { "del" -> "\u232B"; "bio" -> if (mode == 0 && canBio() && Prefs.bool(this, K.BIO, true)) "\uD83D\uDC46" else ""; else -> k }
        val t = tv(label, if (k == "bio") 26f else 26f, P.text, 1)
        t.gravity = Gravity.CENTER
        if (label.isNotEmpty()) {
            t.background = shape(P.card, dpf(38))
            t.soft(1)
            t.pressable {
                when (k) {
                    "del" -> if (pin.isNotEmpty() && !busy) { pin.deleteCharAt(pin.length - 1); updateDots() }
                    "bio" -> startBio()
                    else -> digit(k)
                }
            }
        }
        return t
    }

    private fun digit(d: String) {
        if (busy || isLockedOut() || pin.length >= 6) return
        pin.append(d)
        msg.text = ""
        updateDots()
        if (pin.length == 6) dots.postDelayed({ submit() }, 120)
    }

    private fun updateDots() {
        for (i in 0 until 6) {
            val v = dots.getChildAt(i)
            v.background = if (i < pin.length) oval(P.blue) else shape(0x00000000, dpf(7), P.sub, dp(2)).also { it.shape = android.graphics.drawable.GradientDrawable.OVAL }
        }
    }

    private fun refreshTitle() {
        title.text = when {
            mode == 1 || (mode == 2 && stage == 1) -> if (first == null) "Buat PIN baru (6 digit)" else "Ulangi PIN"
            mode == 2 -> "Masukkan PIN lama"
            else -> "Masukkan PIN untuk membuka"
        }
    }

    private fun submit() {
        val p = pin.toString()
        pin.setLength(0)
        updateDots()
        when {
            mode == 1 || (mode == 2 && stage == 1) -> setupStep(p)
            else -> verify(p)
        }
    }

    private fun setupStep(p: String) {
        val f = first
        if (f == null) { first = p; refreshTitle(); return }
        if (f != p) { first = null; msg.text = "PIN tidak sama, ulangi"; refreshTitle(); return }
        busy = true
        Async.run {
            val salt = Crypto.random(16)
            val h = Crypto.hashPin(p, salt)
            Async.ui {
                Prefs.setStr(this, K.PIN_SALT, Crypto.b64(salt))
                Prefs.setStr(this, K.PIN_HASH, Crypto.b64(h))
                Prefs.setInt(this, K.FAILS, 0)
                if (mode == 1) {
                    if (!Prefs.sp(this).contains(K.BIO)) Prefs.setBool(this, K.BIO, canBio())
                    Lock.unlock()
                } else toast("PIN diubah")
                busy = false
                finish()
            }
        }
    }

    private fun verify(p: String) {
        busy = true
        Async.run {
            val salt = Crypto.unb64(Prefs.str(this, K.PIN_SALT) ?: "")
            val want = Crypto.unb64(Prefs.str(this, K.PIN_HASH) ?: "")
            val ok = MessageDigest.isEqual(Crypto.hashPin(p, salt), want)
            Async.ui { busy = false; if (ok) success() else wrong() }
        }
    }

    private fun success() {
        Prefs.setInt(this, K.FAILS, 0)
        if (mode == 2) { stage = 1; first = null; refreshTitle(); return }
        Lock.unlock()
        finish()
        overridePendingTransition(0, 0)
    }

    private fun wrong() {
        val f = Prefs.int(this, K.FAILS, 0) + 1
        Prefs.setInt(this, K.FAILS, f)
        if (Prefs.bool(this, K.WIPE, false) && f >= 10) {
            Store.wipe(this)
            Prefs.setInt(this, K.FAILS, 0)
            msg.text = "Terlalu banyak percobaan. Semua data dihapus."
            return
        }
        if (f >= 5) {
            val delay = minOf(30_000L shl minOf(f - 5, 7), 3_600_000L)
            Prefs.setLong(this, K.LOCK_UNTIL, System.currentTimeMillis() + delay)
            checkLockout()
        } else {
            msg.text = "PIN salah (${5 - f} percobaan lagi sebelum dikunci sementara)"
        }
    }

    private fun isLockedOut(): Boolean = System.currentTimeMillis() < Prefs.long(this, K.LOCK_UNTIL, 0)

    private fun checkLockout() {
        timer?.cancel()
        val left = Prefs.long(this, K.LOCK_UNTIL, 0) - System.currentTimeMillis()
        if (left <= 0) return
        timer = object : CountDownTimer(left, 1000) {
            override fun onTick(ms: Long) { msg.text = "Terlalu banyak percobaan. Coba lagi dalam ${ms / 1000 + 1} dtk" }
            override fun onFinish() { msg.text = "" }
        }.start()
    }

    private fun canBio(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        return try {
            val bm = getSystemService(BiometricManager::class.java)
            if (Build.VERSION.SDK_INT >= 30) bm.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            ) == BiometricManager.BIOMETRIC_SUCCESS
            else bm.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) { false }
    }

    private fun startBio() {
        if (mode != 0 || !canBio() || isLockedOut()) return
        try {
            cancel?.cancel()
            val cs = CancellationSignal()
            cancel = cs
            val prompt = BiometricPrompt.Builder(this)
                .setTitle("Buka ArsipKu")
                .setSubtitle("Gunakan sidik jari atau wajah")
                .setNegativeButton("Pakai PIN", mainExecutor) { _, _ -> }
                .build()
            prompt.authenticate(cs, mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) { success() }
            })
        } catch (e: Exception) { }
    }
}
