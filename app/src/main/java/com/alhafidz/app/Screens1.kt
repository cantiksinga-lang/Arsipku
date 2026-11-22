@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.alhafidz.app

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

sealed interface Route {
    data class VehicleDetail(val id: String) : Route
    data class VehicleForm(val id: String?) : Route
    data class ExpenseForm(val vehicleId: String, val id: String? = null, val cat: String = "SERVIS") : Route
    data class DocForm(val id: String?, val type: String = "SIM", val vehicleId: String? = null) : Route
    data class Preview(val file: String, val title: String) : Route
    data object Search : Route
    data object Settings : Route
}

class Nav {
    val stack = mutableStateListOf<Route>()
    var tab by mutableIntStateOf(0)
    fun go(r: Route) { stack.add(r) }
    fun back() { if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex) }
}

@Composable
fun MainApp(nav: Nav) {
    val top = nav.stack.lastOrNull()
    BackHandler(enabled = top != null) { nav.back() }
    when (val r = top) {
        null -> Tabs(nav)
        is Route.VehicleDetail -> VehicleDetailScreen(nav, r.id)
        is Route.VehicleForm -> VehicleFormScreen(nav, r.id)
        is Route.ExpenseForm -> ExpenseFormScreen(nav, r.vehicleId, r.id, r.cat)
        is Route.DocForm -> DocFormScreen(nav, r.id, r.type, r.vehicleId)
        is Route.Preview -> PreviewScreen(nav, r.file, r.title)
        Route.Search -> SearchScreen(nav)
        Route.Settings -> SettingsScreen(nav)
    }
}

@Composable
fun Tabs(nav: Nav) {
    val vehicles by Repo.vehicles.collectAsState()
    val docs by Repo.docs.collectAsState()
    val alerts = alertCount(vehicles, docs)
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar {
                NavigationBarItem(selected = nav.tab == 0, onClick = { nav.tab = 0 }, label = { Text("Kendaraan") },
                    icon = { BadgedBox(badge = { if (alerts > 0) Badge { Text("$alerts") } }) { Icon(Icons.Filled.DirectionsCar, null) } })
                NavigationBarItem(selected = nav.tab == 1, onClick = { nav.tab = 1 }, label = { Text("Dokumen") },
                    icon = { Icon(Icons.Filled.Badge, null) })
                NavigationBarItem(selected = nav.tab == 2, onClick = { nav.tab = 2 }, label = { Text("Laporan") },
                    icon = { Icon(Icons.Filled.Assessment, null) })
            }
        },
        floatingActionButton = {
            if (nav.tab == 0) ExtendedFloatingActionButton(onClick = { nav.go(Route.VehicleForm(null)) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Kendaraan") })
            if (nav.tab == 1) ExtendedFloatingActionButton(onClick = { nav.go(Route.DocForm(null)) },
                icon = { Icon(Icons.Filled.Add, null) }, text = { Text("Dokumen") })
        }
    ) { pad ->
        when (nav.tab) {
            0 -> HomeTab(nav, pad)
            1 -> DocsTab(nav, pad)
            else -> ReportTab(nav, pad)
        }
    }
}

@Composable
fun HomeTab(nav: Nav, pad: PaddingValues) {
    val ctx = LocalContext.current
    val vehicles by Repo.vehicles.collectAsState()
    val expenses by Repo.expenses.collectAsState()
    val docs by Repo.docs.collectAsState()
    var all by remember { mutableStateOf(false) }
    var compact by remember { mutableStateOf(false) }
    var kmFor by remember { mutableStateOf<Vehicle?>(null) }
    val year = LocalDate.now().year
    val cs = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, pad.calculateTopPadding() + 8.dp, 16.dp, pad.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(cs.primary), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Build, null, tint = cs.onPrimary, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("GarasiKu", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Rawat Kendaraan & Status SIM", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                RoundIcon(Icons.Filled.Refresh) {
                    Repo.load(); GarasiWidget.refresh(ctx); Notif.check(ctx); toast(ctx, "Data disegarkan")
                }
                Spacer(Modifier.width(8.dp))
                RoundIcon(Icons.Filled.Settings) { nav.go(Route.Settings) }
            }
        }
        item {
            Text("${greeting()}, ${Prefs.name}!", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = cs.primary)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Kendaraan Saya", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("${vehicles.size} kendaraan", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                RoundIcon(Icons.Filled.Search) { nav.go(Route.Search) }
                Spacer(Modifier.width(8.dp))
                RoundIcon(if (compact) Icons.Filled.ViewAgenda else Icons.Filled.ViewList) { compact = !compact }
            }
        }
        item {
            val list = if (all) expenses else expenses.filter { it.date.toLocal().year == year }
            val servis = list.filter { it.category in setOf("SERVIS", "OLI", "SPAREPART") }
            val bbm = list.filter { it.category == "BBM" }
            val other = list.filter { it.category in setOf("PAJAK", "LAINNYA") }
            val total = list.sumOf { it.amount }
            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Ringkasan Biaya Garasi", fontSize = 13.sp, color = cs.onSurfaceVariant)
                            Text(rupiah(total), fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                            Text((if (all) "Semua waktu" else "Tahun $year") + " · ${list.size} transaksi", fontSize = 12.sp, color = cs.onSurfaceVariant)
                        }
                        Toggle(listOf("$year", "Semua"), if (all) 1 else 0) { all = it == 1 }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)).background(LocalPal.current.track)) {
                        val s = servis.sumOf { it.amount }; val b = bbm.sumOf { it.amount }; val o = other.sumOf { it.amount }
                        if (s > 0) Box(Modifier.weight(s.toFloat()).fillMaxHeight().background(cs.primary))
                        if (b > 0) Box(Modifier.weight(b.toFloat()).fillMaxHeight().background(cs.secondary))
                        if (o > 0) Box(Modifier.weight(o.toFloat()).fillMaxHeight().background(Cat.color("PAJAK")))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("● Servis · ${servis.size}×", fontSize = 12.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                            Text(rupiah(servis.sumOf { it.amount }), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("● BBM · ${bbm.size}×", fontSize = 12.sp, color = cs.secondary, fontWeight = FontWeight.SemiBold)
                            Text(rupiah(bbm.sumOf { it.amount }), fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        }
                    }
                    val taxEst = vehicles.sumOf { it.taxEst }
                    if (taxEst > 0) {
                        HorizontalDivider(Modifier.padding(vertical = 10.dp))
                        Row {
                            Text("Estimasi pajak tahunan (${vehicles.size} kendaraan)", fontSize = 12.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                            Text("${rupiah(taxEst)}/thn", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        if (vehicles.isEmpty()) {
            item {
                AppCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(24.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Filled.TwoWheeler, null, Modifier.size(56.dp), tint = cs.primary)
                        Spacer(Modifier.height(8.dp))
                        Text("Belum ada kendaraan", fontWeight = FontWeight.Bold)
                        Text("Tekan tombol Kendaraan untuk menambah motor atau mobil Anda.", textAlign = TextAlign.Center, color = cs.onSurfaceVariant, fontSize = 13.sp)
                    }
                }
            }
        }
        items(vehicles, key = { it.id }) { v ->
            VehicleCard(v, docs, compact, onOpen = { nav.go(Route.VehicleDetail(v.id)) },
                onFuel = { nav.go(Route.ExpenseForm(v.id, null, "BBM")) }, onKm = { kmFor = v })
        }
    }
    kmFor?.let { KmDialog(it) { kmFor = null } }
}

fun taxChip(v: Vehicle, docs: List<Doc>): Pair<String, Lv> {
    val d = latestDoc(docs, v.id, "STNK") ?: return "Pajak: belum diisi" to Lv.NONE
    val days = daysLeft(d.expiry)
    return daysText(days, "Pajak") to lvDays(days)
}

@Composable
fun VehicleCard(v: Vehicle, docs: List<Doc>, compact: Boolean, onOpen: () -> Unit, onFuel: () -> Unit, onKm: () -> Unit) {
    val pal = LocalPal.current
    val cs = MaterialTheme.colorScheme
    val img = rememberBitmap(v.photo, if (compact) 300 else 900)
    val (taxText, taxLv) = taxChip(v, docs)
    val oilLv = lvKm(v.oilLeft, Prefs.oilWarn)
    if (compact) {
        AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(72.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(pal.g1, pal.g2))), contentAlignment = Alignment.Center) {
                    if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Icon(if (v.type == "Mobil") Icons.Filled.DirectionsCar else Icons.Filled.TwoWheeler, null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(v.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${v.plate} · ${num(v.odo)} km", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    Spacer(Modifier.height(4.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Pill(taxText, taxLv.color(pal)); Pill(kmText(v.oilLeft, "Oli"), oilLv.color(pal))
                    }
                }
            }
        }
        return
    }
    AppCard(Modifier.fillMaxWidth(), onClick = onOpen) {
        Box(Modifier.fillMaxWidth().height(190.dp).background(Brush.linearGradient(listOf(pal.g1, pal.g2)))) {
            if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Icon(if (v.type == "Mobil") Icons.Filled.DirectionsCar else Icons.Filled.TwoWheeler, null,
                tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(84.dp).align(Alignment.Center))
            Surface(color = Color.Black.copy(alpha = .72f), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                Text(v.plate, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
            }
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(v.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Filled.ChevronRight, null, tint = cs.primary)
            }
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(taxText, taxLv.color(pal)); Pill(kmText(v.oilLeft, "Oli mesin"), oilLv.color(pal))
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Speed, null, tint = cs.onSurfaceVariant)
                        Spacer(Modifier.width(6.dp))
                        Text("${num(v.odo)} km", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    }
                    Text("Diperbarui ${agoText(v.odoAt)}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
                FilledTonalButton(onClick = onFuel, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Filled.LocalGasStation, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Isi BBM", fontSize = 13.sp)
                }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = onKm, contentPadding = PaddingValues(horizontal = 12.dp)) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Update KM", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun ExpenseRow(e: Expense, vehicleName: String?, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AppCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Cat.color(e.category).copy(alpha = .18f)), contentAlignment = Alignment.Center) {
                Icon(when (e.category) { "BBM" -> Icons.Filled.LocalGasStation; "PAJAK" -> Icons.Filled.Description; else -> Icons.Filled.Build },
                    null, tint = Cat.color(e.category))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(Cat.label(e.category) + " · " + fmtDate(e.date) + (vehicleName?.let { " · $it" } ?: ""), fontSize = 12.sp, color = cs.onSurfaceVariant, maxLines = 1)
            }
            Text(rupiah(e.amount), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun VehicleDetailScreen(nav: Nav, id: String) {
    val vehicles by Repo.vehicles.collectAsState()
    val expenses by Repo.expenses.collectAsState()
    val docs by Repo.docs.collectAsState()
    val v = vehicles.firstOrNull { it.id == id }
    if (v == null) { LaunchedEffect(Unit) { nav.back() }; return }
    val pal = LocalPal.current
    val cs = MaterialTheme.colorScheme
    var kmDlg by remember { mutableStateOf(false) }
    var delDlg by remember { mutableStateOf(false) }
    val img = rememberBitmap(v.photo, 1000)
    val mine = expenses.filter { it.vehicleId == id }.sortedByDescending { it.date }
    val now = LocalDate.now()
    val fuelMonth = mine.filter { it.category == "BBM" && it.date.toLocal().let { d -> d.year == now.year && d.month == now.month } }
    val fills = mine.filter { it.category == "BBM" && it.odo > 0 && it.liters > 0 }.sortedBy { it.odo }
    val kmPerL = if (fills.size >= 2) {
        val dist = fills.last().odo - fills.first().odo
        val lit = fills.drop(1).sumOf { it.liters }
        if (lit > 0 && dist > 0) dist / lit else null
    } else null
    val stnk = latestDoc(docs, id, "STNK")
    val p5 = latestDoc(docs, id, "PLAT5")

    Scaffold(
        containerColor = cs.background,
        topBar = {
            TopBar(v.name, { nav.back() }) {
                IconButton(onClick = { nav.go(Route.VehicleForm(id)) }) { Icon(Icons.Filled.Edit, "Edit") }
                IconButton(onClick = { delDlg = true }) { Icon(Icons.Filled.Delete, "Hapus") }
            }
        }
    ) { pad ->
        Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(pal.g1, pal.g2)))) {
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(if (v.type == "Mobil") Icons.Filled.DirectionsCar else Icons.Filled.TwoWheeler, null, tint = Color.White.copy(alpha = .85f), modifier = Modifier.size(90.dp).align(Alignment.Center))
                Surface(color = Color.Black.copy(alpha = .72f), shape = RoundedCornerShape(8.dp), modifier = Modifier.align(Alignment.BottomStart).padding(12.dp)) {
                    Text(v.plate, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp))
                }
            }
            AppCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Speed, null, tint = cs.primary, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${num(v.odo)} km", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Diperbarui ${agoText(v.odoAt)}", fontSize = 12.sp, color = cs.onSurfaceVariant)
                    }
                    Button(onClick = { kmDlg = true }) { Text("Update KM") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RingCard(Modifier.weight(1f), "Oli Mesin", num(v.oilLeft.coerceAtLeast(0)), "km lagi",
                    if (v.oilLeft < 0) "lewat ${num(-v.oilLeft)} km" else "interval ${num(v.oilInterval)} km",
                    if (v.oilInterval > 0) v.oilLeft.toFloat() / v.oilInterval else 0f, lvKm(v.oilLeft, Prefs.oilWarn))
                RingCard(Modifier.weight(1f), "Servis Berkala", num(v.serviceLeft.coerceAtLeast(0)), "km lagi",
                    if (v.serviceLeft < 0) "lewat ${num(-v.serviceLeft)} km" else "interval ${num(v.serviceInterval)} km",
                    if (v.serviceInterval > 0) v.serviceLeft.toFloat() / v.serviceInterval else 0f, lvKm(v.serviceLeft, Prefs.svcWarn))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val d1 = stnk?.let { daysLeft(it.expiry) }
                RingCard(Modifier.weight(1f), "Pajak Tahunan", d1?.coerceAtLeast(0)?.toString() ?: "-", "hari lagi",
                    stnk?.let { "s/d ${fmtDate(it.expiry)}" } ?: "belum diisi",
                    if (d1 == null) 0f else d1 / 365f, if (d1 == null) Lv.NONE else lvDays(d1))
                val d2 = p5?.let { daysLeft(it.expiry) }
                RingCard(Modifier.weight(1f), "Pajak Plat 5 Thn", d2?.coerceAtLeast(0)?.toString() ?: "-", "hari lagi",
                    p5?.let { "s/d ${fmtDate(it.expiry)}" } ?: "belum diisi",
                    if (d2 == null) 0f else d2 / 1825f, if (d2 == null) Lv.NONE else lvDays(d2))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { nav.go(Route.ExpenseForm(id, null, "BBM")) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Isi BBM", fontSize = 13.sp) }
                Button(onClick = { nav.go(Route.ExpenseForm(id, null, "SERVIS")) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Servis", fontSize = 13.sp) }
                Button(onClick = { nav.go(Route.ExpenseForm(id, null, "OLI")) }, Modifier.weight(1f), contentPadding = PaddingValues(4.dp)) { Text("Ganti Oli", fontSize = 13.sp) }
            }
            OutlinedButton(onClick = { nav.go(Route.ExpenseForm(id, null, "LAINNYA")) }, Modifier.fillMaxWidth()) { Text("Catat pengeluaran lain / sparepart") }

            AppCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("BBM", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Bulan ini: " + String.format(ID_LOCALE, "%.1f", fuelMonth.sumOf { it.liters }) + " L · " + rupiah(fuelMonth.sumOf { it.amount }))
                    Text(kmPerL?.let { "Rata-rata konsumsi: " + String.format(ID_LOCALE, "%.1f", it) + " km/L" } ?: "Konsumsi rata-rata muncul setelah 2x isi BBM dengan KM & liter.", fontSize = 12.sp, color = cs.onSurfaceVariant)
                }
            }
            val vdocs = docs.filter { it.vehicleId == id }
            SectionLabel("Dokumen kendaraan")
            if (vdocs.isEmpty()) Text("Belum ada dokumen.", color = cs.onSurfaceVariant, fontSize = 13.sp)
            vdocs.forEach { d -> DocCard(d, v.plate, { nav.go(Route.DocForm(d.id)) }, { d.file?.let { f -> nav.go(Route.Preview(f, d.name)) } }) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { nav.go(Route.DocForm(null, "STNK", id)) }) { Text("+ STNK/Pajak") }
                OutlinedButton(onClick = { nav.go(Route.DocForm(null, "PLAT5", id)) }) { Text("+ Plat 5 Thn") }
            }
            SectionLabel("Riwayat (${mine.size})")
            mine.take(20).forEach { e -> ExpenseRow(e, null) { nav.go(Route.ExpenseForm(id, e.id, e.category)) } }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (kmDlg) KmDialog(v) { kmDlg = false }
    if (delDlg) ConfirmDialog("Hapus kendaraan?", "Semua pengeluaran dan dokumen kendaraan ini ikut terhapus.",
        { delDlg = false; Repo.deleteVehicle(id); nav.back() }, { delDlg = false })
}

@Composable
fun VehicleFormScreen(nav: Nav, id: String?) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val old = remember(id) { Repo.vehicle(id) }
    var name by remember { mutableStateOf(old?.name ?: "") }
    var plate by remember { mutableStateOf(old?.plate ?: "") }
    var type by remember { mutableStateOf(old?.type ?: "Motor") }
    var odo by remember { mutableStateOf(old?.odo?.toString() ?: "") }
    var oilInt by remember { mutableStateOf((old?.oilInterval ?: 2000).toString()) }
    var lastOil by remember { mutableStateOf(old?.lastOilKm?.toString() ?: "") }
    var svcInt by remember { mutableStateOf((old?.serviceInterval ?: 3000).toString()) }
    var lastSvc by remember { mutableStateOf(old?.lastServiceKm?.toString() ?: "") }
    var tax by remember { mutableStateOf(old?.taxEst?.takeIf { it > 0 }?.toString() ?: "") }
    var photo by remember { mutableStateOf(old?.photo) }
    val img = rememberBitmap(photo, 900)
    val cap = rememberCapture { uri ->
        scope.launch {
            val n = withContext(Dispatchers.IO) { Repo.importUri(uri, "veh") }
            if (n != null) photo = n else toast(ctx, "Gagal membaca foto")
        }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { TopBar(if (old == null) "Tambah Kendaraan" else "Edit Kendaraan", { nav.back() }) }) { pad ->
        Column(Modifier.padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Text("Foto kendaraan (opsional)", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = cap.camera) { Icon(Icons.Filled.CameraAlt, null); Spacer(Modifier.width(6.dp)); Text("Kamera") }
                FilledTonalButton(onClick = cap.gallery) { Icon(Icons.Filled.PhotoLibrary, null); Spacer(Modifier.width(6.dp)); Text("Galeri") }
            }
            Choice(listOf("Motor" to "Motor", "Mobil" to "Mobil"), type) { type = it; if (old == null) { oilInt = if (it == "Mobil") "10000" else "2000"; svcInt = if (it == "Mobil") "10000" else "3000" } }
            Field(name, { name = it }, "Nama / model (mis. Yamaha NMAX Neo S)")
            Field(plate, { plate = it.uppercase() }, "Plat nomor", caps = true)
            Field(odo, { odo = it.filter(Char::isDigit).take(7) }, "KM saat ini", number = true)
            SectionLabel("Jadwal ganti oli & servis")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(oilInt, { oilInt = it.filter(Char::isDigit).take(6) }, "Interval oli (km)", Modifier.weight(1f), number = true)
                Field(lastOil, { lastOil = it.filter(Char::isDigit).take(7) }, "Terakhir ganti oli (KM)", Modifier.weight(1f), number = true)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(svcInt, { svcInt = it.filter(Char::isDigit).take(6) }, "Interval servis (km)", Modifier.weight(1f), number = true)
                Field(lastSvc, { lastSvc = it.filter(Char::isDigit).take(7) }, "Terakhir servis (KM)", Modifier.weight(1f), number = true)
            }
            Field(tax, { tax = it.filter(Char::isDigit).take(10) }, "Estimasi pajak tahunan (Rp, opsional)", number = true)
            Button(onClick = {
                if (name.isBlank() || plate.isBlank()) { toast(ctx, "Nama dan plat wajib diisi"); return@Button }
                val o = odo.toIntOrNull() ?: 0
                val v = Vehicle(
                    id = old?.id ?: newId(), name = name.trim(), plate = plate.trim(), type = type, odo = o,
                    odoAt = if (old != null && old.odo == o) old.odoAt else System.currentTimeMillis(), photo = photo,
                    oilInterval = oilInt.toIntOrNull() ?: 2000, lastOilKm = lastOil.toIntOrNull() ?: o,
                    serviceInterval = svcInt.toIntOrNull() ?: 3000, lastServiceKm = lastSvc.toIntOrNull() ?: o,
                    taxEst = tax.toLongOrNull() ?: 0
                )
                Repo.saveVehicle(v); nav.back()
            }, Modifier.fillMaxWidth().height(52.dp)) { Text("Simpan") }
        }
    }
}
