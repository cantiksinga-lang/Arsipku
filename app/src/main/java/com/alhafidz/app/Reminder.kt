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
import android.os.PowerManager
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

object Reminder {
    // Channel dibuat SENYAP: suara diputar sendiri (Sound.kt) memakai volume ALARM dan bisa dimatikan/kustom.
    const val CH = "expiry_v2"
    private const val ID = 1001

    fun createChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.deleteNotificationChannel("expiry")
        val ch = NotificationChannel(CH, "Pengingat masa berlaku", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Pengingat dokumen yang akan atau sudah habis masa berlakunya"
        ch.lockscreenVisibility = Notification.VISIBILITY_PRIVATE
        ch.setSound(null, null)
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 350, 200, 350)
        nm.createNotificationChannel(ch)
    }

    private fun pi(c: Context): PendingIntent = PendingIntent.getBroadcast(
        c, 1, Intent(c, ReminderReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    fun nextTrigger(c: Context): Long {
        val h = Prefs.int(c, K.HOUR, 8)
        val zone = ZoneId.systemDefault()
        var t = LocalDate.now().atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        if (t <= System.currentTimeMillis() + 30_000L) {
            t = LocalDate.now().plusDays(1).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
        }
        return t
    }

    /** Satu alarm exact per hari (tembus Doze), dijadwalkan ulang setiap kali berbunyi. */
    fun schedule(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        am.cancel(pi(c))
        if (!Prefs.bool(c, K.NOTIF, true)) return
        val t = nextTrigger(c)
        val exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()
        try {
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c))
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, pi(c))
        }
    }

    fun notifEnabled(c: Context): Boolean =
        c.getSystemService(NotificationManager::class.java).areNotificationsEnabled()

    /** Tampilkan notifikasi ringkasan. Return true bila notifikasi benar-benar ditampilkan. */
    fun fire(c: Context, test: Boolean = false): Boolean {
        if (!test && !Prefs.bool(c, K.NOTIF, true)) return false
        val nm = c.getSystemService(NotificationManager::class.java)
        if (!nm.areNotificationsEnabled()) return false
        val due = Store.docs(c).filter { !it.lifetime }
            .map { it to statusOf(c, it) }
            .filter { it.second.level == 0 }
            .sortedBy { it.second.days }
        if (due.isEmpty() && !test) { ExpiryWidget.refresh(c); return false }

        val open = PendingIntent.getActivity(
            c, 2,
            Intent(c, MainActivity::class.java).putExtra("tab", 2)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val generic = if (due.isEmpty()) "Tes pengingat ArsipKu" else "Ada ${due.size} dokumen yang perlu diperhatikan"
        val pub = Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("ArsipKu").setContentText(generic).build()
        val b = Notification.Builder(c, CH).setSmallIcon(R.drawable.ic_stat)
            .setContentIntent(open).setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setPublicVersion(pub)
        if (due.isEmpty()) {
            b.setContentTitle("Tes pengingat ArsipKu").setContentText("Notifikasi berfungsi normal. Tidak ada dokumen yang perlu diperpanjang.")
        } else if (Prefs.bool(c, K.HIDE, false)) {
            b.setContentTitle("ArsipKu").setContentText(generic)
        } else {
            val mem = Store.members(c).associateBy { it.id }
            val lines = due.take(6).map { (d, s) ->
                val o = mem[d.member]?.name?.uppercase()
                (if (o != null) "${d.title} ($o)" else d.title) + ": " + s.label
            }
            val st = Notification.InboxStyle()
            lines.forEach { st.addLine(it) }
            b.setContentTitle(generic).setContentText(lines.first()).setStyle(st)
        }
        nm.notify(ID, b.build())
        if (!test) Prefs.setLong(c, K.LAST_FIRE, Fmt.today())
        ExpiryWidget.refresh(c)
        return true
    }

    /** Dipanggil setelah reboot: jika jam pengingat hari ini sudah lewat dan belum pernah tampil, tampilkan sekarang. */
    fun catchUp(c: Context) {
        if (!Prefs.bool(c, K.NOTIF, true)) return
        if (LocalDateTime.now().hour >= Prefs.int(c, K.HOUR, 8) && Prefs.long(c, K.LAST_FIRE, -1L) != Fmt.today()) fire(c)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pr = goAsync()
        val wl = app.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "alhafidz:reminder")
        wl.acquire(45_000L)
        Thread {
            try {
                Reminder.schedule(app) // jadwalkan besok DULU supaya pengingat harian tidak pernah putus
                if (Reminder.fire(app)) Sound.play(app)
            } catch (t: Throwable) {
            } finally {
                try { if (wl.isHeld) wl.release() } catch (e: Exception) { }
                pr.finish()
            }
        }.start()
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val pr = goAsync()
        Thread {
            try {
                Reminder.schedule(app)
                if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) Reminder.catchUp(app)
                ExpiryWidget.refresh(app)
            } catch (t: Throwable) {
            } finally {
                pr.finish()
            }
        }.start()
    }
}
