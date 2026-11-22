@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.alhafidz.app

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

@Composable
fun ExpenseFormScreen(nav: Nav, vehicleId: String, id: String?, cat0: String) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vehicles by Repo.vehicles.collectAsState()
    val old = remember(id) { id?.let { i -> Repo.expenses.value.firstOrNull { it.id == i } } }
    var vid by remember { mutableStateOf(old?.vehicleId ?: vehicleId) }
    var cat by remember { mutableStateOf(old?.category ?: cat0) }
    var title by remember { mutableStateOf(old?.title ?: "") }
    var amount by remember { mutableStateOf(old?.amount?.takeIf { it > 0 }?.toString() ?: "") }
    var date by remember { mutableLongStateOf(old?.date ?: LocalDate.now().ms()) }
    var odo by remember { mutableStateOf(old?.odo?.takeIf { it > 0 }?.toString() ?: "") }
    var liters by remember { mutableStateOf(old?.liters?.takeIf { it > 0 }?.toString() ?: "") }
    var note by remember { mutableStateOf(old?.note ?: "") }
    var receipt by remember { mutableStateOf(old?.receipt) }
    var reset by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    val thumb = rememberBitmap(receipt, 400)

    val cap = rememberCapture { uri ->
        scope.launch {
            busy = true
            val n = withContext(Dispatchers.IO) { Repo.importUri(uri, "rcp") }
            if (n == null) { toast(ctx, "Gagal membaca file"); busy = false; return@launch }
            receipt = n
            val r = Ocr.parseReceipt(Ocr.read(ctx, n))
            r.amount?.let { amount = it.toString() }
            r.title?.let { title = it }
            r.category?.let { cat = it }
            r.date?.let { date = it.ms() }
            r.odo?.let { odo = it.toString() }
            r.liters?.let { liters = it.toString() }
            val found = listOfNotNull(r.amount, r.title, r.date).size
            toast(ctx, if (found == 0) "Teks tidak terbaca, isi manual ya" else "Hasil OCR terisi — periksa kembali")
            busy = false
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopBar(if (old == null) "Catat Pengeluaran" else "Edit Pengeluaran", { nav.back() }) {
            if (old != null) IconButton(onClick = { del = true }) { Icon(Icons.Filled.Delete, "Hapus") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (vehicles.size > 1) Choice(vehicles.map { it.id to it.name }, vid) { vid = it }
            ScanBar(cap, busy, true, "Scan nota / struk (OCR)")
            if (receipt != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { nav.go(Route.Preview(receipt!!, "Nota")) }, contentAlignment = Alignment.Center) {
                        if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.Description, null)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("Nota terlampir — ketuk untuk pratinjau", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { receipt = null }) { Icon(Icons.Filled.Close, "Lepas") }
                }
            }
            Choice(Cat.all.map { it to Cat.label(it) }, cat) { cat = it }
            Field(title, { title = it }, when (cat) { "BBM" -> "Jenis BBM (mis. Pertalite)"; "SPAREPART" -> "Nama sparepart"; "OLI" -> "Merek / jenis oli"; else -> "Keterangan" })
            Field(amount, { amount = it.filter(Char::isDigit).take(11) }, "Nominal (Rp)", number = true)
            DateField("Tanggal", date, { date = it })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(odo, { odo = it.filter(Char::isDigit).take(7) }, "KM saat ini", Modifier.weight(1f), number = true)
                if (cat == "BBM") Field(liters, { liters = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(7) }, "Liter", Modifier.weight(1f), decimal = true)
            }
            Field(note, { note = it }, "Catatan", single = false)
            if (old == null && (cat == "OLI" || cat == "SERVIS")) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = reset, onCheckedChange = { reset = it })
                    Text(if (cat == "OLI") "Reset hitungan interval oli ke KM ini" else "Reset hitungan interval servis ke KM ini", fontSize = 13.sp)
                }
            }
            Button(onClick = {
                val a = amount.toLongOrNull() ?: 0L
                val e = Expense(
                    id = old?.id ?: newId(), vehicleId = vid, date = date, category = cat,
                    title = title.trim().ifBlank { Cat.label(cat) }, amount = a, odo = odo.toIntOrNull() ?: 0,
                    liters = liters.replace(',', '.').toDoubleOrNull() ?: 0.0, note = note.trim(), receipt = receipt
                )
                Repo.saveExpense(e, old == null && reset); nav.back()
            }, Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan") }
        }
    }
    if (del) ConfirmDialog("Hapus pengeluaran?", "Data ini akan dihapus permanen.", { del = false; Repo.deleteExpense(old!!.id); nav.back() }, { del = false })
}

@Composable
fun DocCard(d: Doc, plate: String?, onEdit: () -> Unit, onPreview: () -> Unit) {
    val pal = LocalPal.current
    val cs = MaterialTheme.colorScheme
    val days = if (d.expiry > 0) daysLeft(d.expiry) else null
    val lv = if (days == null) Lv.NONE else lvDays(days)
    AppCard(Modifier.fillMaxWidth(), onClick = onEdit) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Donut(if (days == null) 0f else days.toFloat() / DocT.cycle(d.type), lv.color(pal), Modifier.size(76.dp), 8.dp) {
                Text(days?.let { if (it < 0) "!" else "$it" } ?: "-", fontWeight = FontWeight.ExtraBold, fontSize = if ((days ?: 0) > 999) 14.sp else 18.sp)
                Text("hari", fontSize = 10.sp, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(DocT.label(d.type), fontSize = 11.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                Text(d.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, maxLines = 1)
                if (d.number.isNotBlank()) Text("No. ${d.number}" + (plate?.let { " · $it" } ?: ""), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
                Text(if (d.expiry > 0) "Berlaku s/d ${fmtDate(d.expiry)}" else "Masa berlaku belum diisi", fontSize = 12.sp, color = lv.color(pal), fontWeight = FontWeight.SemiBold)
            }
            if (d.file != null) IconButton(onClick = onPreview) { Icon(Icons.Filled.Visibility, "Pratinjau", tint = cs.primary) }
        }
    }
}

@Composable
fun DocsTab(nav: Nav, pad: PaddingValues) {
    val docs by Repo.docs.collectAsState()
    val vehicles by Repo.vehicles.collectAsState()
    val sorted = docs.sortedBy { if (it.expiry <= 0) Long.MAX_VALUE else it.expiry }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Dokumen Saya", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                    Text("SIM, STNK (pajak tahunan) & pajak plat 5 tahunan", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                RoundIcon(Icons.Filled.Search) { nav.go(Route.Search) }
                Spacer(Modifier.width(8.dp))
                RoundIcon(Icons.Filled.Settings) { nav.go(Route.Settings) }
            }
        }
        if (sorted.isEmpty()) item {
            AppCard(Modifier.fillMaxWidth()) {
                Text("Belum ada dokumen. Tekan tombol Dokumen untuk menambah SIM, STNK atau pajak plat — bisa scan otomatis dengan OCR.",
                    Modifier.padding(20.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(sorted, key = { it.id }) { d ->
            DocCard(d, vehicles.firstOrNull { it.id == d.vehicleId }?.plate, { nav.go(Route.DocForm(d.id)) }, { d.file?.let { nav.go(Route.Preview(it, d.name)) } })
        }
    }
}

@Composable
fun DocFormScreen(nav: Nav, id: String?, type0: String, vehicleId0: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val vehicles by Repo.vehicles.collectAsState()
    val old = remember(id) { id?.let { i -> Repo.docs.value.firstOrNull { it.id == i } } }
    var type by remember { mutableStateOf(old?.type ?: type0) }
    var vid by remember { mutableStateOf(old?.vehicleId ?: vehicleId0 ?: vehicles.firstOrNull()?.id) }
    var name by remember { mutableStateOf(old?.name ?: "") }
    var number by remember { mutableStateOf(old?.number ?: "") }
    var expiry by remember { mutableLongStateOf(old?.expiry ?: 0L) }
    var note by remember { mutableStateOf(old?.note ?: "") }
    var file by remember { mutableStateOf(old?.file) }
    var busy by remember { mutableStateOf(false) }
    var del by remember { mutableStateOf(false) }
    val thumb = rememberBitmap(file, 400)

    val cap = rememberCapture { uri ->
        scope.launch {
            busy = true
            val n = withContext(Dispatchers.IO) { Repo.importUri(uri, "doc") }
            if (n == null) { toast(ctx, "Gagal membaca file"); busy = false; return@launch }
            file = n
            val r = Ocr.parseDoc(Ocr.read(ctx, n), type)
            r.name?.let { name = it }
            r.number?.let { number = it }
            r.expiry?.let { expiry = it.ms() }
            r.owner?.let { if (note.isBlank()) note = "Pemilik: $it" }
            toast(ctx, if (r.number == null && r.expiry == null) "Teks tidak terbaca, isi manual ya" else "Hasil OCR terisi — periksa kembali")
            busy = false
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        TopBar(if (old == null) "Tambah Dokumen" else "Edit Dokumen", { nav.back() }) {
            if (old != null) IconButton(onClick = { del = true }) { Icon(Icons.Filled.Delete, "Hapus") }
        }
    }) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Choice(DocT.all.map { it to DocT.label(it) }, type) { type = it }
            if (type != "SIM") {
                if (vehicles.isEmpty()) Text("Tambahkan kendaraan terlebih dahulu.", color = MaterialTheme.colorScheme.error)
                else Choice(vehicles.map { it.id to "${it.name} · ${it.plate}" }, vid) { vid = it }
            }
            ScanBar(cap, busy, true, "Scan / unggah dokumen (OCR)")
            if (file != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(72.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
                        .clickable { nav.go(Route.Preview(file!!, name.ifBlank { "Dokumen" })) }, contentAlignment = Alignment.Center) {
                        if (thumb != null) Image(thumb, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                        else Icon(Icons.Filled.PictureAsPdf, null)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("File terlampir — ketuk untuk pratinjau", fontSize = 12.sp, modifier = Modifier.weight(1f))
                    IconButton(onClick = { file = null }) { Icon(Icons.Filled.Close, "Lepas") }
                }
            }
            Field(name, { name = it }, "Nama dokumen (mis. SIM C, STNK NMAX)")
            Field(number, { number = it.uppercase() }, if (type == "SIM") "Nomor SIM" else "Nomor / plat", caps = true)
            DateField(if (type == "STNK") "Jatuh tempo pajak tahunan" else "Masa berlaku s/d", expiry, { expiry = it })
            Field(note, { note = it }, "Catatan / nama pemilik", single = false)
            Button(onClick = {
                if (type != "SIM" && vid == null) { toast(ctx, "Pilih kendaraan dulu"); return@Button }
                val nm = name.trim().ifBlank { DocT.label(type) }
                Repo.saveDoc(Doc(id = old?.id ?: newId(), vehicleId = if (type == "SIM") null else vid, type = type, name = nm,
                    number = number.trim(), expiry = expiry, file = file, note = note.trim()))
                nav.back()
            }, Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan") }
        }
    }
    if (del) ConfirmDialog("Hapus dokumen?", "Dokumen dan file terlampir akan dihapus.", { del = false; Repo.deleteDoc(old!!.id); nav.back() }, { del = false })
}

fun openExternal(ctx: android.content.Context, f: File) {
    try {
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
        val mime = if (f.name.endsWith(".pdf")) "application/pdf" else "image/*"
        val i = Intent(Intent.ACTION_VIEW).setDataAndType(u, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        ctx.startActivity(Intent.createChooser(i, "Buka dengan"))
    } catch (e: Exception) { toast(ctx, "Tidak ada aplikasi untuk membuka file") }
}

@Composable
fun PreviewScreen(nav: Nav, file: String, title: String) {
    val ctx = LocalContext.current
    val f = remember(file) { File(Repo.filesDir, file) }
    Scaffold(containerColor = Color.Black, topBar = {
        TopBar(title, { nav.back() }) { IconButton(onClick = { openExternal(ctx, f) }) { Icon(Icons.Filled.Share, "Buka") } }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize().background(Color.Black)) {
            if (file.endsWith(".pdf")) PdfView(f) else ZoomImage(file)
        }
    }
}

@Composable
fun ZoomImage(file: String) {
    val bmp = rememberBitmap(file, 2200)
    var scale by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    if (bmp == null) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return }
    Image(bmp, null, Modifier.fillMaxSize()
        .pointerInput(Unit) {
            detectTransformGestures { _, pan, zoom, _ ->
                scale = (scale * zoom).coerceIn(1f, 6f)
                off = if (scale == 1f) Offset.Zero else off + pan
            }
        }
        .graphicsLayer { scaleX = scale; scaleY = scale; translationX = off.x; translationY = off.y },
        contentScale = ContentScale.Fit)
}

@Composable
fun PdfView(f: File) {
    val holder = remember(f) { PdfHolder(f) }
    DisposableEffect(f) { onDispose { holder.close() } }
    if (holder.count == 0) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("PDF tidak dapat ditampilkan", color = Color.White) }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(8.dp)) {
        items(holder.count) { i ->
            val bmp = produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, i) {
                value = withContext(Dispatchers.IO) { holder.render(i, 1080)?.asImageBitmap() }
            }.value
            if (bmp != null) Image(bmp, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            else Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit, onBio: (() -> Unit)?) {
    var pin by remember { mutableStateOf("") }
    var err by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { onBio?.invoke() }
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Filled.Lock, null, tint = cs.primary, modifier = Modifier.size(48.dp))
        Spacer(Modifier.height(12.dp))
        Text("GarasiKu terkunci", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(if (err) "PIN salah, coba lagi" else "Masukkan PIN 6 digit", color = if (err) cs.error else cs.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            repeat(6) { i -> Box(Modifier.size(14.dp).clip(CircleShape).background(if (i < pin.length) cs.primary else cs.outline.copy(alpha = .4f))) }
        }
        Spacer(Modifier.height(28.dp))
        fun press(k: String) {
            err = false
            if (k == "del") { pin = pin.dropLast(1); return }
            if (pin.length >= 6) return
            pin += k
            if (pin.length == 6) { if (Prefs.checkPin(pin)) onUnlock() else { err = true; pin = "" } }
        }
        val rows = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("bio", "0", "del"))
        rows.forEach { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                r.forEach { k ->
                    Box(Modifier.size(72.dp).clip(CircleShape).background(if (k == "bio" && onBio == null) Color.Transparent else cs.surfaceVariant)
                        .clickable(enabled = !(k == "bio" && onBio == null)) { if (k == "bio") onBio?.invoke() else press(k) },
                        contentAlignment = Alignment.Center) {
                        when (k) {
                            "del" -> Icon(Icons.AutoMirrored.Filled.Backspace, "Hapus")
                            "bio" -> if (onBio != null) Icon(Icons.Filled.Fingerprint, "Biometrik")
                            else -> Text(k, fontSize = 26.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }
    }
}
