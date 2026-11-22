package com.alhafidz.app

import android.app.TimePickerDialog
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.CalendarContract
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import java.time.LocalDate

class MainActivity : BaseActivity() {
    lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var fab: TextView
    private val icons = ArrayList<TextView>()
    private val labels = ArrayList<TextView>()
    var tab = 0
    var fCat = -1
    var fMember = -1L
    var fQuery = ""
    var remTab = 0
    private var pendingPw: CharArray? = null
    private var avatarFor: Member? = null
    private var lastT = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        tab = intent.getIntExtra("tab", 0)
        seed()
        buildShell()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.hasExtra("tab") && !Lock.locked) show(intent.getIntExtra("tab", 0))
    }

    override fun onResume() {
        super.onResume()
        if (!Lock.locked) { show(tab); askNotif() }
    }

    private fun seed() {
        if (Store.members(this).isEmpty()) Store.saveMember(this, Member(0, "Saya", null))
        val users = getSharedPreferences("arsipku_users", MODE_PRIVATE)
        if (!users.getBoolean("welcomed", false)) {
            val input = EditText(this).apply { hint = "Nama Anda"; setSingleLine(); setText("Saya") }
            AlertDialog.Builder(this).setTitle("Selamat datang di ArsipKu")
                .setMessage("Masukkan nama untuk sapaan di beranda.").setView(input)
                .setCancelable(false).setPositiveButton("Mulai") { _, _ ->
                    val n = input.text.toString().trim().ifBlank { "Saya" }
                    users.edit().putString("profiles", n).putString("active", n).putBoolean("welcomed", true).apply()
                    show(tab)
                }.show()
        } else if (users.getString("active", null).isNullOrBlank()) {
            val first = users.getString("profiles", "Saya")!!.split("\n").firstOrNull().orEmpty().ifBlank { "Saya" }
            users.edit().putString("active", first).apply()
        }
        Prefs.setBool(this, K.SEEDED, true)
    }

    fun manageProfiles() {
        val p = getSharedPreferences("arsipku_users", MODE_PRIVATE)
        val names = p.getString("profiles", "Saya")!!.split("\n").filter { it.isNotBlank() }.toMutableList()
        val options = names.toMutableList().apply { add("＋ Tambah pengguna") }
        AlertDialog.Builder(this).setTitle("Pilih pengguna")
            .setItems(options.toTypedArray()) { _, index ->
                if (index == names.size) {
                    val e = EditText(this).apply { hint = "Nama pengguna baru"; setSingleLine() }
                    AlertDialog.Builder(this).setTitle("Tambah pengguna").setView(e)
                        .setNegativeButton("Batal", null).setPositiveButton("Tambah") { _, _ ->
                            val n=e.text.toString().trim()
                            if(n.isNotEmpty() && !names.any { it.equals(n,true) }) { names.add(n); p.edit().putString("profiles",names.joinToString("\n")).putString("active",n).apply(); show(0) }
                        }.show()
                } else { p.edit().putString("active", names[index]).apply(); show(0) }
            }.show()
    }

    private fun askNotif() {
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.bool(this, K.ASKED, false) &&
            checkSelfPermission("android.permission.POST_NOTIFICATIONS") != PackageManager.PERMISSION_GRANTED
        ) {
            Prefs.setBool(this, K.ASKED, true)
            requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 51)
        }
    }

    private fun buildShell() {
        val root = FrameLayout(this)
        root.background = pageBg()
        content = FrameLayout(this)
        root.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))

        nav = LinearLayout(this)
        nav.orientation = LinearLayout.HORIZONTAL
        nav.gravity = Gravity.CENTER_VERTICAL
        nav.background = cardBg(32, P.cardSolid)
        nav.soft(8)
        nav.setPadding(dp(6), 0, dp(6), 0)
        val names = arrayOf("Beranda", "Cari", "Pengingat", "Profil")
        val emo = arrayOf("\uD83C\uDFE0", "\uD83D\uDD0D", "\uD83D\uDD14", "\uD83D\uDC64")
        val order = intArrayOf(0, 1, -1, 2, 3)
        for (o in order) {
            if (o < 0) { nav.addView(View(this), lp(78, 1)); continue }
            val item = LinearLayout(this)
            item.orientation = LinearLayout.VERTICAL
            item.gravity = Gravity.CENTER
            val ic = tv(emo[o], 22f)
            ic.gravity = Gravity.CENTER
            val lb = tv(names[o], 11f, P.sub, 1)
            icons.add(ic); labels.add(lb)
            item.addView(ic, lp(50, 32))
            item.addView(lb, lp(WRAP, WRAP, t = 2))
            item.pressable { show(o) }
            nav.addView(item, lp(0, MATCH, 1f))
        }
        root.addView(nav, FrameLayout.LayoutParams(MATCH, dp(68), Gravity.BOTTOM))

        fab = tv("+", 38f, Color.WHITE, 1)
        fab.gravity = Gravity.CENTER
        fab.background = grad(P.blue2, P.blueDark, dpf(40), GradientDrawable.Orientation.TOP_BOTTOM).also {
            it.shape = GradientDrawable.OVAL
            it.setStroke(dp(5), P.cardSolid)
        }
        fab.soft(10)
        fab.pressable { startActivity(Intent(this, AddEditActivity::class.java)) }
        root.addView(fab, FrameLayout.LayoutParams(dp(70), dp(70), Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL))

        setRoot(root) {
            val nl = nav.layoutParams as FrameLayout.LayoutParams
            nl.leftMargin = dp(14) + inL; nl.rightMargin = dp(14) + inR; nl.bottomMargin = inB + dp(12)
            nav.layoutParams = nl
            val fl = fab.layoutParams as FrameLayout.LayoutParams
            fl.bottomMargin = inB + dp(12) + dp(38)
            fab.layoutParams = fl
            content.setPadding(inL, 0, inR, 0)
            val kb = imeB > dp(100)
            nav.visibility = if (kb) View.GONE else View.VISIBLE
            fab.visibility = if (kb) View.GONE else View.VISIBLE
            if (inT != lastT) { lastT = inT; if (!Lock.locked) show(tab) }
        }
    }

    private fun updateNav() {
        val pos = intArrayOf(0, 1, 2, 3)
        for (i in pos) {
            val sel = i == tab
            icons[i].background = if (sel) shape(P.chipSel, dpf(16)) else null
            labels[i].setTextColor(if (sel) P.blue else P.sub)
        }
    }

    fun show(t: Int) {
        tab = t
        updateNav()
        content.removeAllViews()
        val v: View = when (t) { 0 -> buildHome(); 1 -> buildSearch(); 2 -> buildReminders(); else -> buildProfile() }
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    // ---------- aksi ----------
    fun openDoc(d: Doc) {
        startActivity(Intent(this, DetailActivity::class.java).putExtra("id", d.id))
    }

    fun addMember() {
        promptText("Anggota keluarga baru", "") { Store.saveMember(this, Member(0, it, null)); show(tab) }
    }

    fun memberMenu(m: Member) {
        choose(m.name.uppercase(), arrayOf("Ubah nama", "Ganti foto", "Hapus anggota")) {
            when (it) {
                0 -> promptText("Ubah nama", m.name) { n -> Store.saveMember(this, m.copy(name = n)); show(tab) }
                1 -> {
                    avatarFor = m
                    launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), 31)
                }
                else -> confirm("Hapus ${m.name}?", "Dokumen milik anggota ini tidak dihapus, hanya menjadi tanpa pemilik.", "Hapus") {
                    Store.deleteMember(this, m); show(tab)
                }
            }
        }
    }

    fun doBackup() {
        promptPassword(
            "Backup terenkripsi",
            "Backup dikunci dengan kata sandi ini (AES-256). Jika kata sandi lupa, backup TIDAK bisa dibuka.", true
        ) { pw ->
            pendingPw = pw
            val i = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/octet-stream").putExtra(Intent.EXTRA_TITLE, "ArsipKu-" + LocalDate.now() + ".alhbak")
            launch(i, 41)
        }
    }

    fun doRestore() {
        confirm("Restore dari backup", "Semua data saat ini akan DIGANTI dengan isi backup. Lanjutkan?", "Lanjut") {
            promptPassword("Kata sandi backup", "Masukkan kata sandi yang dipakai saat backup dibuat.", false) { pw ->
                pendingPw = pw
                launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), 42)
            }
        }
    }

    fun reminderSettings() {
        choose(
            "Pengaturan pengingat",
            arrayOf(
                "Mulai mengingatkan: ${Prefs.int(this, K.DAYS, 30)} hari sebelum habis",
                "Jam pengingat harian: ${Fmt.hour(Prefs.int(this, K.HOUR, 8))}",
                "Suara notifikasi: " + (if (Prefs.bool(this, K.SOUND, true)) "aktif" else "mati") + " \u2022 " + Sound.label(this),
                "Tes notifikasi sekarang"
            )
        ) {
            if (it == 2) { soundMenu(); return@choose }
            if (it == 3) { testNotif(); return@choose }
            if (it == 0) {
                val v = intArrayOf(7, 14, 30, 60, 90)
                choose("Mulai mengingatkan", Array(v.size) { i -> "${v[i]} hari sebelum" }) { j ->
                    Prefs.setInt(this, K.DAYS, v[j]); show(tab)
                }
            } else {
                TimePickerDialog(this, { _, h, _ ->
                    Prefs.setInt(this, K.HOUR, h); Reminder.schedule(this); show(tab)
                }, Prefs.int(this, K.HOUR, 8), 0, true).show()
            }
        }
    }

    fun soundMenu() {
        val on = Prefs.bool(this, K.SOUND, true)
        choose(
            "Suara pengingat (volume alarm)",
            arrayOf(if (on) "Matikan suara" else "Aktifkan suara", "Pakai suara bawaan", "Pilih berkas MP3 / WAV\u2026", "Tes suara")
        ) {
            when (it) {
                0 -> { Prefs.setBool(this, K.SOUND, !on); show(tab) }
                1 -> { Sound.clearCustom(this); toast("Memakai suara bawaan"); show(tab) }
                2 -> {
                    val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("audio/mpeg", "audio/mp3", "audio/wav", "audio/x-wav", "audio/wave", "audio/vnd.wave"))
                    launch(i, 71)
                }
                else -> { toast("Memutar\u2026 (volume mengikuti alarm)"); Async.run { Sound.play(this, true) } }
            }
        }
    }

    fun testNotif() {
        if (!Reminder.notifEnabled(this)) {
            toast("Notifikasi belum diizinkan")
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 51)
            else openNotifSettings()
            return
        }
        Async.run {
            val ok = Reminder.fire(this, true)
            if (ok) Sound.play(this)
            Async.ui { toast(if (ok) "Notifikasi ditampilkan" else "Gagal menampilkan notifikasi") }
        }
    }

    fun openNotifSettings() {
        try {
            launch(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName), 81)
        } catch (e: Exception) { toast("Buka Pengaturan > Aplikasi > ArsipKu > Notifikasi") }
    }

    fun batteryHelp() {
        choose(
            "Agar pengingat selalu muncul",
            arrayOf("Autostart (Xiaomi/Redmi)", "Baterai: pilih \"Tanpa batasan\"", "Info aplikasi", "Pengaturan notifikasi")
        ) {
            when (it) {
                0 -> {
                    val i = Intent().setClassName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    try { launch(i, 82) } catch (e: Exception) { toast("Cari menu Autostart di Keamanan / Info aplikasi") }
                }
                1 -> try { launch(Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), 83) }
                     catch (e: Exception) { toast("Buka Info aplikasi > Hemat baterai > Tanpa batasan") }
                2 -> launch(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")), 84)
                else -> openNotifSettings()
            }
        }
    }

    fun autoLockChoice() {
        val v = intArrayOf(0, 30, 60, 300)
        choose("Kunci otomatis", arrayOf("Segera", "Setelah 30 detik", "Setelah 1 menit", "Setelah 5 menit")) {
            Prefs.setInt(this, K.AUTOLOCK, v[it]); show(tab)
        }
    }

    fun calendarPicker() {
        val l = Store.docs(this).filter { !it.lifetime }
        if (l.isEmpty()) { toast("Belum ada dokumen dengan masa berlaku"); return }
        val items = Array(l.size) { l[it].title + " \u2022 " + Fmt.date(l[it].expiry) }
        choose("Masukkan ke Kalender", items) { toCalendar(l[it]) }
    }

    fun toCalendar(d: Doc) {
        if (d.lifetime) return
        val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
            .putExtra(CalendarContract.Events.TITLE, "Perpanjang: " + d.title)
            .putExtra(CalendarContract.Events.DESCRIPTION, "Masa berlaku " + d.title + " habis " + Fmt.date(d.expiry))
            .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
            .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, d.expiry * 86_400_000L)
            .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, d.expiry * 86_400_000L)
        launch(i, 61)
    }

    fun wipeAll() {
        confirm("Hapus SEMUA data?", "Seluruh dokumen, anggota, dan berkas akan dihapus permanen dari perangkat ini.", "Hapus semua") {
            Async.run { Store.wipe(this); Async.ui { toast("Semua data dihapus"); show(tab) } }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        val u = data?.data
        if (res != RESULT_OK || u == null) {
            if (rc == 41 || rc == 42) { pendingPw?.fill('0'); pendingPw = null }
            return
        }
        when (rc) {
            41 -> {
                val pw = pendingPw ?: return
                pendingPw = null
                toast("Membuat backup\u2026")
                Async.run {
                    try {
                        val os = contentResolver.openOutputStream(u) ?: throw java.io.IOException("Tidak bisa menulis berkas")
                        os.use { Backup.export(this, it, pw) }
                        Prefs.setLong(this, K.LAST_BACKUP, System.currentTimeMillis())
                        Async.ui { toast("Backup berhasil disimpan"); show(tab) }
                    } catch (e: Exception) {
                        Async.ui { toast("Backup gagal: " + e.message) }
                    } finally { pw.fill('0') }
                }
            }
            42 -> {
                val pw = pendingPw ?: return
                pendingPw = null
                toast("Memulihkan data\u2026")
                Async.run {
                    try {
                        val ins = contentResolver.openInputStream(u) ?: throw java.io.IOException("Tidak bisa membaca berkas")
                        val n = ins.use { Backup.restore(this, it, pw) }
                        Async.ui { toast("Restore berhasil: $n dokumen"); show(tab) }
                    } catch (e: Exception) {
                        Async.ui { toast(e.message ?: "Restore gagal") }
                    } finally { pw.fill('0') }
                }
            }
            71 -> {
                Async.run {
                    try {
                        val n = Sound.setCustom(this, u)
                        Prefs.setBool(this, K.SOUND, true)
                        Async.ui { toast("Suara diganti: $n"); show(tab) }
                    } catch (e: Throwable) { Async.ui { toast(e.msg()) } }
                }
            }
            31 -> {
                val m = avatarFor ?: return
                Async.run {
                    try {
                        val name = Vault.importAvatar(this, u)
                        Store.saveMember(this, m.copy(avatar = name))
                        m.avatar?.let { Vault.delete(this, it) }
                        Async.ui { show(tab) }
                    } catch (e: Exception) { Async.ui { toast("Gagal: " + e.message) } }
                }
            }
        }
    }
}
