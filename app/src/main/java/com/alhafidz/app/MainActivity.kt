package com.alhafidz.app

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import kotlinx.coroutines.delay

fun Context.activity(): MainActivity? {
    var c: Context = this
    while (c is ContextWrapper) {
        if (c is MainActivity) return c
        c = c.baseContext
    }
    return null
}

class MainActivity : FragmentActivity() {
    val nav = Nav()
    var locked by mutableStateOf(false)
    private var stoppedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        locked = Prefs.pinEnabled
        handleIntent(intent)
        setContent { Root() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i == null) return
        val vid = i.getStringExtra("vehicle")
        if (vid != null) {
            nav.stack.clear(); nav.tab = 0
            if (Repo.vehicle(vid) != null) nav.go(Route.VehicleDetail(vid))
        } else if (i.getBooleanExtra("doc", false)) {
            nav.stack.clear(); nav.tab = 1
        }
    }

    override fun onStop() {
        super.onStop()
        stoppedAt = SystemClock.elapsedRealtime()
    }

    override fun onStart() {
        super.onStart()
        if (Prefs.pinEnabled && stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 45_000) locked = true
    }

    fun canBio(): Boolean = BiometricManager.from(this)
        .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

    fun promptBio(onOk: () -> Unit) {
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
            })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Buka GarasiKu")
            .setNegativeButtonText("Pakai PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
            .build()
        prompt.authenticate(info)
    }

    @Composable
    fun Root() {
        var tick by remember { mutableLongStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) { while (true) { delay(30_000); tick = System.currentTimeMillis() } }
        val mode = remember(tick, Prefs.themeMode) { currentMode() }
        val permL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        val named = Prefs.name.isNotBlank()
        LaunchedEffect(named) {
            if (named && Build.VERSION.SDK_INT >= 33 && !Notif.hasPermission(this@MainActivity)) {
                permL.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        SideEffect {
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = mode == Mode.LIGHT
                isAppearanceLightNavigationBars = mode == Mode.LIGHT
            }
            if (Prefs.allowShot) window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            else window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
        GarasiTheme(mode) {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                if (locked) {
                    LockScreen(
                        onUnlock = { locked = false },
                        onBio = if (Prefs.bio && canBio()) ({ promptBio { locked = false } }) else null
                    )
                } else {
                    MainApp(nav)
                    if (!named) NameDialog()
                }
            }
        }
    }
}

@Composable
fun NameDialog() {
    var t by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = {},
        title = { Text("Selamat datang di GarasiKu 👋") },
        text = {
            Column {
                Text("Siapa nama Anda? Nama ini dipakai untuk sapaan di beranda.")
                Spacer(Modifier.height(12.dp))
                Field(t, { t = it.take(30) }, "Nama")
            }
        },
        confirmButton = {
            TextButton(onClick = { Prefs.name = t.trim(); Prefs.persist() }, enabled = t.isNotBlank()) { Text("Mulai") }
        }
    )
}
