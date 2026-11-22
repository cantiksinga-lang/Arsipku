package com.alhafidz.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView

class DetailActivity : BaseActivity() {
    private lateinit var holder: FrameLayout
    private var doc: Doc? = null
    private var sv: ScrollView? = null
    private var gen = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        holder = FrameLayout(this)
        holder.background = pageBg()
        setRoot(holder) { sv?.setPadding(inL, inT + dp(14), inR, inB + dp(24)) }
    }

    override fun onResume() { super.onResume(); render() }

    private fun render() {
        val d = Store.doc(this, intent.getLongExtra("id", 0L))
        if (d == null) { finish(); return }
        doc = d
        val mem = Store.members(this).associateBy { it.id }
        val st = statusOf(this, d)
        holder.removeAllViews()
        val myGen = ++gen
        val sv = ScrollView(this)
        this.sv = sv
        sv.isVerticalScrollBarEnabled = false
        sv.clipToPadding = false
        sv.setPadding(inL, inT + dp(14), inR, inB + dp(24))
        val col = vbox()
        col.setPadding(dp(20), 0, dp(20), 0)

        val hd = hbox()
        val back = tv("\u2039", 30f, P.text, 1)
        back.gravity = Gravity.CENTER
        back.background = cardBg(16, P.cardSolid); back.soft(2)
        back.pressable { finish() }
        hd.addView(back, lp(46, 46))
        hd.addView(tv(d.title, 22f, P.text, 2).also { it.maxLines = 1 }, lp(0, WRAP, 1f, l = 14, r = 8))
        val more = tv("\u22EE", 24f, P.text, 2)
        more.gravity = Gravity.CENTER
        more.background = cardBg(16, P.cardSolid); more.soft(2)
        more.pressable {
            val pm = PopupMenu(this, more)
            pm.menu.add(0, 1, 0, "Edit")
            if (!d.lifetime) pm.menu.add(0, 2, 1, "Masukkan ke Kalender")
            pm.menu.add(0, 3, 2, "Ekspor salinan (tidak terenkripsi)")
            pm.menu.add(0, 4, 3, "Hapus")
            pm.setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> edit(d)
                    2 -> launch(calIntent(d), 61)
                    3 -> export(d)
                    4 -> confirm("Hapus \"${d.title}\"?", "Dokumen dan filenya dihapus permanen dari perangkat.", "Hapus") {
                        Store.deleteDoc(this, d); finish()
                    }
                }
                true
            }
            pm.show()
        }
        hd.addView(more, lp(46, 46))
        col.addView(hd, lp(MATCH, WRAP, b = 18))

        val pf = FrameLayout(this)
        pf.background = cardBg(26); pf.clipToOutline = true; pf.soft(3)
        val iv = ImageView(this)
        iv.scaleType = ImageView.ScaleType.CENTER_CROP
        pf.addView(iv, FrameLayout.LayoutParams(MATCH, MATCH))
        Thumbs.doc(this, iv, d)
        Async.run {
            val b = try {
                if (d.isPdf) {
                    val t = Vault.decryptToTemp(this, d.file, "pdf")
                    try { Img.pdfFirstPage(t, 900) } finally { t.delete() }
                } else Img.decode({ Vault.open(this, d.file) }, 1200)
            } catch (t: Throwable) { null }
            Async.ui { if (b != null && myGen == gen) iv.setImageBitmap(b) }
        }
        val hint = tv("Ketuk untuk membuka", 12.5f, android.graphics.Color.WHITE, 1)
        hint.background = cardBg(14, 0x99000000.toInt())
        hint.setPadding(dp(12), dp(6), dp(12), dp(6))
        pf.addView(hint, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).also { it.bottomMargin = dp(12) })
        pf.pressable { openViewer(d) }
        col.addView(pf, lp(MATCH, 240))

        col.addView(badge(st.label, st.level), lp(WRAP, WRAP, t = 16))

        val info = vbox()
        info.background = cardBg(22); info.soft(1)
        info.setPadding(dp(16), dp(6), dp(16), dp(6))
        fun row(k: String, v: String, copy: Boolean = false) {
            if (v.isEmpty()) return
            val r = vbox()
            r.setPadding(0, dp(10), 0, dp(10))
            r.addView(tv(k, 12.5f, P.sub, 1))
            r.addView(tv(v, 16f, P.text, 1), lp(WRAP, WRAP, t = 3))
            if (copy) r.setOnLongClickListener {
                getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("", v)); toast("Disalin"); true
            }
            if (info.childCount > 0) info.addView(View(this).also { it.setBackgroundColor(P.line) }, lp(MATCH, 1))
            info.addView(r)
        }
        row("Pemilik", mem[d.member]?.name?.uppercase() ?: "")
        row("Kategori", Cat.names[d.cat])
        row("Nomor dokumen (tekan lama untuk menyalin)", d.num, true)
        row("Masa berlaku", if (d.lifetime) "Seumur hidup" else Fmt.date(d.expiry))
        row("Jenis berkas", if (d.isPdf) "PDF" else "Foto")
        row("Ditambahkan", Fmt.date(d.created / 86_400_000L))
        row("Catatan", d.notes)
        col.addView(info, lp(MATCH, WRAP, t = 14))

        val open = primaryButton("Buka dokumen")
        open.pressable { openViewer(d) }
        col.addView(open, lp(MATCH, WRAP, t = 18))
        val two = hbox()
        val e = ghostButton("\u270F\uFE0F  Edit"); e.pressable { edit(d) }
        two.addView(e, lp(0, WRAP, 1f, r = 6))
        if (!d.lifetime) {
            val c = ghostButton("\uD83D\uDCC5  Kalender"); c.pressable { launch(calIntent(d), 61) }
            two.addView(c, lp(0, WRAP, 1f, l = 6))
        }
        col.addView(two, lp(MATCH, WRAP, t = 12))

        sv.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        holder.addView(sv, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun edit(d: Doc) { startActivity(Intent(this, AddEditActivity::class.java).putExtra("id", d.id)) }
    private fun openViewer(d: Doc) { startActivity(Intent(this, ViewerActivity::class.java).putExtra("id", d.id)) }

    private fun calIntent(d: Doc): Intent = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.Events.TITLE, "Perpanjang: " + d.title)
        .putExtra(CalendarContract.Events.DESCRIPTION, "Masa berlaku " + d.title + " habis " + Fmt.date(d.expiry))
        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, d.expiry * 86_400_000L)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, d.expiry * 86_400_000L)

    private fun export(d: Doc) {
        val ext = if (d.isPdf) ".pdf" else ".jpg"
        val name = d.title.replace(Regex("[^A-Za-z0-9 _-]"), "_") + ext
        launch(
            Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType(d.mime).putExtra(Intent.EXTRA_TITLE, name), 21
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        val u = data?.data
        val d = doc
        if (rc == 21 && res == RESULT_OK && u != null && d != null) {
            Async.run {
                try {
                    val os = contentResolver.openOutputStream(u) ?: throw java.io.IOException("Tidak bisa menulis")
                    os.use { o -> Vault.open(this, d.file).use { it.copyTo(o) } }
                    Async.ui { toast("Salinan tersimpan") }
                } catch (e: Exception) { Async.ui { toast("Gagal: " + e.message) } }
            }
        }
    }
}
