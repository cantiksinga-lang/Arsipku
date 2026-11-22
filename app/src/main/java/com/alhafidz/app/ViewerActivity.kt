package com.alhafidz.app

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.pdf.PdfRenderer
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import java.io.File
import java.util.concurrent.Executors

class PdfPages(file: File) {
    private val pfd: ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val r = PdfRenderer(pfd)
    val count: Int = r.pageCount
    val ratios = FloatArray(count)

    init {
        for (i in 0 until count) {
            val p = r.openPage(i)
            ratios[i] = if (p.width > 0) (p.height.toFloat() / p.width).coerceIn(0.2f, 4f) else 1.414f
            p.close()
        }
    }

    @Synchronized
    fun render(i: Int, w: Int): Bitmap {
        val p = r.openPage(i)
        try {
            val h = (w * p.height.toFloat() / p.width.coerceAtLeast(1)).toInt().coerceIn(1, 6000)
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            b.eraseColor(Color.WHITE)
            p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            return b
        } finally {
            p.close()
        }
    }

    @Synchronized
    fun close() {
        try { r.close(); pfd.close() } catch (e: Exception) { }
    }
}

class ViewerActivity : BaseActivity() {
    private var temp: File? = null
    private var pdf: PdfPages? = null
    private val exec = Executors.newSingleThreadExecutor()
    private val cache = object : LruCache<Int, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: Int, value: Bitmap): Int = value.byteCount
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = Store.doc(this, intent.getLongExtra("id", 0L))
        if (d == null) { finish(); return }
        if (Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.setSystemBarsAppearance(
                0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            )
        } else {
            window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and
                (View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR).inv()
        }
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)
        val bar = hbox()
        bar.setBackgroundColor(0x99000000.toInt())
        val x = tv("\u2715", 20f, Color.WHITE, 1)
        x.gravity = Gravity.CENTER
        x.pressable { finish() }
        bar.addView(x, lp(52, 52))
        bar.addView(tv(d.title, 17f, Color.WHITE, 2).also { it.maxLines = 1 }, lp(0, WRAP, 1f, r = 16))
        val loading = tv("Memuat\u2026", 14f, Color.WHITE, 1)
        root.addView(loading, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))

        val content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))
        setRoot(root) { bar.setPadding(inL, inT, inR, 0) }

        Async.run {
            try {
                if (d.isPdf) {
                    val t = Vault.decryptToTemp(this, d.file, "pdf")
                    temp = t
                    val p = PdfPages(t)
                    pdf = p
                    if (p.count == 0) throw java.io.IOException("PDF kosong")
                    Async.ui { loading.visibility = View.GONE; showPdf(content, p) }
                } else {
                    val b = Img.decode({ Vault.open(this, d.file) }, 2048) ?: throw java.io.IOException("Gambar rusak")
                    Async.ui {
                        loading.visibility = View.GONE
                        val z = ZoomImageView(this)
                        z.setImageBitmap(b)
                        content.addView(z, FrameLayout.LayoutParams(MATCH, MATCH))
                    }
                }
            } catch (t: Throwable) {
                Async.ui { loading.text = "Tidak dapat membuka dokumen\n(" + t.msg() + ")" ; loading.gravity = Gravity.CENTER }
            }
        }
    }

    private fun showPdf(host: FrameLayout, p: PdfPages) {
        val lv = ListView(this)
        lv.divider = ColorDrawable(0xFF1A1A1A.toInt())
        lv.dividerHeight = dp(8)
        lv.clipToPadding = false
        lv.setPadding(0, inT + dp(56), 0, inB + dp(16))
        lv.isVerticalScrollBarEnabled = false
        val w = (resources.displayMetrics.widthPixels - inL - inR).coerceIn(300, 1440)
        lv.adapter = object : BaseAdapter() {
            override fun getCount(): Int = p.count
            override fun getItem(position: Int): Any = position
            override fun getItemId(position: Int): Long = position.toLong()
            override fun getView(position: Int, cv: View?, parent: ViewGroup?): View {
                val iv = (cv as? ImageView) ?: ImageView(this@ViewerActivity).also { it.setBackgroundColor(Color.WHITE) }
                iv.layoutParams = AbsListView.LayoutParams(w, (w * p.ratios[position]).toInt().coerceAtLeast(1))
                iv.tag = position
                val hit = cache.get(position)
                if (hit != null) { iv.setImageBitmap(hit); return iv }
                iv.setImageDrawable(null)
                exec.execute {
                    try {
                        val b = p.render(position, w)
                        cache.put(position, b)
                        runOnUiThread { if (iv.tag == position) iv.setImageBitmap(b) }
                    } catch (t: Throwable) { }
                }
                return iv
            }
        }
        host.addView(lv, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdownNow()
        pdf?.close()
        temp?.delete()
    }
}
