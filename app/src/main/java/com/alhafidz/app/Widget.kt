package com.alhafidz.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDate

object WidgetPrefs {
    private fun sp(c: Context) = c.getSharedPreferences("widgets", Context.MODE_PRIVATE)
    fun vehicles(c: Context, id: Int): Set<String>? = sp(c).getString("w_$id", null)?.split(",")?.filter { it.isNotBlank() }?.toSet()
    fun docs(c: Context, id: Int): Boolean = sp(c).getBoolean("d_$id", true)
    fun save(c: Context, id: Int, v: Set<String>, docs: Boolean) {
        sp(c).edit().putString("w_$id", v.joinToString(",")).putBoolean("d_$id", docs).apply()
    }
    fun remove(c: Context, id: Int) { sp(c).edit().remove("w_$id").remove("d_$id").apply() }
}

class WPal(val bg: Int, val main: Int, val sub: Int, val track: Int)

fun wpal(m: Mode): WPal = when (m) {
    Mode.LIGHT -> WPal(R.drawable.bg_w_light, 0xFF14202B.toInt(), 0xFF55606B.toInt(), 0xFFE3E9F0.toInt())
    Mode.SENJA -> WPal(R.drawable.bg_w_senja, 0xFFFFE8D6.toInt(), 0xFFE9C6B8.toInt(), 0x55FFFFFF)
    Mode.DARK -> WPal(R.drawable.bg_w_dark, 0xFFEAF2F8.toInt(), 0xFF9FB1C0.toInt(), 0xFF2A3C50.toInt())
}

fun lvInt(l: Lv): Int = when (l) {
    Lv.OK -> 0xFF2EB872.toInt(); Lv.WARN -> 0xFFF5A623.toInt(); Lv.BAD -> 0xFFE5484D.toInt(); Lv.NONE -> 0xFF9AA5B1.toInt()
}

object Rings {
    fun make(progress: Float, color: Int, track: Int, big: String, small: String, txt: Int, px: Int = 168): Bitmap {
        val b = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val sw = px * 0.12f
        val r = RectF(sw / 2, sw / 2, px - sw / 2, px - sw / 2)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.style = Paint.Style.STROKE; p.strokeWidth = sw; p.strokeCap = Paint.Cap.ROUND
        p.color = track
        c.drawArc(r, 0f, 360f, false, p)
        p.color = color
        c.drawArc(r, -90f, 360f * progress.coerceIn(0f, 1f), false, p)
        val tp = Paint(Paint.ANTI_ALIAS_FLAG)
        tp.color = txt; tp.textAlign = Paint.Align.CENTER; tp.typeface = Typeface.DEFAULT_BOLD
        tp.textSize = px * when { big.length <= 3 -> 0.28f; big.length == 4 -> 0.24f; big.length == 5 -> 0.20f; else -> 0.17f }
        c.drawText(big, px / 2f, px * 0.53f, tp)
        tp.typeface = Typeface.DEFAULT; tp.textSize = px * 0.14f
        c.drawText(small, px / 2f, px * 0.71f, tp)
        return b
    }
}

class GarasiWidget : AppWidgetProvider() {
    override fun onUpdate(ctx: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { update(ctx, mgr, it) }
    }

    override fun onDeleted(ctx: Context, ids: IntArray) {
        ids.forEach { WidgetPrefs.remove(ctx, it) }
    }

    companion object {
        fun refresh(ctx: Context) {
            try {
                val m = AppWidgetManager.getInstance(ctx)
                val ids = m.getAppWidgetIds(ComponentName(ctx, GarasiWidget::class.java))
                ids.forEach { update(ctx, m, it) }
            } catch (e: Exception) { }
        }

        fun update(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_garasi)
            val svc = Intent(ctx, WidgetService::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            rv.setRemoteAdapter(R.id.stack, svc)
            rv.setEmptyView(R.id.stack, R.id.empty)
            val tpl = Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pi = PendingIntent.getActivity(ctx, id, tpl, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            rv.setPendingIntentTemplate(R.id.stack, pi)
            mgr.updateAppWidget(id, rv)
            mgr.notifyAppWidgetViewDataChanged(id, R.id.stack)
        }
    }
}

class WidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsService.RemoteViewsFactory =
        Factory(applicationContext, intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, 0))

    class Factory(private val ctx: Context, private val wid: Int) : RemoteViewsService.RemoteViewsFactory {
        private var vehicles = listOf<Vehicle>()
        private var docs = listOf<Doc>()

        override fun onCreate() {}
        override fun onDestroy() {}
        override fun onDataSetChanged() {
            Repo.load()
            val sel = WidgetPrefs.vehicles(ctx, wid)
            vehicles = Repo.vehicles.value.filter { sel == null || it.id in sel }
            val ids = vehicles.map { it.id }.toSet()
            docs = if (WidgetPrefs.docs(ctx, wid))
                Repo.docs.value.filter { it.vehicleId == null || it.vehicleId in ids }
                    .sortedBy { if (it.expiry <= 0) Long.MAX_VALUE else it.expiry }.take(8)
            else emptyList()
        }

        override fun getCount(): Int = vehicles.size + docs.size
        override fun getViewAt(position: Int): RemoteViews {
            val t = wpal(currentMode())
            val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
            rv.setInt(R.id.item_root, "setBackgroundResource", t.bg)
            fun txt(id: Int, s: String, color: Int) { rv.setTextViewText(id, s); rv.setTextColor(id, color) }
            if (position < vehicles.size) {
                val v = vehicles[position]
                val now = LocalDate.now()
                val month = Repo.expenses.value.filter { it.vehicleId == v.id && it.category == "BBM" && it.date.toLocal().let { d -> d.year == now.year && d.month == now.month } }
                val lit = month.sumOf { it.liters }
                val rp = month.sumOf { it.amount }
                val stnk = latestDoc(Repo.docs.value, v.id, "STNK")
                val p5 = latestDoc(Repo.docs.value, v.id, "PLAT5")
                val oilLv = lvKm(v.oilLeft, Prefs.oilWarn)
                val oilProg = if (v.oilInterval > 0) v.oilLeft.toFloat() / v.oilInterval else 0f
                rv.setImageViewBitmap(R.id.w_ring1, Rings.make(oilProg, lvInt(oilLv), t.track, num(v.oilLeft.coerceAtLeast(0)), "km oli", t.main))
                rv.setViewVisibility(R.id.w_ring2, View.VISIBLE)
                val d = stnk?.let { daysLeft(it.expiry) }
                val taxLv = if (d == null) Lv.NONE else lvDays(d)
                rv.setImageViewBitmap(R.id.w_ring2, Rings.make(if (d == null) 0f else d / 365f, lvInt(taxLv), t.track, d?.coerceAtLeast(0)?.toString() ?: "-", "hr pajak", t.main))
                txt(R.id.w_title, v.name, t.main)
                txt(R.id.w_sub, "${v.plate} • ${num(v.odo)} km", t.sub)
                txt(R.id.w_l1, kmText(v.oilLeft, "Oli") + " • " + kmText(v.serviceLeft, "Servis").replace(" lagi", ""), lvInt(if (oilLv == Lv.OK) lvKm(v.serviceLeft, Prefs.svcWarn) else oilLv))
                txt(R.id.w_l2, "BBM bulan ini: " + String.format(ID_LOCALE, "%.1f", lit) + " L • " + rupiah(rp), t.sub)
                val p5d = p5?.let { daysLeft(it.expiry) }
                txt(R.id.w_l3, (d?.let { daysText(it, "Pajak") } ?: "Pajak: belum diisi") + (p5d?.let { " • Plat: $it hr" } ?: ""), lvInt(taxLv))
                rv.setOnClickFillInIntent(R.id.item_root, Intent().putExtra("vehicle", v.id))
            } else {
                val d = docs[position - vehicles.size]
                val days = if (d.expiry > 0) daysLeft(d.expiry) else null
                val lv = if (days == null) Lv.NONE else lvDays(days)
                rv.setImageViewBitmap(R.id.w_ring1, Rings.make(if (days == null) 0f else days.toFloat() / DocT.cycle(d.type), lvInt(lv), t.track, days?.coerceAtLeast(0)?.toString() ?: "-", "hari", t.main))
                rv.setViewVisibility(R.id.w_ring2, View.GONE)
                txt(R.id.w_title, d.name, t.main)
                txt(R.id.w_sub, DocT.label(d.type) + (d.vehicleId?.let { " • " + Repo.vehicleName(it) } ?: ""), t.sub)
                txt(R.id.w_l1, if (d.number.isNotBlank()) "No. ${d.number}" else "", t.sub)
                txt(R.id.w_l2, if (d.expiry > 0) "Berlaku s/d ${fmtDate(d.expiry)}" else "Masa berlaku belum diisi", t.sub)
                txt(R.id.w_l3, days?.let { if (it < 0) "Sudah habis ${-it} hari" else "$it hari lagi" } ?: "", lvInt(lv))
                rv.setOnClickFillInIntent(R.id.item_root, Intent().putExtra("doc", true))
            }
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount(): Int = 1
        override fun getItemId(position: Int): Long = position.toLong()
        override fun hasStableIds(): Boolean = false
    }
}

class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        val wid = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (wid == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        setContent {
            GarasiTheme(currentMode()) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val vs by Repo.vehicles.collectAsState()
                    val saved = remember { WidgetPrefs.vehicles(this@WidgetConfigActivity, wid) }
                    var sel by remember { mutableStateOf(saved ?: Repo.vehicles.value.map { it.id }.toSet()) }
                    var docs by remember { mutableStateOf(WidgetPrefs.docs(this@WidgetConfigActivity, wid)) }
                    Column(Modifier.statusBarsPadding().navigationBarsPadding().padding(20.dp)) {
                        Text("Widget GarasiKu", style = MaterialTheme.typography.headlineSmall)
                        Text("Pilih kendaraan yang ditampilkan. Jika lebih dari satu, widget bisa digeser.", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(12.dp))
                        LazyColumn(Modifier.weight(1f)) {
                            items(vs, key = { it.id }) { v ->
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(checked = v.id in sel, onCheckedChange = { c -> sel = if (c) sel + v.id else sel - v.id })
                                    Column { Text(v.name); Text(v.plate, style = MaterialTheme.typography.bodySmall) }
                                }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = docs, onCheckedChange = { docs = it })
                            Spacer(Modifier.width(10.dp))
                            Text("Tampilkan SIM, STNK & pajak")
                        }
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = {
                            WidgetPrefs.save(this@WidgetConfigActivity, wid, sel, docs)
                            GarasiWidget.refresh(this@WidgetConfigActivity)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, wid))
                            finish()
                        }, modifier = Modifier.fillMaxWidth()) { Text("Simpan") }
                    }
                }
            }
        }
    }
}
