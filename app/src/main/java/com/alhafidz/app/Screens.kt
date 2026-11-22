package com.alhafidz.app

import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.Editable
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView

fun MainActivity.page(inner: LinearLayout): View {
    val sv = ScrollView(this)
    sv.isVerticalScrollBarEnabled = false
    inner.setPadding(dp(20), inT + dp(16), dp(20), dp(160))
    sv.addView(inner, FrameLayout.LayoutParams(MATCH, WRAP))
    return sv
}

private fun MainActivity.dueList(docs: List<Doc>): List<Pair<Doc, Status>> =
    docs.filter { !it.lifetime }.map { it to statusOf(this, it) }.filter { it.second.level == 0 }.sortedBy { it.second.days }

private fun owner(mem: Map<Long, Member>, d: Doc): String = mem[d.member]?.name?.uppercase() ?: ""

// =============================== BERANDA ===============================
fun MainActivity.buildHome(): View {
    val docs = Store.docs(this)
    val members = Store.members(this)
    val mem = members.associateBy { it.id }
    val due = dueList(docs)
    val col = vbox()
    val homeHead = hbox()
    homeHead.gravity = Gravity.CENTER_VERTICAL
    val activeUser = getSharedPreferences("arsipku_users", android.content.Context.MODE_PRIVATE).getString("active", "Saya") ?: "Saya"
    val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 4..10 -> "Selamat pagi"; in 11..14 -> "Selamat siang"; in 15..17 -> "Selamat sore"; else -> "Selamat malam" }
    val headTitle = vbox()
    headTitle.addView(tv("ArsipKu", 26f, P.text, 2))
    headTitle.addView(tv("$greeting, $activeUser!", 13f, P.sub), lp(WRAP, WRAP, t = 2))
    headTitle.pressable { manageProfiles() }
    homeHead.addView(headTitle, lp(0, WRAP, 1f))
    val userSwitch = tv("👤", 19f, P.text, 2).also { it.gravity=Gravity.CENTER; it.background=cardBg(16,P.cardSolid); it.setOnClickListener { manageProfiles() } }
    homeHead.addView(userSwitch, lp(42,42,r=6))
    val darkMode = P.night
    val themeToggle = tv(if (darkMode) "🌙" else "☀️", 22f, P.text, 2)
    themeToggle.gravity = Gravity.CENTER
    themeToggle.contentDescription = if (darkMode) "Ganti ke mode terang" else "Ganti ke mode gelap"
    themeToggle.background = cardBg(16, P.cardSolid)
    themeToggle.setPadding(dp(10), dp(7), dp(10), dp(7))
    themeToggle.pressable {
        Prefs.setInt(this, K.THEME, if (P.night) 0 else 1)
        recreate()
    }
    homeHead.addView(themeToggle, lp(46, 42))
    col.addView(homeHead, lp(MATCH, WRAP))
    col.addView(tv("Semua dokumen keluarga ada di sini.", 15f, P.sub), lp(WRAP, WRAP, t = 4, b = 18))

    // kartu status biru
    val card = FrameLayout(this)
    card.background = grad(0xFF2F63E8.toInt(), 0xFF3B82F6.toInt(), dpf(28))
    card.clipToOutline = true
    card.soft(6)
    val c1 = View(this); c1.background = oval(0x16FFFFFF)
    card.addView(c1, FrameLayout.LayoutParams(dp(170), dp(170), Gravity.END or Gravity.TOP).also { it.topMargin = dp(-60); it.rightMargin = dp(-40) })
    val c2 = View(this); c2.background = oval(0x12FFFFFF)
    card.addView(c2, FrameLayout.LayoutParams(dp(120), dp(120), Gravity.END or Gravity.BOTTOM).also { it.bottomMargin = dp(-50); it.rightMargin = dp(60) })
    val row = hbox()
    row.setPadding(dp(20), dp(22), dp(14), dp(22))
    val sh = ImageView(this)
    sh.setImageResource(R.drawable.ic_shield)
    row.addView(sh, lp(70, 78, r = 16))
    val txt = vbox()
    val last = Prefs.long(this, K.LAST_BACKUP, 0)
    val ps = SpannableString("\u25CF  " + if (last == 0L) "Belum pernah backup" else "Backup terakhir: " + Fmt.stamp(last))
    ps.setSpan(ForegroundColorSpan(if (last == 0L) 0xFFFFD166.toInt() else 0xFF4ADE80.toInt()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val pill = tv(ps, 12f, Color.WHITE, 1)
    pill.background = cardBg(20, 0x33FFFFFF)
    pill.setPadding(dp(12), dp(6), dp(12), dp(6))
    txt.addView(pill, lp(WRAP, WRAP))
    txt.addView(tv("Dokumen Anda Aman & Tertata", 20f, Color.WHITE, 2), lp(WRAP, WRAP, t = 10))
    txt.addView(tv("Terenkripsi di perangkat \u00B7 100% offline", 13f, 0xD9FFFFFF.toInt()), lp(WRAP, WRAP, t = 4))
    row.addView(txt, lp(0, WRAP, 1f))
    row.addView(tv("\u203A", 32f, Color.WHITE, 1), lp(WRAP, WRAP, l = 6))
    card.addView(row, FrameLayout.LayoutParams(MATCH, WRAP))
    card.pressable { show(3) }
    col.addView(card, lp(MATCH, WRAP))

    // dua kartu ringkas
    val two = hbox()
    two.gravity = Gravity.TOP
    val l = vbox()
    l.background = cardBg(24); l.soft(2)
    l.setPadding(dp(16), dp(16), dp(16), dp(16))
    val top = hbox()
    val ring = RingView(this)
    if (due.isNotEmpty()) {
        ring.color = P.lvRing(0); ring.track = 0x33E5484D; ring.num = due.size.toString(); ring.progress = due.first().second.progress
    } else {
        ring.color = P.lvRing(2); ring.track = 0x332DB551; ring.num = "\u2713"; ring.progress = 1f
    }
    top.addView(ring, lp(54, 54))
    top.addView(View(this), lp(0, 1, 1f))
    if (due.isNotEmpty()) {
        val cek = tv("Cek", 13f, P.lvText(0), 2)
        cek.background = cardBg(14, P.lvBadge(0))
        cek.setPadding(dp(14), dp(6), dp(14), dp(6))
        top.addView(cek)
    }
    l.addView(top)
    l.addView(tv("Perlu diperpanjang", 17f, P.text, 2), lp(WRAP, WRAP, t = 12))
    val sub = if (due.isEmpty()) "Tidak ada yang perlu diperpanjang" else {
        val (d, s) = due.first()
        val o = owner(mem, d)
        d.title + (if (o.isNotEmpty()) " $o" else "") + " \u00B7 " + if (s.days < 0) "sudah habis" else if (s.days == 0L) "hari ini" else "${s.days} hari lagi"
    }
    l.addView(tv(sub, 13f, P.sub), lp(WRAP, WRAP, t = 4))
    l.pressable { show(2) }
    l.minimumHeight = dp(172)
    l.minimumWidth = 0
    two.addView(l, lp(0, WRAP, 1f, r = 7, t = 14))

    val r = vbox()
    r.background = cardBg(24); r.soft(2)
    r.setPadding(dp(16), dp(16), dp(16), dp(16))
    r.minimumHeight = dp(172)
    r.minimumWidth = 0
    r.addView(tv("\uD83D\uDCC1", 36f))
    r.addView(tv(docs.size.toString(), 34f, P.text, 2).also { it.setPadding(0, dp(4), 0, dp(4)) }, lp(WRAP, WRAP, t = 6))
    r.addView(tv("Total dokumen", 14f, P.sub, 1), lp(WRAP, WRAP, t = 2))
    r.addView(tv("${members.size} anggota", 14f, P.sub), lp(WRAP, WRAP, t = 2))
    r.pressable { fCat = -1; fMember = -1; show(1) }
    two.addView(r, lp(0, WRAP, 1f, l = 7, t = 14))
    col.addView(two, lp(MATCH, WRAP))

    // kategori
    val hdr = hbox()
    hdr.addView(tv("Kategori", 20f, P.text, 2), lp(0, WRAP, 1f))
    val all = tv("Semua dokumen \u203A", 14f, P.blue, 2)
    all.pressable { fCat = -1; fMember = -1; show(1) }
    hdr.addView(all)
    col.addView(hdr, lp(MATCH, WRAP, t = 26, b = 14))

    for (rr in 0 until 2) {
        val rw = hbox()
        rw.gravity = Gravity.TOP
        for (cc in 0 until 3) {
            val i = rr * 3 + cc
            val n = docs.count { it.cat == i }
            val k = vbox()
            k.gravity = Gravity.CENTER_HORIZONTAL
            k.background = grad(P.card, Cat.tint(i), dpf(22), GradientDrawable.Orientation.TOP_BOTTOM)
            k.soft(1)
            k.setPadding(dp(8), dp(16), dp(8), dp(16))
            k.addView(tv(Cat.icon(i), 36f))
            k.addView(tv(Cat.names[i], 15f, P.text, 2), lp(WRAP, WRAP, t = 8))
            k.addView(tv("$n dokumen", 12f, P.sub), lp(WRAP, WRAP, t = 3))
            k.pressable { fCat = i; fMember = -1; show(1) }
            rw.addView(k, lp(0, WRAP, 1f, l = if (cc == 0) 0 else 6, r = if (cc == 2) 0 else 6))
        }
        col.addView(rw, lp(MATCH, WRAP, b = 12))
    }
    if (docs.isEmpty()) {
        col.addView(tv("Ketuk tombol + untuk menambah dokumen pertama Anda.", 14f, P.sub).also { it.gravity = Gravity.CENTER }, lp(MATCH, WRAP, t = 10))
    }
    val garage = vbox()
    garage.background = cardBg(22, P.cardSolid); garage.soft(2)
    garage.setPadding(dp(16), dp(14), dp(16), dp(14))
    garage.addView(tv("🚗  Kendaraan & Garasi", 18f, P.text, 2))
    garage.addView(tv("Daftar kendaraan, servis, isi BBM, kilometer, pajak, dan laporan pengeluaran.", 13f, P.sub), lp(WRAP, WRAP, t=5))
    garage.addView(tv("Buka manajemen kendaraan  ›", 14f, P.blue, 2), lp(WRAP, WRAP, t=10))
    garage.pressable { startActivity(android.content.Intent(this, VehicleActivity::class.java)) }
    col.addView(garage, lp(MATCH, WRAP, t=18))
    return page(col)
}

// =============================== CARI ===============================
fun MainActivity.docRow(d: Doc, mem: Map<Long, Member>): View {
    val st = statusOf(this, d)
    val row = hbox()
    row.background = cardBg(22); row.soft(2)
    row.setPadding(dp(12), dp(12), dp(8), dp(12))
    val iv = ImageView(this)
    iv.scaleType = ImageView.ScaleType.CENTER_CROP
    iv.background = shape(P.line, dpf(16))
    iv.clipToOutline = true
    Thumbs.doc(this, iv, d)
    row.addView(iv, lp(64, 64))
    val mid = vbox()
    mid.addView(tv(d.title, 17f, P.text, 2).also { it.maxLines = 1; it.ellipsize = android.text.TextUtils.TruncateAt.END })
    val o = owner(mem, d)
    mid.addView(tv((if (o.isNotEmpty()) "$o \u00B7 " else "") + Cat.names[d.cat], 13f, P.sub).also { it.maxLines = 1 }, lp(WRAP, WRAP, t = 3))
    mid.addView(badge(if (d.lifetime) st.label else if (st.level == 0) st.label else st.label, st.level), lp(WRAP, WRAP, t = 8))
    row.addView(mid, lp(0, WRAP, 1f, l = 12))
    val more = tv("\u22EE", 22f, P.sub, 2)
    more.setPadding(dp(12), dp(10), dp(10), dp(10))
    more.pressable {
        val pm = PopupMenu(this, more)
        pm.menu.add(0, 1, 0, "Lihat")
        pm.menu.add(0, 2, 1, "Edit")
        if (!d.lifetime) pm.menu.add(0, 3, 2, "Masukkan ke Kalender")
        pm.menu.add(0, 4, 3, "Hapus")
        pm.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> openDoc(d)
                2 -> startActivity(android.content.Intent(this, AddEditActivity::class.java).putExtra("id", d.id))
                3 -> toCalendar(d)
                4 -> confirm("Hapus \"${d.title}\"?", "Dokumen dan filenya dihapus permanen dari perangkat.", "Hapus") {
                    Store.deleteDoc(this, d); show(tab)
                }
            }
            true
        }
        pm.show()
    }
    row.addView(more)
    row.pressable { openDoc(d) }
    return row
}

fun MainActivity.buildSearch(): View {
    val docs = Store.docs(this)
    val members = Store.members(this)
    val mem = members.associateBy { it.id }
    val col = vbox()

    val bar = hbox()
    bar.background = cardBg(26, P.cardSolid); bar.soft(2)
    bar.setPadding(dp(18), 0, dp(12), 0)
    bar.addView(tv("\uD83D\uDD0D", 18f))
    val et = EditText(this)
    et.hint = "Cari dokumen\u2026"
    et.setHintTextColor(P.sub); et.setTextColor(P.text)
    et.textSize = 16f
    et.setSingleLine()
    et.background = null
    et.setText(fQuery)
    bar.addView(et, lp(0, 52, 1f, l = 10))
    col.addView(bar, lp(MATCH, 52))

    col.addView(tv("Kategori", 14f, P.sub, 1), lp(WRAP, WRAP, t = 18, b = 8, l = 2))
    val catRow = LinearLayout(this)
    val hs1 = HorizontalScrollView(this)
    hs1.isHorizontalScrollBarEnabled = false
    hs1.addView(catRow)
    col.addView(hs1, lp(MATCH, WRAP))

    col.addView(tv("Anggota keluarga", 14f, P.sub, 1), lp(WRAP, WRAP, t = 16, b = 8, l = 2))
    val memRow = LinearLayout(this)
    val hs2 = HorizontalScrollView(this)
    hs2.isHorizontalScrollBarEnabled = false
    hs2.addView(memRow)
    col.addView(hs2, lp(MATCH, WRAP))

    val header = tv("", 14f, P.sub, 1)
    col.addView(header, lp(WRAP, WRAP, t = 20, b = 10, l = 2))
    val listBox = vbox()
    col.addView(listBox, lp(MATCH, WRAP))

    fun chip(label: String, lead: View?, sel: Boolean, onClick: () -> Unit): View {
        val h = hbox()
        h.setPadding(dp(14), dp(9), dp(16), dp(9))
        h.background = if (sel) shape(P.chipSel, dpf(18), P.blue, dp(1)) else cardBg(18, P.cardSolid)
        if (lead != null) h.addView(lead, lp(if (lead is TextView) WRAP else 26, if (lead is TextView) WRAP else 26, r = 8))
        h.addView(tv(label, 14f, P.text, 1))
        h.pressable(onClick)
        return h
    }

    fun fill() {
        catRow.removeAllViews()
        for (i in Cat.names.indices) {
            catRow.addView(chip(Cat.names[i], tv(Cat.icon(i), 16f), fCat == i) { fCat = if (fCat == i) -1 else i; fill() }, lp(WRAP, WRAP, r = 8))
        }
        memRow.removeAllViews()
        for (m in members) {
            memRow.addView(chip(m.name.uppercase(), avatar(m, 26), fMember == m.id) { fMember = if (fMember == m.id) -1 else m.id; fill() }, lp(WRAP, WRAP, r = 8))
        }
        listBox.removeAllViews()
        val q = fQuery.trim().lowercase()
        val res = docs.filter {
            (fCat < 0 || it.cat == fCat) && (fMember < 0 || it.member == fMember) &&
                (q.isEmpty() || it.title.lowercase().contains(q) || it.num.lowercase().contains(q) ||
                    (mem[it.member]?.name ?: "").lowercase().contains(q) || Cat.names[it.cat].lowercase().contains(q))
        }
        header.text = "Semua dokumen (${res.size})"
        if (res.isEmpty()) listBox.addView(tv("Tidak ada dokumen.", 14f, P.sub).also { it.gravity = Gravity.CENTER }, lp(MATCH, WRAP, t = 24))
        res.forEach { listBox.addView(docRow(it, mem), lp(MATCH, WRAP, b = 12)) }
    }
    et.addTextChangedListener(object : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        override fun afterTextChanged(s: Editable?) { fQuery = s?.toString() ?: ""; fill() }
    })
    fill()
    return page(col)
}

// =============================== PENGINGAT ===============================
fun MainActivity.buildReminders(): View {
    val docs = Store.docs(this)
    val mem = Store.members(this).associateBy { it.id }
    val stats = docs.filter { !it.lifetime }.map { it to statusOf(this, it) }.sortedBy { it.second.days }
    val expired = stats.filter { it.second.level == 0 }
    val upcoming = stats.filter { it.second.level != 0 }
    val col = vbox()

    val head = hbox()
    head.addView(tv("\uD83D\uDD14", 30f), lp(WRAP, WRAP, r = 10))
    head.addView(tv("Pengingat", 28f, P.text, 2), lp(0, WRAP, 1f))
    val gear = tv("\u2699\uFE0F", 20f)
    gear.gravity = Gravity.CENTER
    gear.background = cardBg(16, P.cardSolid); gear.soft(2)
    gear.pressable { reminderSettings() }
    head.addView(gear, lp(46, 46))
    col.addView(head, lp(MATCH, WRAP, b = 16))

    val seg = hbox()
    seg.background = cardBg(24, P.card)
    seg.setPadding(dp(4), dp(4), dp(4), dp(4))
    val labels = arrayOf("Semua", "Kadaluarsa (${expired.size})", "Mendatang")
    val listBox = vbox()
    fun fillList() {
        seg.removeAllViews()
        for (i in 0 until 3) {
            val t = tv(labels[i], 14f, if (remTab == i) Color.WHITE else P.text, 2)
            t.gravity = Gravity.CENTER
            t.setPadding(dp(4), dp(12), dp(4), dp(12))
            t.maxLines = 1
            if (remTab == i) t.background = grad(P.blue2, P.blueDark, dpf(20), GradientDrawable.Orientation.TOP_BOTTOM)
            t.pressable { remTab = i; fillList() }
            seg.addView(t, lp(0, WRAP, 1f))
        }
        listBox.removeAllViews()
        val items = when (remTab) { 1 -> expired; 2 -> upcoming; else -> stats }
        if (items.isEmpty()) {
            listBox.addView(tv(if (stats.isEmpty()) "Belum ada dokumen dengan masa berlaku." else "Tidak ada pengingat \uD83C\uDF89", 14f, P.sub).also { it.gravity = Gravity.CENTER }, lp(MATCH, WRAP, t = 20, b = 10))
        }
        for ((d, s) in items) {
            val rw = hbox()
            rw.background = shape(P.lvCard(s.level), dpf(24), P.lvStroke(s.level), dp(1))
            rw.setPadding(dp(14), dp(14), dp(14), dp(14))
            val iv = ImageView(this)
            iv.scaleType = ImageView.ScaleType.CENTER_CROP
            iv.background = shape(P.line, dpf(14)); iv.clipToOutline = true
            Thumbs.doc(this, iv, d)
            rw.addView(iv, lp(64, 64))
            val mid = vbox()
            mid.addView(tv(d.title, 18f, P.text, 2).also { it.maxLines = 1 })
            mid.addView(tv(owner(mem, d), 13f, P.sub).also { it.maxLines = 1 }, lp(WRAP, WRAP, t = 3))
            mid.addView(tv("Habis " + Fmt.date(d.expiry), 14f, P.text, 1), lp(WRAP, WRAP, t = 6))
            rw.addView(mid, lp(0, WRAP, 1f, l = 12))
            val ring = RingView(this)
            ring.color = P.lvRing(s.level); ring.track = (P.lvRing(s.level) and 0x00FFFFFF) or 0x33000000
            ring.num = s.num; ring.unit = s.unit; ring.progress = s.progress
            rw.addView(ring, lp(58, 58))
            rw.pressable { openDoc(d) }
            listBox.addView(rw, lp(MATCH, WRAP, b = 12))
        }
    }
    col.addView(seg, lp(MATCH, WRAP, b = 16))
    col.addView(listBox, lp(MATCH, WRAP))
    fillList()

    // pengingat otomatis
    val auto = hbox()
    auto.background = shape(P.lvCard(2), dpf(24), P.lvStroke(2), dp(1))
    auto.setPadding(dp(14), dp(14), dp(14), dp(14))
    val bell = tv("\uD83D\uDD14", 22f)
    bell.gravity = Gravity.CENTER
    bell.background = grad(0xFF4ADE80.toInt(), 0xFF16A34A.toInt(), dpf(16))
    auto.addView(bell, lp(50, 50))
    val at = vbox()
    at.addView(tv("Pengingat otomatis", 16f, P.text, 2))
    at.addView(tv("Muncul ${Prefs.int(this, K.DAYS, 30)} hari sebelum dokumen habis", 13f, P.sub), lp(WRAP, WRAP, t = 3))
    auto.addView(at, lp(0, WRAP, 1f, l = 12, r = 8))
    auto.addView(sw(Prefs.bool(this, K.NOTIF, true)) { on ->
        Prefs.setBool(this, K.NOTIF, on)
        Reminder.schedule(this)
        if (on && Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 51)
    })
    col.addView(auto, lp(MATCH, WRAP, t = 8, b = 12))

    val cal = ghostButton("\uD83D\uDCC5  Atur waktu & masukkan ke Kalender")
    cal.soft(1)
    cal.pressable {
        choose("Atur pengingat", arrayOf("Masukkan dokumen ke Kalender", "Atur hari & jam pengingat")) {
            if (it == 0) calendarPicker() else reminderSettings()
        }
    }
    col.addView(cal, lp(MATCH, WRAP))
    return page(col)
}

// =============================== PROFIL ===============================
private fun MainActivity.srow(lead: View, title: String, sub: String?, trail: View?, onClick: (() -> Unit)?, danger: Boolean = false): View {
    val r = hbox()
    r.setPadding(dp(14), dp(12), dp(14), dp(12))
    r.addView(lead, lp(36, 36, r = 12))
    val t = vbox()
    t.addView(tv(title, 15.5f, if (danger) P.danger else P.text, 1))
    if (sub != null) t.addView(tv(sub, 12.5f, P.sub), lp(WRAP, WRAP, t = 2))
    r.addView(t, lp(0, WRAP, 1f))
    if (trail != null) r.addView(trail, lp(WRAP, WRAP, l = 8))
    if (onClick != null) r.pressable(onClick)
    return r
}

private fun MainActivity.emo(s: String): View = tv(s, 22f).also { it.gravity = Gravity.CENTER }

private fun MainActivity.section(title: String, rows: List<View>): View {
    val w = vbox()
    w.addView(tv(title.uppercase(), 12f, P.sub, 2), lp(WRAP, WRAP, l = 6, t = 20, b = 8))
    val c = vbox()
    c.background = cardBg(22); c.soft(1)
    rows.forEachIndexed { i, v ->
        if (i > 0) c.addView(View(this).also { it.setBackgroundColor(P.line) }, lp(MATCH, 1, l = 14, r = 14))
        c.addView(v)
    }
    w.addView(c, lp(MATCH, WRAP))
    return w
}

fun MainActivity.buildProfile(): View {
    val members = Store.members(this)
    val docs = Store.docs(this)
    val col = vbox()

    val head = hbox()
    val sh = ImageView(this)
    sh.setImageResource(R.drawable.ic_shield)
    head.addView(sh, lp(52, 58, r = 14))
    val ht = vbox()
    ht.addView(tv("ArsipKu", 26f, P.text, 2))
    ht.addView(tv("Penyimpanan dokumen pribadi \u00B7 v1.0.0", 13f, P.sub), lp(WRAP, WRAP, t = 2))
    head.addView(ht, lp(0, WRAP, 1f))
    col.addView(head)

    // anggota
    val mrows = ArrayList<View>()
    for (m in members) {
        val n = docs.count { it.member == m.id }
        mrows.add(srow(avatar(m, 36), m.name.uppercase(), "$n dokumen", tv("\u22EF", 20f, P.sub, 2), { memberMenu(m) }))
    }
    mrows.add(srow(emo("\u2795"), "Tambah anggota keluarga", null, null, { addMember() }))
    col.addView(section("Anggota keluarga", mrows))

    // keamanan
    val srows = ArrayList<View>()
    srows.add(srow(emo("\uD83D\uDD22"), "Ubah PIN", "PIN 6 digit untuk membuka aplikasi", null, { LockActivity.change(this) }))
    if (Build.VERSION.SDK_INT >= 29) {
        srows.add(srow(emo("\uD83D\uDC46"), "Buka dengan sidik jari / wajah", null,
            sw(Prefs.bool(this, K.BIO, true)) { Prefs.setBool(this, K.BIO, it) }, null))
    }
    val al = Prefs.int(this, K.AUTOLOCK, 60)
    srows.add(srow(emo("\u23F1\uFE0F"), "Kunci otomatis",
        if (al == 0) "Segera saat keluar aplikasi" else if (al < 60) "Setelah $al detik" else "Setelah ${al / 60} menit", null, { autoLockChoice() }))
    srows.add(srow(emo("\uD83D\uDEE1\uFE0F"), "Blokir screenshot & preview app", "Layar disembunyikan di Recent Apps",
        sw(Prefs.bool(this, K.SECURE, true)) { Prefs.setBool(this, K.SECURE, it); toast("Berlaku setelah aplikasi dibuka ulang") }, null))
    srows.add(srow(emo("\uD83D\uDE48"), "Sembunyikan isi notifikasi", "Notifikasi tidak menampilkan nama dokumen",
        sw(Prefs.bool(this, K.HIDE, false)) { Prefs.setBool(this, K.HIDE, it) }, null))
    srows.add(srow(emo("\uD83E\uDDE8"), "Hapus data setelah 10x PIN salah", "Opsional, hati-hati",
        sw(Prefs.bool(this, K.WIPE, false)) { Prefs.setBool(this, K.WIPE, it) }, null))
    col.addView(section("Keamanan", srows))

    // pengingat
    val rrows = ArrayList<View>()
    if (!Reminder.notifEnabled(this)) {
        rrows.add(srow(emo("\u26A0\uFE0F"), "Notifikasi belum diizinkan", "Ketuk untuk membuka pengaturan notifikasi", null, { openNotifSettings() }, true))
    }
    rrows.add(srow(emo("\uD83D\uDD14"), "Pengingat otomatis", null,
        sw(Prefs.bool(this, K.NOTIF, true)) { Prefs.setBool(this, K.NOTIF, it); Reminder.schedule(this) }, null))
    rrows.add(srow(emo("\u23F0"), "Waktu pengingat", "${Prefs.int(this, K.DAYS, 30)} hari sebelum habis \u00B7 jam ${Fmt.hour(Prefs.int(this, K.HOUR, 8))} setiap hari", null, { reminderSettings() }))
    rrows.add(srow(emo("\uD83D\uDD0A"), "Suara notifikasi", "Volume & aturan mengikuti alarm",
        sw(Prefs.bool(this, K.SOUND, true)) { Prefs.setBool(this, K.SOUND, it) }, null))
    rrows.add(srow(emo("\uD83C\uDFB5"), "Pilih suara", Sound.label(this), null, { soundMenu() }))
    rrows.add(srow(emo("\uD83D\uDCE8"), "Tes notifikasi sekarang", "Menampilkan notifikasi + suara contoh", null, { testNotif() }))
    rrows.add(srow(emo("\uD83D\uDD0B"), "Agar pengingat selalu jalan", "Xiaomi/HP lain: izinkan autostart & baterai tanpa batasan", null, { batteryHelp() }))
    rrows.add(srow(emo("\uD83E\uDDE9"), "Sembunyikan nama dokumen di widget", "Widget hanya menampilkan kata \"Dokumen\"",
        sw(Prefs.bool(this, K.WHIDE, false)) { Prefs.setBool(this, K.WHIDE, it); ExpiryWidget.refresh(this) }, null))
    col.addView(section("Pengingat & widget", rrows))

    // backup
    val last = Prefs.long(this, K.LAST_BACKUP, 0)
    col.addView(section("Backup & restore", listOf(
        srow(emo("\uD83D\uDCE4"), "Backup ke berkas", if (last == 0L) "Belum pernah backup" else "Terakhir: " + Fmt.stamp(last), null, { doBackup() }),
        srow(emo("\uD83D\uDCE5"), "Restore dari berkas", "Berkas .alhbak + kata sandi", null, { doRestore() })
    )))

    // privasi
    val perms = try {
        packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS).requestedPermissions ?: emptyArray()
    } catch (e: Exception) { emptyArray<String>() }
    val net = perms.any { it == "android.permission.INTERNET" || it == "android.permission.ACCESS_NETWORK_STATE" }
    col.addView(section("Privasi", listOf(
        srow(emo(if (net) "\u26A0\uFE0F" else "\u2705"), "Akses internet: " + if (net) "ADA" else "Tidak ada",
            "Aplikasi tidak meminta izin jaringan. Data tidak pernah keluar dari perangkat ini.", null, null),
        srow(emo("\uD83D\uDD10"), "Enkripsi AES-256-GCM", "File, thumbnail, dan data dokumen dienkripsi; kunci dilindungi Android Keystore.", null, null)
    )))

    col.addView(section("Zona bahaya", listOf(
        srow(emo("\uD83D\uDDD1\uFE0F"), "Hapus semua data", "Menghapus semua dokumen & anggota", null, { wipeAll() }, true)
    )))

    val foot = tv("\u00A9 2026 Rizal Alhafidz", 13f, P.sub, 1)
    foot.gravity = Gravity.CENTER
    col.addView(foot, lp(MATCH, WRAP, t = 28))
    return page(col)
}
