@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.alhafidz.app

import android.Manifest
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

@Composable
fun ReportTab(nav: Nav, pad: PaddingValues) {
    val ctx = LocalContext.current
    val vehicles by Repo.vehicles.collectAsState()
    val expenses by Repo.expenses.collectAsState()
    var mode by remember { mutableIntStateOf(0) }
    var ym by remember { mutableStateOf(YearMonth.now()) }
    var yr by remember { mutableIntStateOf(LocalDate.now().year) }
    var from by remember { mutableLongStateOf(LocalDate.now().withDayOfMonth(1).ms()) }
    var to by remember { mutableLongStateOf(LocalDate.now().ms()) }
    var vf by remember { mutableStateOf<String?>(null) }
    val cs = MaterialTheme.colorScheme

    val filtered = remember(expenses, mode, ym, yr, from, to, vf) {
        expenses.filter { e ->
            (vf == null || e.vehicleId == vf) && when (mode) {
                0 -> YearMonth.from(e.date.toLocal()) == ym
                1 -> e.date.toLocal().year == yr
                2 -> true
                else -> e.date >= from && e.date < to.toLocal().plusDays(1).ms()
            }
        }.sortedByDescending { it.date }
    }
    val total = filtered.sumOf { it.amount }
    val byCat = Cat.all.map { c -> c to filtered.filter { it.category == c }.sumOf { it.amount } }.filter { it.second > 0 }
    val months = filtered.groupBy { YearMonth.from(it.date.toLocal()) }.toSortedMap().entries.toList().takeLast(12)

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Laporan Keuangan", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f))
                RoundIcon(Icons.Filled.Search) { nav.go(Route.Search) }
            }
        }
        item { Toggle(listOf("Bulan", "Tahun", "Semua", "Rentang"), mode) { mode = it } }
        item {
            when (mode) {
                0 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.Filled.ChevronLeft, null) }
                    Text(ym.month.getDisplayName(TextStyle.FULL, ID_LOCALE) + " " + ym.year, Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { ym = ym.plusMonths(1) }) { Icon(Icons.Filled.ChevronRight, null) }
                }
                1 -> Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { yr-- }) { Icon(Icons.Filled.ChevronLeft, null) }
                    Text("$yr", Modifier.weight(1f), fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    IconButton(onClick = { yr++ }) { Icon(Icons.Filled.ChevronRight, null) }
                }
                3 -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateField("Dari", from, { from = it }, Modifier.weight(1f))
                    DateField("Sampai", to, { to = it }, Modifier.weight(1f))
                }
                else -> Text("Seluruh riwayat pengeluaran", color = cs.onSurfaceVariant)
            }
        }
        item { Choice(listOf("" to "Semua kendaraan") + vehicles.map { it.id to it.name }, vf ?: "") { vf = it.ifBlank { null } } }
        item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Total pengeluaran", fontSize = 13.sp, color = cs.onSurfaceVariant)
                    Text(rupiah(total), fontSize = 30.sp, fontWeight = FontWeight.ExtraBold)
                    Text("${filtered.size} transaksi", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    if (byCat.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(Modifier.size(120.dp)) {
                                val sw = 22.dp.toPx()
                                val s = Size(size.width - sw, size.height - sw)
                                val tl = Offset(sw / 2, sw / 2)
                                var start = -90f
                                byCat.forEach { (c, v) ->
                                    val sweep = 360f * v / total
                                    drawArc(Cat.color(c), start, (sweep - 1.5f).coerceAtLeast(0.5f), false, tl, s, style = Stroke(sw))
                                    start += sweep
                                }
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                byCat.forEach { (c, v) ->
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(Cat.color(c)))
                                        Spacer(Modifier.width(6.dp))
                                        Text("${Cat.label(c)} ${(100 * v / total)}% · ${rupiah(v)}", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (mode != 0 && months.size > 1) item {
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Per bulan", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    val sums = months.map { it.key to it.value.sumOf { e -> e.amount } }
                    val mx = (sums.maxOf { it.second }).coerceAtLeast(1L)
                    Row(Modifier.fillMaxWidth().height(150.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        sums.forEach { (m, s) ->
                            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                                Box(Modifier.fillMaxWidth(0.7f).height((110f * s / mx).dp.coerceAtLeast(3.dp)).clip(RoundedCornerShape(5.dp)).background(cs.primary))
                                Text("%02d".format(m.monthValue), fontSize = 10.sp, color = cs.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Transaksi (${filtered.size})", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    if (filtered.isEmpty()) { toast(ctx, "Tidak ada data"); return@TextButton }
                    val sb = StringBuilder("Tanggal,Kendaraan,Kategori,Keterangan,Nominal,KM,Liter\n")
                    filtered.forEach { e ->
                        fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
                        sb.append(listOf(fmtDate(e.date), q(Repo.vehicleName(e.vehicleId)), Cat.label(e.category), q(e.title), e.amount, e.odo, e.liters).joinToString(",")).append('\n')
                    }
                    val f = File(ctx.cacheDir, "laporan_garasiku.csv"); f.writeText(sb.toString())
                    val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                    val i = Intent(Intent.ACTION_SEND).setType("text/csv").putExtra(Intent.EXTRA_STREAM, u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    ctx.startActivity(Intent.createChooser(i, "Ekspor laporan"))
                }) { Icon(Icons.Filled.Share, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Ekspor CSV") }
            }
        }
        items(filtered, key = { it.id }) { e ->
            ExpenseRow(e, Repo.vehicleName(e.vehicleId)) { nav.go(Route.ExpenseForm(e.vehicleId, e.id, e.category)) }
        }
    }
}

@Composable
fun SearchScreen(nav: Nav) {
    val vehicles by Repo.vehicles.collectAsState()
    val expenses by Repo.expenses.collectAsState()
    val docs by Repo.docs.collectAsState()
    var q by remember { mutableStateOf("") }
    val s = q.trim().lowercase()
    val vs = if (s.isEmpty()) emptyList() else vehicles.filter { (it.name + " " + it.plate).lowercase().contains(s) }
    val es = if (s.isEmpty()) emptyList() else expenses.filter {
        (it.title + " " + it.note + " " + Cat.label(it.category) + " " + Repo.vehicleName(it.vehicleId) + " " + it.amount).lowercase().contains(s)
    }.sortedByDescending { it.date }.take(50)
    val ds = if (s.isEmpty()) emptyList() else docs.filter { (it.name + " " + it.number + " " + it.note + " " + DocT.label(it.type)).lowercase().contains(s) }
    val cs = MaterialTheme.colorScheme
    Scaffold(containerColor = cs.background, topBar = { TopBar("Pencarian", { nav.back() }) }) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(16.dp),
                    placeholder = { Text("Cari kendaraan, plat, sparepart, nominal, dokumen…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, null) } })
            }
            if (s.isNotEmpty() && vs.isEmpty() && es.isEmpty() && ds.isEmpty()) item { Text("Tidak ditemukan.", color = cs.onSurfaceVariant) }
            if (vs.isNotEmpty()) item { SectionLabel("Kendaraan") }
            items(vs, key = { "v" + it.id }) { v ->
                AppCard(Modifier.fillMaxWidth(), onClick = { nav.go(Route.VehicleDetail(v.id)) }) {
                    Column(Modifier.padding(14.dp)) { Text(v.name, fontWeight = FontWeight.Bold); Text(v.plate, fontSize = 12.sp, color = cs.onSurfaceVariant) }
                }
            }
            if (es.isNotEmpty()) item { SectionLabel("Pengeluaran") }
            items(es, key = { "e" + it.id }) { e -> ExpenseRow(e, Repo.vehicleName(e.vehicleId)) { nav.go(Route.ExpenseForm(e.vehicleId, e.id, e.category)) } }
            if (ds.isNotEmpty()) item { SectionLabel("Dokumen") }
            items(ds, key = { "d" + it.id }) { d ->
                DocCard(d, d.vehicleId?.let { Repo.vehicle(it)?.plate }, { nav.go(Route.DocForm(d.id)) }, { d.file?.let { f -> nav.go(Route.Preview(f, d.name)) } })
            }
        }
    }
}

@Composable
private fun SettingRow(icon: ImageVector, title: String, sub: String? = null, onClick: (() -> Unit)? = null, trailing: @Composable (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable { onClick() } else Modifier).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (sub != null) Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) trailing()
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
    AppCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), content = content) }
}

@Composable
fun SettingsScreen(nav: Nav) {
    val ctx = LocalContext.current
    val act = ctx.activity()
    val scope = rememberCoroutineScope()
    var pinDlg by remember { mutableStateOf(false) }
    var aboutDlg by remember { mutableStateOf(false) }
    var nameDlg by remember { mutableStateOf(false) }
    var restoreDlg by remember { mutableStateOf<android.net.Uri?>(null) }
    var oil by remember { mutableStateOf(Prefs.oilWarn.toString()) }
    var svc by remember { mutableStateOf(Prefs.svcWarn.toString()) }
    var dd by remember { mutableStateOf(Prefs.docWarn.toString()) }

    val permL = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) { Notif.test(ctx) } else toast(ctx, "Izin notifikasi ditolak")
    }
    val backupL = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Repo.backup(uri) }
            toast(ctx, if (ok) "Backup tersimpan" else "Backup gagal")
        }
    }
    val restoreL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) restoreDlg = uri }
    val soundL = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) toast(ctx, if (Notif.setCustomSound(ctx, uri)) "Suara notifikasi diganti" else "Gagal memakai file suara")
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopBar("Pengaturan", { nav.back() }) }) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Section("Profil & Tampilan") {
                SettingRow(Icons.Filled.Edit, "Nama", Prefs.name, { nameDlg = true })
                Text("Tema", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                Choice(listOf("0" to "Otomatis (waktu)", "1" to "Terang", "2" to "Senja", "3" to "Gelap"), Prefs.themeMode.toString()) {
                    Prefs.themeMode = it.toInt(); Prefs.persist(); GarasiWidget.refresh(ctx)
                }
                Text("Otomatis: pagi–siang terang, sore senja, malam gelap.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            }
            Section("Notifikasi & Pengingat") {
                SettingRow(Icons.Filled.Notifications, "Aktifkan pengingat", "Servis, ganti oli, SIM, STNK, pajak", trailing = {
                    Switch(checked = Prefs.notifOn, onCheckedChange = {
                        Prefs.notifOn = it; Prefs.persist()
                        if (it && Build.VERSION.SDK_INT >= 33 && !Notif.hasPermission(ctx)) permL.launch(Manifest.permission.POST_NOTIFICATIONS)
                    })
                })
                SettingRow(Icons.Filled.NotificationsActive, "Tes notifikasi", "Kirim notifikasi contoh sekarang", {
                    if (Notif.hasPermission(ctx)) Notif.test(ctx) else permL.launch(Manifest.permission.POST_NOTIFICATIONS)
                })
                SettingRow(Icons.Filled.MusicNote, "Suara notifikasi", if (Prefs.soundFile == null) "Bawaan GarasiKu" else "Kustom (${Prefs.soundFile})", {
                    soundL.launch(arrayOf("audio/mpeg", "audio/wav", "audio/x-wav", "audio/*"))
                })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    OutlinedButton(onClick = {
                        try { android.media.RingtoneManager.getRingtone(ctx, Notif.soundUri(ctx)).play() } catch (e: Exception) { toast(ctx, "Tidak bisa memutar") }
                    }) { Text("Putar suara") }
                    if (Prefs.soundFile != null) OutlinedButton(onClick = { Notif.resetSound(ctx) }) { Text("Pakai bawaan") }
                }
                Text("Ingatkan saat sisa jarak ≤ (km) / sisa hari ≤", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                    Field(oil, { oil = it.filter(Char::isDigit).take(5); oil.toIntOrNull()?.let { v -> Prefs.oilWarn = v; Prefs.persist() } }, "Oli (km)", Modifier.weight(1f), number = true)
                    Field(svc, { svc = it.filter(Char::isDigit).take(5); svc.toIntOrNull()?.let { v -> Prefs.svcWarn = v; Prefs.persist() } }, "Servis (km)", Modifier.weight(1f), number = true)
                    Field(dd, { dd = it.filter(Char::isDigit).take(3); dd.toIntOrNull()?.let { v -> Prefs.docWarn = v; Prefs.persist() } }, "Dokumen (hari)", Modifier.weight(1f), number = true)
                }
            }
            Section("Keamanan") {
                SettingRow(Icons.Filled.Lock, "PIN", if (Prefs.pinEnabled) "Aktif" else "Belum diatur", { pinDlg = true }, trailing = {
                    if (Prefs.pinEnabled) TextButton(onClick = { Prefs.clearPin() }) { Text("Matikan") }
                })
                SettingRow(Icons.Filled.Fingerprint, "Biometrik", if (act?.canBio() == true) "Buka kunci dengan sidik jari/wajah" else "Tidak tersedia di perangkat ini", trailing = {
                    Switch(checked = Prefs.bio, enabled = Prefs.pinEnabled && act?.canBio() == true, onCheckedChange = { Prefs.bio = it; Prefs.persist() })
                })
                SettingRow(Icons.Filled.Visibility, "Izinkan screenshot", "Matikan untuk memblokir screenshot & rekam layar", trailing = {
                    Switch(checked = Prefs.allowShot, onCheckedChange = { Prefs.allowShot = it; Prefs.persist() })
                })
            }
            Section("Data") {
                SettingRow(Icons.Filled.Backup, "Backup", "Simpan semua data & file ke .zip", {
                    backupL.launch("GarasiKu_backup_${LocalDate.now()}.zip")
                })
                SettingRow(Icons.Filled.Restore, "Restore", "Pulihkan dari file backup (data saat ini diganti)", { restoreL.launch(arrayOf("*/*")) })
            }
            Section("Widget") {
                SettingRow(Icons.Filled.Info, "Cara menambah widget", "Tekan lama layar utama → Widget → GarasiKu. Pilih kendaraan yang ditampilkan; geser untuk berpindah.")
            }
            Section("Lainnya") {
                SettingRow(Icons.Filled.Info, "Tentang aplikasi", "GarasiKu", { aboutDlg = true })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (nameDlg) {
        var t by remember { mutableStateOf(Prefs.name) }
        AlertDialog(onDismissRequest = { nameDlg = false }, title = { Text("Ubah nama") }, text = { Field(t, { t = it.take(30) }, "Nama") },
            confirmButton = { TextButton(onClick = { if (t.isNotBlank()) { Prefs.name = t.trim(); Prefs.persist() }; nameDlg = false }) { Text("Simpan") } },
            dismissButton = { TextButton(onClick = { nameDlg = false }) { Text("Batal") } })
    }
    if (pinDlg) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        AlertDialog(onDismissRequest = { pinDlg = false }, title = { Text(if (Prefs.pinEnabled) "Ganti PIN" else "Buat PIN") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p1, { p1 = it.filter(Char::isDigit).take(6) }, label = { Text("PIN 6 digit") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                    OutlinedTextField(p2, { p2 = it.filter(Char::isDigit).take(6) }, label = { Text("Ulangi PIN") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (p1.length == 6 && p1 == p2) { Prefs.setPin(p1); pinDlg = false; toast(ctx, "PIN disimpan") }
                    else toast(ctx, "PIN harus 6 digit dan sama")
                }) { Text("Simpan") }
            },
            dismissButton = { TextButton(onClick = { pinDlg = false }) { Text("Batal") } })
    }
    restoreDlg?.let { uri ->
        AlertDialog(onDismissRequest = { restoreDlg = null }, title = { Text("Restore data?") },
            text = { Text("Semua data saat ini akan diganti dengan isi file backup.") },
            confirmButton = {
                TextButton(onClick = {
                    restoreDlg = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) { Repo.restore(uri) }
                        toast(ctx, if (ok) "Restore berhasil" else "File backup tidak valid")
                    }
                }) { Text("Restore") }
            },
            dismissButton = { TextButton(onClick = { restoreDlg = null }) { Text("Batal") } })
    }
    if (aboutDlg) {
        val ver = try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName } catch (e: Exception) { "1.0.0" }
        AlertDialog(onDismissRequest = { aboutDlg = false }, title = { Text("GarasiKu") },
            text = {
                Text("Rawat Kendaraan & Status SIM\n\nVersi $ver\nPaket ${ctx.packageName}\n\nCatat servis, BBM, ganti oli, sparepart, serta pantau masa berlaku SIM, STNK dan pajak plat — lengkap dengan OCR, widget, pengingat, backup & kunci PIN. Semua data tersimpan di perangkat Anda.")
            },
            confirmButton = { TextButton(onClick = { aboutDlg = false }) { Text("Tutup") } })
    }
}
