package com.alhafidz.app

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Suara pengingat. Diputar dengan AudioAttributes USAGE_ALARM, sehingga volume & aturan mengikuti
 * volume alarm (bukan volume media/notifikasi). Bawaan: res/raw/reminder.wav. Kustom: MP3/WAV pilihan pengguna.
 */
object Sound {
    private const val MAX_BYTES = 15L * 1024 * 1024
    private const val MAX_PLAY_MS = 20_000L

    private fun customFile(c: Context): File = File(File(c.filesDir, "sound").also { it.mkdirs() }, "custom")

    fun hasCustom(c: Context): Boolean = customFile(c).exists()
    fun label(c: Context): String =
        if (hasCustom(c)) (Prefs.str(c, K.SOUND_NAME) ?: "Suara kustom") else "Bawaan (lonceng)"

    fun clearCustom(c: Context) {
        customFile(c).delete()
        Prefs.setStr(c, K.SOUND_NAME, "")
    }

    /** Salin berkas MP3/WAV ke penyimpanan privat dan pastikan bisa diputar. Return nama berkas. */
    fun setCustom(c: Context, uri: Uri): String {
        val dest = customFile(c)
        val tmp = File(dest.parentFile, "custom.tmp")
        try {
            val ins = c.contentResolver.openInputStream(uri) ?: throw IOException("Tidak bisa membaca berkas")
            var total = 0L
            ins.use { i ->
                FileOutputStream(tmp).use { o ->
                    val buf = ByteArray(32768)
                    while (true) {
                        val n = i.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) throw IOException("Berkas terlalu besar (maks. 15 MB)")
                        o.write(buf, 0, n)
                    }
                }
            }
            val mp = MediaPlayer()
            try {
                mp.setDataSource(tmp.absolutePath)
                mp.prepare()
                if (mp.duration <= 0) throw IOException("x")
            } catch (e: Exception) {
                throw IOException("Format audio tidak didukung. Gunakan MP3 atau WAV.")
            } finally {
                mp.release()
            }
            dest.delete()
            if (!tmp.renameTo(dest)) throw IOException("Gagal menyimpan suara")
        } finally {
            tmp.delete()
        }
        val found: String? = try {
            c.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        } catch (e: Exception) { null }
        val name = found ?: "Suara kustom"
        Prefs.setStr(c, K.SOUND_NAME, name)
        return name
    }

    private fun prepared(c: Context, custom: Boolean): MediaPlayer {
        val mp = MediaPlayer()
        try {
            mp.setAudioAttributes(
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
            )
            if (custom) mp.setDataSource(customFile(c).absolutePath)
            else c.resources.openRawResourceFd(R.raw.reminder).use { a -> mp.setDataSource(a.fileDescriptor, a.startOffset, a.length) }
            mp.prepare()
            return mp
        } catch (t: Throwable) {
            mp.release()
            throw t
        }
    }

    /** Memutar (memblokir sampai selesai, maks. 20 dtk). Panggil dari thread latar. */
    fun play(c: Context, force: Boolean = false) {
        if (!force && !Prefs.bool(c, K.SOUND, true)) return
        var mp: MediaPlayer? = null
        if (hasCustom(c)) mp = try { prepared(c, true) } catch (t: Throwable) { null }
        if (mp == null) mp = try { prepared(c, false) } catch (t: Throwable) { null }
        val player: MediaPlayer = mp ?: return
        val latch = CountDownLatch(1)
        try {
            player.setOnCompletionListener { latch.countDown() }
            player.setOnErrorListener { _, _, _ -> latch.countDown(); true }
            player.start()
            latch.await(MAX_PLAY_MS, TimeUnit.MILLISECONDS)
        } catch (t: Throwable) {
        } finally {
            try { player.stop() } catch (e: Exception) { }
            player.release()
        }
    }
}
