package com.alhafidz.app

import android.app.DatePickerDialog
import android.content.ClipData
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.time.LocalDate

class AddEditActivity : BaseActivity() {
    private var editing: Doc? = null
    private var uri: Uri? = null
    private var isPdf = false
    private var cat = 0
    private var member = 0L
    private var expiry = 0L
    private var lifetime = false
    private var members: List<Member> = emptyList()
    private lateinit var title: EditText
    private lateinit var num: EditText
    private lateinit var notes: EditText
    private lateinit var preview: ImageView
    private lateinit var previewLabel: TextView
    private lateinit var dateBtn: TextView
    private lateinit var saveBtn: TextView
    private lateinit var catRow: LinearLayout
    private lateinit var memRow: LinearLayout
    private lateinit var scroll: ScrollView
    private val cap: File by lazy { File(File(cacheDir, "cap").also { it.mkdirs() }, "capture.jpg") }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra("id", 0L)
        val e = if (id != 0L) Store.doc(this, id) else null
        editing = e
        members = Store.members(this)
        cat = e?.cat ?: 0
        member = e?.member ?: members.firstOrNull()?.id ?: 0L
        lifetime = e?.lifetime ?: false
        expiry = if (e != null && !e.lifetime) e.expiry else Fmt.today() + 365

        val root = FrameLayout(this)
        root.background = pageBg()
        scroll = ScrollView(this)
        scroll.isVerticalScrollBarEnabled = false
        scroll.clipToPadding = false
        val col = vbox()
        col.setPadding(dp(20), 0, dp(20), 0)

        val hd = hbox()
        val back = tv("\u2039", 30f, P.text, 1)
        back.gravity = Gravity.CENTER
        back.background = cardBg(16, P.cardSolid); back.soft(2)
        back.pressable { finish() }
        hd.addView(back, lp(46, 46))
        hd.addView(tv(if (e == null) "Tambah Dokumen" else "Ubah Dokumen", 22f, P.text, 2), lp(0, WRAP, 1f, l = 14))
        col.addView(hd, lp(MATCH, WRAP, b = 18))

        // pratinjau file
        val pf = FrameLayout(this)
        pf.background = cardBg(24); pf.clipToOutline = true; pf.soft(2)
        preview = ImageView(this)
        preview.scaleType = ImageView.ScaleType.CENTER_CROP
        pf.addView(preview, FrameLayout.LayoutParams(MATCH, MATCH))
        previewLabel = tv(if (e == null) "\uD83D\uDCCE  Belum ada file\nPilih foto, kamera, atau PDF" else "File saat ini\nPilih file baru untuk mengganti", 14f, P.sub, 1)
        previewLabel.gravity = Gravity.CENTER
        pf.addView(previewLabel, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        if (e != null) { Thumbs.doc(this, preview, e); previewLabel.visibility = View.GONE }
        col.addView(pf, lp(MATCH, 190))

        val pick = hbox()
        val b1 = ghostButton("\uD83D\uDDBC  Foto"); b1.pressable { pickFile("image/*", 1) }
        val b2 = ghostButton("\uD83D\uDCF7  Kamera"); b2.pressable { camera() }
        val b3 = ghostButton("\uD83D\uDCC4  PDF"); b3.pressable { pickFile("application/pdf", 3) }
        pick.addView(b1, lp(0, WRAP, 1f, r = 6))
        pick.addView(b2, lp(0, WRAP, 1f, l = 3, r = 3))
        pick.addView(b3, lp(0, WRAP, 1f, l = 6))
        col.addView(pick, lp(MATCH, WRAP, t = 12))

        fun label(t: String) = tv(t, 14f, P.sub, 1).also { col.addView(it, lp(WRAP, WRAP, t = 18, b = 8, l = 2)) }

        label("Nama dokumen")
        title = input("mis. SIM A, KTP, STNK Motor")
        title.setText(e?.title ?: "")
        col.addView(title, lp(MATCH, WRAP))

        label("Kategori")
        catRow = LinearLayout(this)
        val hs1 = HorizontalScrollView(this)
        hs1.isHorizontalScrollBarEnabled = false
        hs1.addView(catRow)
        col.addView(hs1, lp(MATCH, WRAP))

        label("Pemilik")
        memRow = LinearLayout(this)
        val hs2 = HorizontalScrollView(this)
        hs2.isHorizontalScrollBarEnabled = false
        hs2.addView(memRow)
        col.addView(hs2, lp(MATCH, WRAP))
        renderChips()

        label("Nomor dokumen (opsional)")
        num = input("Nomor SIM / NIK / plat")
        num.setText(e?.num ?: "")
        col.addView(num, lp(MATCH, WRAP))

        label("Masa berlaku")
        val dr = hbox()
        dateBtn = tv("", 16f, P.text, 1)
        dateBtn.background = cardBg(16, P.cardSolid)
        dateBtn.setPadding(dp(16), dp(14), dp(16), dp(14))
        dateBtn.pressable { pickDate() }
        dr.addView(dateBtn, lp(0, WRAP, 1f))
        dr.addView(tv("Seumur hidup", 14f, P.text, 1), lp(WRAP, WRAP, l = 12, r = 8))
        dr.addView(sw(lifetime) { lifetime = it; refreshDate() })
        col.addView(dr, lp(MATCH, WRAP))
        refreshDate()

        label("Catatan (opsional)")
        notes = input("Catatan", true)
        notes.setText(e?.notes ?: "")
        col.addView(notes, lp(MATCH, WRAP))

        saveBtn = primaryButton(if (e == null) "Simpan dokumen" else "Simpan perubahan")
        saveBtn.pressable { save() }
        col.addView(saveBtn, lp(MATCH, WRAP, t = 26))

        scroll.addView(col, FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(scroll, FrameLayout.LayoutParams(MATCH, MATCH))
        setRoot(root) { scroll.setPadding(inL, inT + dp(14), inR, maxOf(inB, imeB) + dp(24)) }
    }

    private fun chip(label: String, sel: Boolean, lead: View?, onClick: () -> Unit): View {
        val h = hbox()
        h.setPadding(dp(14), dp(9), dp(16), dp(9))
        h.background = if (sel) shape(P.chipSel, dpf(18), P.blue, dp(1)) else cardBg(18, P.cardSolid)
        if (lead != null) h.addView(lead, lp(if (lead is TextView) WRAP else 26, if (lead is TextView) WRAP else 26, r = 8))
        h.addView(tv(label, 14f, P.text, 1))
        h.pressable(onClick)
        return h
    }

    private fun renderChips() {
        catRow.removeAllViews()
        for (i in Cat.names.indices) catRow.addView(chip(Cat.names[i], cat == i, tv(Cat.icon(i), 16f)) { cat = i; renderChips() }, lp(WRAP, WRAP, r = 8))
        memRow.removeAllViews()
        memRow.addView(chip("Tanpa pemilik", member == 0L, null) { member = 0L; renderChips() }, lp(WRAP, WRAP, r = 8))
        for (m in members) memRow.addView(chip(m.name.uppercase(), member == m.id, avatar(m, 26)) { member = m.id; renderChips() }, lp(WRAP, WRAP, r = 8))
    }

    private fun refreshDate() {
        dateBtn.text = if (lifetime) "\u221E  Seumur hidup" else "\uD83D\uDCC5  " + Fmt.date(expiry)
        dateBtn.alpha = if (lifetime) 0.5f else 1f
    }

    private fun pickDate() {
        if (lifetime) return
        val d = LocalDate.ofEpochDay(expiry)
        DatePickerDialog(this, { _, y, m, day -> expiry = LocalDate.of(y, m + 1, day).toEpochDay(); refreshDate() },
            d.year, d.monthValue - 1, d.dayOfMonth).show()
    }

    private fun pickFile(type: String, rc: Int) {
        launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(type), rc)
    }

    private fun camera() {
        cap.delete()
        val u = Uri.parse("content://com.alhafidz.app.capture/capture.jpg")
        val i = Intent(MediaStore.ACTION_IMAGE_CAPTURE).putExtra(MediaStore.EXTRA_OUTPUT, u)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        i.clipData = ClipData.newRawUri("", u)
        launch(i, 2)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        if (res != RESULT_OK) return
        when (rc) {
            1 -> { uri = data?.data; isPdf = false }
            2 -> { uri = Uri.fromFile(cap); isPdf = false }
            3 -> { uri = data?.data; isPdf = true }
        }
        val u = uri ?: return
        if (isPdf) {
            preview.setImageDrawable(null)
            previewLabel.visibility = View.VISIBLE
            previewLabel.text = "\uD83D\uDCC4  " + (nameOf(u) ?: "Dokumen PDF")
        } else {
            previewLabel.visibility = View.GONE
            Async.run {
                val b = try { Img.decodeUri(this, u, 700) } catch (t: Throwable) { null }
                Async.ui { if (b != null) preview.setImageBitmap(b) }
            }
        }
    }

    private fun nameOf(u: Uri): String? = try {
        contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
    } catch (e: Exception) { null }

    private fun save() {
        val t = title.text.toString().trim()
        if (t.isEmpty()) { toast("Nama dokumen wajib diisi"); return }
        val old = editing
        val u = uri
        if (old == null && u == null) { toast("Pilih foto atau PDF terlebih dahulu"); return }
        saveBtn.isEnabled = false
        saveBtn.text = "Menyimpan\u2026"
        val exp = if (lifetime) -1L else expiry
        val pdf = isPdf
        val n = num.text.toString().trim()
        val no = notes.text.toString().trim()
        Async.run {
            try {
                var file = old?.file ?: ""
                var mime = old?.mime ?: ""
                if (u != null) {
                    val r = Vault.importUri(this, u, pdf)
                    file = r.first; mime = r.second
                }
                val doc = Doc(old?.id ?: 0L, t, cat, member, n, no, exp, file, mime, old?.created ?: System.currentTimeMillis())
                Store.saveDoc(this, doc)
                if (u != null && old != null) Vault.delete(this, old.file)
                cap.delete()
                Async.ui { setResult(RESULT_OK); finish() }
            } catch (e: Throwable) {
                Async.ui {
                    toast("Gagal menyimpan: " + e.msg())
                    saveBtn.isEnabled = true
                    saveBtn.text = "Simpan"
                }
            }
        }
    }

    override fun onDestroy() { super.onDestroy(); cap.delete() }
}
