package com.alhafidz.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.File
import java.util.concurrent.TimeUnit

class ReminderWorker(ctx: Context, p: WorkerParameters) : CoroutineWorker(ctx, p) {
    override suspend fun doWork(): Result {
        Notif.check(applicationContext)
        return Result.success()
    }
}

object Notif {
    private fun chId() = "garasiku_s${Prefs.soundVer}"

    fun soundUri(ctx: Context): Uri {
        val f = Prefs.soundFile
        if (f != null) {
            val file = File(ctx.filesDir, "sound/$f")
            if (file.exists()) {
                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", file)
                ctx.grantUriPermission("com.android.systemui", u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                return u
            }
        }
        return Uri.parse("android.resource://${ctx.packageName}/${R.raw.garasi_default}")
    }

    fun ensureChannel(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val id = chId()
        nm.notificationChannels.filter { it.id.startsWith("garasiku_") && it.id != id }
            .forEach { nm.deleteNotificationChannel(it.id) }
        if (nm.getNotificationChannel(id) == null) {
            val ch = NotificationChannel(id, "Pengingat GarasiKu", NotificationManager.IMPORTANCE_HIGH)
            ch.description = "Pengingat servis, ganti oli, SIM, STNK dan pajak"
            ch.enableVibration(true)
            ch.setSound(
                soundUri(ctx),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            nm.createNotificationChannel(ch)
        }
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(ctx: Context, key: String, title: String, text: String, vehicleId: String? = null, docTab: Boolean = false) {
        if (!hasPermission(ctx)) return
        ensureChannel(ctx)
        val i = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (vehicleId != null) i.putExtra("vehicle", vehicleId)
        if (docTab) i.putExtra("doc", true)
        val pi = PendingIntent.getActivity(ctx, key.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val n = NotificationCompat.Builder(ctx, chId())
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title).setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true).setContentIntent(pi).build()
        ctx.getSystemService(NotificationManager::class.java).notify(key.hashCode(), n)
    }

    fun test(ctx: Context): Boolean {
        if (!hasPermission(ctx)) return false
        show(ctx, "test_${System.currentTimeMillis()}", "Tes Notifikasi GarasiKu",
            "Notifikasi berfungsi. Suara ini yang akan terdengar saat pengingat muncul.")
        return true
    }

    fun setCustomSound(ctx: Context, uri: Uri): Boolean {
        return try {
            val dir = File(ctx.filesDir, "sound").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val t = ctx.contentResolver.getType(uri) ?: ""
            val ext = if (t.contains("wav")) "wav" else if (t.contains("ogg")) "ogg" else "mp3"
            val f = File(dir, "custom_${System.currentTimeMillis()}.$ext")
            ctx.contentResolver.openInputStream(uri)!!.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            Prefs.soundFile = f.name
            Prefs.soundVer = Prefs.soundVer + 1
            Prefs.persist()
            ensureChannel(ctx)
            true
        } catch (e: Exception) { false }
    }

    fun resetSound(ctx: Context) {
        File(ctx.filesDir, "sound").listFiles()?.forEach { it.delete() }
        Prefs.soundFile = null
        Prefs.soundVer = Prefs.soundVer + 1
        Prefs.persist()
        ensureChannel(ctx)
    }

    fun schedule(ctx: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork("garasiku_reminder", ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Cek semua pengingat; tiap kondisi hanya dinotifikasi sekali per level. */
    fun check(ctx: Context) {
        if (!Prefs.notifOn) return
        val sp = ctx.getSharedPreferences("notified", Context.MODE_PRIVATE)
        val seen = sp.getStringSet("keys", emptySet())!!.toMutableSet()
        for (v in Repo.vehicles.value) {
            val oil = v.oilLeft
            if (oil <= Prefs.oilWarn) {
                val lvl = if (oil <= 0) "due" else "soon"
                val k = "oil:${v.id}:${v.lastOilKm}:$lvl"
                if (seen.add(k)) show(ctx, k, "Waktunya ganti oli — ${v.name}",
                    if (oil <= 0) "${v.plate}: oli sudah lewat ${num(-oil)} km dari jadwal." else "${v.plate}: ganti oli ${num(oil)} km lagi.", v.id)
            }
            val sv = v.serviceLeft
            if (sv <= Prefs.svcWarn) {
                val lvl = if (sv <= 0) "due" else "soon"
                val k = "svc:${v.id}:${v.lastServiceKm}:$lvl"
                if (seen.add(k)) show(ctx, k, "Waktunya servis — ${v.name}",
                    if (sv <= 0) "${v.plate}: servis sudah lewat ${num(-sv)} km dari jadwal." else "${v.plate}: servis ${num(sv)} km lagi.", v.id)
            }
        }
        for (d in Repo.docs.value) {
            if (d.expiry <= 0) continue
            val days = daysLeft(d.expiry)
            val lvl = when {
                days < 0 -> "expired"; days <= 1 -> "d1"; days <= 7 -> "d7"; days <= Prefs.docWarn -> "d${Prefs.docWarn}"; else -> null
            } ?: continue
            val k = "doc:${d.id}:${d.expiry}:$lvl"
            val who = d.vehicleId?.let { Repo.vehicle(it)?.plate } ?: ""
            if (seen.add(k)) show(ctx, k, "${DocT.short(d.type)} — ${d.name}",
                (if (days < 0) "Sudah habis ${-days} hari lalu" else "Berlaku $days hari lagi (${fmtDate(d.expiry)})") + if (who.isNotBlank()) " • $who" else "",
                null, true)
        }
        sp.edit().putStringSet("keys", seen).apply()
    }
}
