package com.alhafidz.app

import android.content.Context
import android.content.SharedPreferences

object K {
    const val PIN_HASH = "pin_hash"
    const val PIN_SALT = "pin_salt"
    const val BIO = "bio"
    const val AUTOLOCK = "autolock"
    const val SECURE = "secure"
    const val NOTIF = "notif"
    const val DAYS = "days"
    const val HOUR = "hour"
    const val HIDE = "hide"
    const val WIPE = "wipe"
    const val FAILS = "fails"
    const val LOCK_UNTIL = "lock_until"
    const val LAST_BACKUP = "last_backup"
    const val ASKED = "asked_notif"
    const val MK = "mk"
    const val SEEDED = "seeded"
    const val SOUND = "sound"
    const val SOUND_NAME = "sound_name"
    const val LAST_FIRE = "last_fire"
    const val WHIDE = "whide"
    const val THEME = "theme_mode"
}

object Prefs {
    fun sp(c: Context): SharedPreferences = c.applicationContext.getSharedPreferences("alh", Context.MODE_PRIVATE)
    fun str(c: Context, k: String): String? = sp(c).getString(k, null)
    fun setStr(c: Context, k: String, v: String) { sp(c).edit().putString(k, v).apply() }
    fun bool(c: Context, k: String, d: Boolean): Boolean = sp(c).getBoolean(k, d)
    fun setBool(c: Context, k: String, v: Boolean) { sp(c).edit().putBoolean(k, v).apply() }
    fun int(c: Context, k: String, d: Int): Int = sp(c).getInt(k, d)
    fun setInt(c: Context, k: String, v: Int) { sp(c).edit().putInt(k, v).apply() }
    fun long(c: Context, k: String, d: Long): Long = sp(c).getLong(k, d)
    fun setLong(c: Context, k: String, v: Long) { sp(c).edit().putLong(k, v).apply() }
    fun hasPin(c: Context): Boolean = str(c, K.PIN_HASH) != null
}
