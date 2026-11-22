package com.alhafidz.app

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.security.MessageDigest
import java.util.UUID

object Prefs {
    private lateinit var sp: SharedPreferences

    var name by mutableStateOf("")
    var themeMode by mutableIntStateOf(0) // 0 otomatis, 1 terang, 2 senja, 3 gelap
    var notifOn by mutableStateOf(true)
    var allowShot by mutableStateOf(true)
    var bio by mutableStateOf(false)
    var pinEnabled by mutableStateOf(false)
    var oilWarn by mutableIntStateOf(500)
    var svcWarn by mutableIntStateOf(300)
    var docWarn by mutableIntStateOf(30)
    var soundFile: String? by mutableStateOf(null)
    var soundVer by mutableIntStateOf(0)

    fun init(c: Context) {
        sp = c.getSharedPreferences("garasiku", Context.MODE_PRIVATE)
        name = sp.getString("name", "") ?: ""
        themeMode = sp.getInt("theme", 0)
        notifOn = sp.getBoolean("notifOn", true)
        allowShot = sp.getBoolean("allowShot", true)
        bio = sp.getBoolean("bio", false)
        pinEnabled = sp.getString("pinHash", null) != null
        oilWarn = sp.getInt("oilWarn", 500)
        svcWarn = sp.getInt("svcWarn", 300)
        docWarn = sp.getInt("docWarn", 30)
        soundFile = sp.getString("soundFile", null)
        soundVer = sp.getInt("soundVer", 0)
    }

    fun persist() {
        sp.edit().putString("name", name).putInt("theme", themeMode).putBoolean("notifOn", notifOn)
            .putBoolean("allowShot", allowShot).putBoolean("bio", bio).putInt("oilWarn", oilWarn)
            .putInt("svcWarn", svcWarn).putInt("docWarn", docWarn).putString("soundFile", soundFile)
            .putInt("soundVer", soundVer).apply()
    }

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256").digest((salt + pin).toByteArray()).joinToString("") { "%02x".format(it) }

    fun setPin(pin: String) {
        val salt = UUID.randomUUID().toString()
        sp.edit().putString("pinSalt", salt).putString("pinHash", hash(pin, salt)).apply()
        pinEnabled = true
    }

    fun checkPin(pin: String): Boolean {
        val salt = sp.getString("pinSalt", "") ?: ""
        return hash(pin, salt) == sp.getString("pinHash", null)
    }

    fun clearPin() {
        sp.edit().remove("pinSalt").remove("pinHash").apply()
        pinEnabled = false
        bio = false
        persist()
    }
}
