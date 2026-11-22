package com.alhafidz.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.widget.ImageView
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

object Vault {
    fun dir(c: Context): File = File(c.filesDir, "vault").also { it.mkdirs() }
    fun newName(): String = UUID.randomUUID().toString().replace("-", "")
    fun enc(c: Context, n: String): File = File(dir(c), "$n.enc")
    fun thumb(c: Context, n: String): File = File(dir(c), "$n.t")

    fun input(c: Context, f: File): InputStream =
        DecIn(BufferedInputStream(FileInputStream(f), 32768), Crypto.master(c))

    fun open(c: Context, n: String): InputStream = input(c, enc(c, n))

    fun output(c: Context, f: File): OutputStream =
        EncOut(BufferedOutputStream(FileOutputStream(f), 32768), Crypto.master(c))

    fun delete(c: Context, n: String) {
        enc(c, n).delete()
        thumb(c, n).delete()
    }

    private fun tmpDir(c: Context): File = File(c.cacheDir, "tmp").also { it.mkdirs() }

    fun clearTemp(c: Context) {
        tmpDir(c).listFiles()?.forEach { it.delete() }
        File(c.cacheDir, "cap").listFiles()?.forEach { it.delete() }
    }

    fun decryptToTemp(c: Context, n: String, ext: String): File {
        val f = File(tmpDir(c), newName() + "." + ext)
        open(c, n).use { i -> FileOutputStream(f).use { o -> i.copyTo(o) } }
        return f
    }

    /** Salin + enkripsi. Foto dikecilkan (maks 2048px, JPEG) agar hemat ruang. Return (namaBerkas, mime). */
    fun importUri(c: Context, uri: Uri, pdf: Boolean): Pair<String, String> {
        val name = newName()
        try {
            if (pdf) {
                val ins = c.contentResolver.openInputStream(uri) ?: throw IOException("Berkas tidak bisa dibuka")
                ins.use { i -> output(c, enc(c, name)).use { o -> i.copyTo(o) } }
            } else {
                val bmp = Img.decodeUri(c, uri, 2048) ?: throw IOException("Gambar tidak bisa dibaca")
                output(c, enc(c, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                bmp.recycle()
            }
            makeThumb(c, name, pdf)
        } catch (e: Throwable) {
            delete(c, name)
            throw e
        }
        return name to (if (pdf) "application/pdf" else "image/jpeg")
    }

    fun makeThumb(c: Context, n: String, pdf: Boolean) {
        val bmp: Bitmap? = if (pdf) {
            val t = decryptToTemp(c, n, "pdf")
            try { Img.pdfFirstPage(t, 360) } finally { t.delete() }
        } else Img.decode({ open(c, n) }, 360)
        if (bmp != null) output(c, thumb(c, n)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    fun importAvatar(c: Context, uri: Uri): String {
        val name = newName()
        val bmp = Img.decodeUri(c, uri, 256) ?: throw IOException("Gambar tidak bisa dibaca")
        output(c, enc(c, name)).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
        return name
    }
}

object Img {
    fun decode(open: () -> InputStream, max: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, o) }
        if (o.outWidth <= 0) return null
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / (s * 2) >= max) s *= 2
        val o2 = BitmapFactory.Options().apply { inSampleSize = s }
        val b = open().use { BitmapFactory.decodeStream(it, null, o2) } ?: return null
        val m = maxOf(b.width, b.height)
        if (m <= max) return b
        val f = max.toFloat() / m
        val r = Bitmap.createScaledBitmap(
            b, (b.width * f).toInt().coerceAtLeast(1), (b.height * f).toInt().coerceAtLeast(1), true
        )
        if (r !== b) b.recycle()
        return r
    }

    fun decodeUri(c: Context, uri: Uri, max: Int): Bitmap? {
        // Android 9+: ImageDecoder menangani HEIC/HEIF, foto 200 MP (Xiaomi), dan rotasi EXIF dengan hemat memori.
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                val src = ImageDecoder.createSource(c.contentResolver, uri)
                return ImageDecoder.decodeBitmap(src) { dec, info, _ ->
                    dec.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    val w = info.size.width
                    val h = info.size.height
                    val m = maxOf(w, h)
                    if (m > max) {
                        val f = max.toFloat() / m
                        dec.setTargetSize((w * f).toInt().coerceAtLeast(1), (h * f).toInt().coerceAtLeast(1))
                    }
                }
            } catch (t: Throwable) {
                // lanjut ke cara lama di bawah
            }
        }
        val decoded: Bitmap? = try {
            decode({ c.contentResolver.openInputStream(uri) ?: throw IOException("open") }, max)
        } catch (t: OutOfMemoryError) {
            throw IOException("Memori tidak cukup (foto terlalu besar)")
        }
        val b = decoded ?: return null
        val ori = try {
            c.contentResolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            } ?: 1
        } catch (e: Exception) { 1 }
        val m = Matrix()
        when (ori) {
            6 -> m.postRotate(90f)
            3 -> m.postRotate(180f)
            8 -> m.postRotate(270f)
            2 -> m.postScale(-1f, 1f)
            4 -> m.postScale(1f, -1f)
            else -> return b
        }
        val r = Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
        if (r !== b) b.recycle()
        return r
    }

    fun pdfFirstPage(f: File, w: Int): Bitmap? {
        val pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        val r = PdfRenderer(pfd)
        try {
            if (r.pageCount == 0) return null
            val p = r.openPage(0)
            try {
                val h = (w * p.height.toFloat() / p.width).toInt().coerceAtLeast(1)
                val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                b.eraseColor(Color.WHITE)
                p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return b
            } finally {
                p.close()
            }
        } finally {
            r.close()
            pfd.close()
        }
    }
}

/** Memuat thumbnail terenkripsi secara async + cache memori. */
object Thumbs {
    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun load(iv: ImageView, key: String, f: () -> Bitmap?) {
        iv.tag = key
        val hit = cache.get(key)
        if (hit != null) { iv.setImageBitmap(hit); return }
        iv.setImageDrawable(null)
        Async.run {
            val b = try { f() } catch (e: Throwable) { null }
            if (b != null) cache.put(key, b)
            Async.ui { if (iv.tag == key && b != null) iv.setImageBitmap(b) }
        }
    }

    fun doc(c: Context, iv: ImageView, d: Doc) = load(iv, "t:" + d.file) {
        val f = Vault.thumb(c, d.file)
        if (f.exists()) Vault.input(c, f).use { BitmapFactory.decodeStream(it) } else null
    }

    fun avatar(c: Context, iv: ImageView, name: String) = load(iv, "a:$name") {
        Vault.open(c, name).use { BitmapFactory.decodeStream(it) }
    }
}
