package com.alhafidz.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.LruCache
import androidx.exifinterface.media.ExifInterface
import java.io.File

object Img {
    private val cache = object : LruCache<String, Bitmap>(30 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    fun decodeUri(ctx: Context, uri: Uri, max: Int): Bitmap? {
        val cr = ctx.contentResolver
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / s > max) s *= 2
        val bmp = cr.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = s })
        } ?: return null
        val orient = try {
            cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
                ?: ExifInterface.ORIENTATION_NORMAL
        } catch (e: Exception) { ExifInterface.ORIENTATION_NORMAL }
        val deg = when (orient) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (deg == 0f) return bmp
        val m = Matrix().apply { postRotate(deg) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    fun decodeFile(f: File, max: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var s = 1
        while (maxOf(o.outWidth, o.outHeight) / s > max) s *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = s })
    }

    fun pdfPage(f: File, index: Int, width: Int): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var r: PdfRenderer? = null
        try {
            pfd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            r = PdfRenderer(pfd)
            if (r.pageCount == 0) return null
            val p = r.openPage(index)
            try {
                val h = (width * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bmp
            } finally { p.close() }
        } catch (e: Exception) {
            return null
        } finally {
            try { r?.close() } catch (e: Exception) { }
            try { pfd?.close() } catch (e: Exception) { }
        }
    }

    fun load(name: String, max: Int): Bitmap? {
        val key = "$name@$max"
        cache.get(key)?.let { return it }
        val f = File(Repo.filesDir, name)
        if (!f.exists()) return null
        val b = if (name.endsWith(".pdf")) pdfPage(f, 0, max) else decodeFile(f, max)
        if (b != null) cache.put(key, b)
        return b
    }
}

/** Pemegang PdfRenderer untuk pratinjau multi-halaman. */
class PdfHolder(file: File) {
    private val pfd: ParcelFileDescriptor? = try { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) } catch (e: Exception) { null }
    private val r: PdfRenderer? = try { pfd?.let { PdfRenderer(it) } } catch (e: Exception) { null }
    val count: Int = r?.pageCount ?: 0

    @Synchronized
    fun render(i: Int, width: Int): Bitmap? {
        val rr = r ?: return null
        return try {
            val p = rr.openPage(i)
            try {
                val h = (width * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            } finally { p.close() }
        } catch (e: Exception) { null }
    }

    @Synchronized
    fun close() {
        try { r?.close() } catch (e: Exception) { }
        try { pfd?.close() } catch (e: Exception) { }
    }
}
