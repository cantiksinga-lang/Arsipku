package com.alhafidz.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Reminder {
    private const val CH = "garasiku_reminder"
    const val ACTION = "com.alhafidz.app.CHECK"

    /** Cek harian jam 08:00. */
    fun schedule(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = PendingIntent.getBroadcast(
            ctx, 100,
            Intent(ctx, ReminderReceiver::class.java).setAction(ACTION),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 8)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        if (cal.timeInMillis < System.currentTimeMillis()) cal.add(Calendar.DAY_OF_YEAR, 1)
        am.setInexactRepeating(AlarmManager.RTC, cal.timeInMillis, AlarmManager.INTERVAL_DAY, pi)
    }

    fun check(ctx: Context, force: Boolean = false) {
        val root = Snap.root(ctx)
        val n = root.optJSONObject("notif")
        if (n != null && !n.optBoolean("enabled", true)) return
        val oilWarn = n?.optLong("oilWarnKm", 500) ?: 500L
        val serviceWarn = n?.optLong("serviceWarnKm", 500) ?: 500L
        val taxWarn = n?.optLong("taxWarnDays", 14) ?: 14L
        val simWarn = n?.optLong("simWarnDays", 30) ?: 30L

        val prefs = ctx.getSharedPreferences(Snap.PREF, Context.MODE_PRIVATE)
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

        fun once(key: String, title: String, text: String) {
            if (!force && prefs.getString("last_$key", "") == today) return
            prefs.edit().putString("last_$key", today).apply()
            post(ctx, key, title, text)
        }

        val vs = root.optJSONArray("vehicles") ?: JSONArray()
        for (i in 0 until vs.length()) {
            val v = vs.optJSONObject(i) ?: continue
            val id = v.optString("id")
            val name = v.optString("name")

            if (Snap.has(v, "oilLeft")) {
                val left = v.optLong("oilLeft")
                if (left <= oilWarn) once(
                    "oil_$id", "Waktunya ganti oli · $name",
                    if (left <= 0) "Sudah lewat ${Snap.fmt(-left)} km dari jadwal ganti oli."
                    else "Sisa ${Snap.fmt(left)} km lagi sebelum ganti oli."
                )
            }
            if (Snap.has(v, "serviceLeft")) {
                val left = v.optLong("serviceLeft")
                if (left <= serviceWarn) once(
                    "svc_$id", "Waktunya servis · $name",
                    if (left <= 0) "Sudah lewat ${Snap.fmt(-left)} km dari jadwal servis."
                    else "Sisa ${Snap.fmt(left)} km lagi sebelum servis."
                )
            }
            if (Snap.has(v, "taxDays")) {
                val d = v.optLong("taxDays")
                if (d <= taxWarn) once(
                    "tax_$id", "Pajak kendaraan · $name",
                    if (d < 0) "Pajak sudah lewat ${-d} hari." else if (d == 0L) "Pajak jatuh tempo hari ini." else "Pajak jatuh tempo $d hari lagi."
                )
            }
        }

        val sims = root.optJSONArray("sims") ?: JSONArray()
        for (i in 0 until sims.length()) {
            val s = sims.optJSONObject(i) ?: continue
            val d = s.optLong("days")
            if (d <= simWarn) once(
                "sim_${s.optString("id")}", "Masa berlaku SIM · ${s.optString("label")}",
                if (d < 0) "SIM sudah habis masa berlaku ${-d} hari." else if (d == 0L) "SIM habis masa berlaku hari ini." else "SIM habis masa berlaku $d hari lagi."
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun post(ctx: Context, key: String, title: String, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CH, "Pengingat servis & dokumen", NotificationManager.IMPORTANCE_HIGH)
            )
        }
        val open = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(ctx, CH) else Notification.Builder(ctx)
        b.setSmallIcon(R.drawable.ic_stat_garasi)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
        nm.notify(key.hashCode(), b.build())
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.check(context)
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminder.schedule(context)
        GarasiWidgetProvider.refreshAll(context)
    }
}
