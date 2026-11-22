package com.alhafidz.app

import android.app.AlertDialog
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class Vehicle(val id: Long, val owner: String, val name: String, val plate: String, val year: String, val km: Long, val tax: String)
private data class VehicleEntry(val id: Long, val vehicle: Long, val owner: String, val type: String, val date: Long, val amount: Long, val km: Long, val liters: Double, val place: String, val note: String)

private class VehicleDb(c: Context) : SQLiteOpenHelper(c, "arsipku_garage.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE vehicles(id INTEGER PRIMARY KEY AUTOINCREMENT, owner TEXT NOT NULL, name TEXT NOT NULL, plate TEXT, year TEXT, km INTEGER NOT NULL DEFAULT 0, tax TEXT)")
        db.execSQL("CREATE TABLE entries(id INTEGER PRIMARY KEY AUTOINCREMENT, vehicle INTEGER NOT NULL, owner TEXT NOT NULL, type TEXT NOT NULL, date INTEGER NOT NULL, amount INTEGER NOT NULL DEFAULT 0, km INTEGER NOT NULL DEFAULT 0, liters REAL NOT NULL DEFAULT 0, place TEXT, note TEXT)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {}
}

private object VehicleStore {
    private var helper: VehicleDb? = null
    private fun db(c: Context) = (helper ?: VehicleDb(c.applicationContext).also { helper = it }).writableDatabase
    fun vehicles(c: Context, owner: String) = buildList {
        db(c).query("vehicles", null, "owner=?", arrayOf(owner), null, null, "name COLLATE NOCASE").use { q ->
            while (q.moveToNext()) add(Vehicle(q.getLong(0), q.getString(1), Crypto.decStr(c, q.getString(2)), Crypto.decStr(c, q.getString(3)), Crypto.decStr(c, q.getString(4)), q.getLong(5), Crypto.decStr(c, q.getString(6))))
        }
    }
    fun saveVehicle(c: Context, v: Vehicle): Long {
        val cv = ContentValues().apply { put("owner", v.owner); put("name", Crypto.encStr(c, v.name)); put("plate", Crypto.encStr(c, v.plate)); put("year", Crypto.encStr(c, v.year)); put("km", v.km); put("tax", Crypto.encStr(c, v.tax)) }
        return if (v.id == 0L) db(c).insert("vehicles", null, cv) else { db(c).update("vehicles", cv, "id=?", arrayOf(v.id.toString())); v.id }
    }
    fun entries(c: Context, owner: String, from: Long = 0, to: Long = Long.MAX_VALUE) = buildList {
        db(c).query("entries", null, "owner=? AND date>=? AND date<=?", arrayOf(owner, from.toString(), to.toString()), null, null, "date DESC").use { q ->
            while (q.moveToNext()) add(VehicleEntry(q.getLong(0), q.getLong(1), q.getString(2), Crypto.decStr(c, q.getString(3)), q.getLong(4), q.getLong(5), q.getLong(6), q.getDouble(7), Crypto.decStr(c, q.getString(8)), Crypto.decStr(c, q.getString(9))))
        }
    }
    fun saveEntry(c: Context, e: VehicleEntry): Long {
        val cv = ContentValues().apply { put("vehicle", e.vehicle); put("owner", e.owner); put("type", Crypto.encStr(c, e.type)); put("date", e.date); put("amount", e.amount); put("km", e.km); put("liters", e.liters); put("place", Crypto.encStr(c, e.place)); put("note", Crypto.encStr(c, e.note)) }
        return db(c).insert("entries", null, cv)
    }
    fun deleteVehicle(c: Context, id: Long) { db(c).delete("entries", "vehicle=?", arrayOf(id.toString())); db(c).delete("vehicles", "id=?", arrayOf(id.toString())) }
}

class VehicleActivity : BaseActivity() {
    private lateinit var root: LinearLayout
    private lateinit var owner: String
    private var selectedVehicle = 0L
    private var pendingOcr: ((String) -> Unit)? = null
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); owner = getSharedPreferences("arsipku_users", MODE_PRIVATE).getString("active", "Saya") ?: "Saya"; render() }
    private fun money(v: Long) = "Rp " + NumberFormat.getNumberInstance(Locale("id", "ID")).format(v)
    private fun date(ms: Long) = SimpleDateFormat("dd-MM-yyyy", Locale("id", "ID")).format(Date(ms))
    private fun base(title: String) {
        val scroll = ScrollView(this); scroll.isFillViewport = true
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), inT + dp(16), dp(18), inB + dp(28)); setBackgroundColor(if (P.night) 0xFF101827.toInt() else 0xFFF4F7FC.toInt()) }
        scroll.addView(root); setRoot(scroll) { root.setPadding(dp(18), inT + dp(16), dp(18), inB + dp(28)) }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = TextView(this).apply { text = "‹"; textSize = 32f; setTextColor(if (P.night) Color.WHITE else 0xFF152238.toInt()); setPadding(0,0,dp(14),0); setOnClickListener { finish() } }
        head.addView(back)
        head.addView(TextView(this).apply { text = title; textSize = 23f; setTypeface(null, 1); setTextColor(if (P.night) Color.WHITE else 0xFF152238.toInt()) }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(TextView(this).apply { text = "＋"; textSize = 27f; setTextColor(0xFF078BC6.toInt()); setOnClickListener { addVehicle() } })
        root.addView(head)
        root.addView(TextView(this).apply { text = "Kendaraan & biaya perawatan · $owner"; textSize = 13f; setTextColor(0xFF7C899B.toInt()); setPadding(0, dp(4),0,dp(16)) })
    }
    private fun card(title: String, sub: String, click: (() -> Unit)? = null) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16),dp(14),dp(16),dp(14)); background = android.graphics.drawable.GradientDrawable().apply { cornerRadius=dp(18).toFloat(); setColor(if(P.night) 0xFF1D293B.toInt() else Color.WHITE); setStroke(dp(1), if(P.night) 0xFF334155.toInt() else 0xFFD8E1EA.toInt()) }; if(click!=null) setOnClickListener { click() } }
        box.addView(TextView(this).apply { text=title; textSize=16f; setTypeface(null,1); setTextColor(if(P.night) Color.WHITE else 0xFF152238.toInt()) })
        box.addView(TextView(this).apply { text=sub; textSize=13f; setTextColor(0xFF7C899B.toInt()); setPadding(0,dp(5),0,0) })
        root.addView(box, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(10) })
    }
    private fun action(label: String, click: () -> Unit) {
        val b=Button(this).apply { text=label; isAllCaps=false; setOnClickListener { click() } }
        root.addView(b, LinearLayout.LayoutParams(-1,-2).apply { bottomMargin=dp(7) })
    }
    private fun render() {
        base("Garasi & Kendaraan")
        val vehicles=VehicleStore.vehicles(this,owner)
        val all=VehicleStore.entries(this,owner)
        val year=Calendar.getInstance().get(Calendar.YEAR)
        val yearly=all.filter { Calendar.getInstance().apply { timeInMillis=it.date }.get(Calendar.YEAR)==year }
        card("Ringkasan biaya perawatan", "${money(yearly.sumOf { it.amount })}\nTahun $year · ${yearly.size} transaksi")
        action("＋ Tambah kendaraan", ::addVehicle)
        action("＋ Catat servis", { chooseVehicle("servis") })
        action("⛽ Isi BBM", { chooseVehicle("BBM") })
        action("📊 Laporan bulanan / tahunan / semua / rentang tanggal", ::reports)
        action("📷 Scan nota dengan OCR", { scanReceipt { text -> entryDialog(vehicles, "Servis", text) } })
        root.addView(TextView(this).apply { text="KENDARAAN SAYA"; textSize=14f; setTypeface(null,1); setTextColor(0xFF7C899B.toInt()); setPadding(0,dp(12),0,dp(10)) })
        if(vehicles.isEmpty()) card("Belum ada kendaraan", "Tambahkan mobil atau motor pertama Anda")
        vehicles.forEach { v ->
            val rows=all.filter { it.vehicle==v.id }
            val lastService=rows.filter { it.type.contains("servis",true) && it.km>0 }.maxByOrNull { it.date }
            if(lastService!=null && v.km-lastService.km>=5000) card("⚠️ Pengingat servis · ${v.name}", "Sudah ${v.km-lastService.km} km sejak servis terakhir. Periksa oli dan jadwal servis berkala.")
            val taxDate=try{SimpleDateFormat("dd-MM-yyyy",Locale("id","ID")).parse(v.tax)?.time?:0L}catch(_:Exception){0L}
            if(taxDate>0 && taxDate-System.currentTimeMillis()<=30L*86400000L && taxDate>=System.currentTimeMillis()) card("⚠️ Pajak segera jatuh tempo · ${v.name}", "Jatuh tempo ${v.tax}")
            card("${v.name} · ${v.plate}", "${v.km} km · ${v.year} · ${rows.size} transaksi · Pajak: ${v.tax.ifBlank { "belum diatur" }}") {
                selectedVehicle=v.id
                AlertDialog.Builder(this).setTitle(v.name).setItems(arrayOf("Catat servis", "Isi BBM", "Riwayat transaksi", "Edit kendaraan", "Hapus kendaraan")) { _, i -> when(i) { 0->entryDialog(vehicles,"Servis",""); 1->entryDialog(vehicles,"BBM",""); 2->history(v); 3->editVehicle(v); 4->AlertDialog.Builder(this).setMessage("Hapus kendaraan dan seluruh riwayatnya?").setNegativeButton("Batal",null).setPositiveButton("Hapus"){_,_->VehicleStore.deleteVehicle(this,v.id);render()}.show() } }.show()
            }
        }
    }
    private fun addVehicle() { vehicleForm(null) }
    private fun editVehicle(v: Vehicle) { vehicleForm(v) }
    private fun vehicleForm(old: Vehicle?) {
        val fields=(0..4).map { EditText(this).apply { textSize=16f; setSingleLine(); hint=arrayOf("Nama / tipe kendaraan","Nomor polisi","Tahun kendaraan","Kilometer saat ini","Jatuh tempo pajak (DD-MM-YYYY)")[it]; setText(arrayOf(old?.name,old?.plate,old?.year,old?.km?.toString(),old?.tax)[it] ?: "") } }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0) }; fields.forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle(if(old==null) "Tambah kendaraan" else "Edit kendaraan").setView(box).setNegativeButton("Batal",null).setPositiveButton("Simpan"){_,_->
            val km=fields[3].text.toString().toLongOrNull() ?: 0L
            VehicleStore.saveVehicle(this,Vehicle(old?.id?:0,owner,fields[0].text.toString().ifBlank{"Kendaraan"},fields[1].text.toString(),fields[2].text.toString(),km,fields[4].text.toString()));render()
        }.show()
    }
    private fun chooseVehicle(type: String) {
        val vs=VehicleStore.vehicles(this,owner); if(vs.isEmpty()){addVehicle();return}
        AlertDialog.Builder(this).setTitle("Pilih kendaraan").setItems(vs.map{"${it.name} · ${it.plate}"}.toTypedArray()){_,i->selectedVehicle=vs[i].id;entryDialog(vs,type,"")}.show()
    }
    private fun entryDialog(vs: List<Vehicle>, type: String, ocr: String) {
        if(selectedVehicle==0L) selectedVehicle=vs.firstOrNull()?.id?:0L
        if(selectedVehicle==0L){toast("Tambahkan kendaraan terlebih dahulu");return}
        val names= arrayOf("Jenis / kategori","Tanggal (DD-MM-YYYY)","Total biaya (Rp)","Kilometer","Liter BBM (jika BBM)","Bengkel / SPBU","Catatan / hasil OCR")
        val hints=arrayOf("Servis / BBM / Pajak / Suku cadang",date(System.currentTimeMillis()),"0","0","0","Nama tempat", "")
        val detectedType=if(ocr.contains("Kategori terdeteksi: BBM",true)) "BBM" else type
        val detectedAmount=Regex("(?i)Nominal terdeteksi: Rp\\s*([0-9]+)").find(ocr)?.groupValues?.get(1) ?: ""
        val values= arrayOf(detectedType,date(System.currentTimeMillis()),detectedAmount,"","","",ocr)
        val es=names.indices.map { i->EditText(this).apply { hint=hints[i]; setSingleLine(i!=6); textSize=15f; setText(values[i]) } }
        val box=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(dp(20),0,dp(20),0) }; es.forEach { box.addView(it) }
        AlertDialog.Builder(this).setTitle("Catat transaksi").setView(ScrollView(this).apply{addView(box)}).setNegativeButton("Batal",null).setPositiveButton("Simpan"){_,_->
            val parsed=try{SimpleDateFormat("dd-MM-yyyy",Locale("id","ID")).parse(es[1].text.toString())?.time?:System.currentTimeMillis()}catch(_:Exception){System.currentTimeMillis()}
            val amount=es[2].text.toString().filter{it.isDigit()}.toLongOrNull()?:0L
            val km=es[3].text.toString().filter{it.isDigit()}.toLongOrNull()?:0L
            val liters=es[4].text.toString().replace(',','.').toDoubleOrNull()?:0.0
            VehicleStore.saveEntry(this,VehicleEntry(0,selectedVehicle,owner,es[0].text.toString().ifBlank{type},parsed,amount,km,liters,es[5].text.toString(),es[6].text.toString()))
            if(km>0) vs.firstOrNull{it.id==selectedVehicle}?.let { VehicleStore.saveVehicle(this,it.copy(km=maxOf(km,it.km))) }
            toast("Transaksi tersimpan");render()
        }.show()
    }
    private fun history(v: Vehicle) {
        val rows=VehicleStore.entries(this,owner).filter{it.vehicle==v.id}
        val text=rows.joinToString("\n\n"){ "${date(it.date)} · ${it.type}\n${money(it.amount)} · ${it.km} km\n${it.place}\n${it.note}" }.ifBlank{"Belum ada transaksi"}
        AlertDialog.Builder(this).setTitle("Riwayat ${v.name}").setMessage(text).setPositiveButton("Tutup",null).show()
    }
    private fun reports() {
        val opts=arrayOf("Bulanan (bulan ini)","Tahunan (tahun ini)","Semua waktu","Rentang tanggal khusus")
        AlertDialog.Builder(this).setTitle("Periode laporan").setItems(opts){_,i->
            val cal=Calendar.getInstance(); val from:Long; val to:Long
            when(i){0->{cal.set(Calendar.DAY_OF_MONTH,1);from=cal.timeInMillis;to=System.currentTimeMillis()};1->{cal.set(Calendar.DAY_OF_YEAR,1);from=cal.timeInMillis;to=System.currentTimeMillis()};2->{from=0;to=Long.MAX_VALUE};else->{rangeReport();return@setItems}}
            showReport(from,to,opts[i])
        }.show()
    }
    private fun rangeReport() {
        val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(dp(20),0,dp(20),0)}
        val a=EditText(this).apply{hint="Tanggal awal DD-MM-YYYY"}; val b=EditText(this).apply{hint="Tanggal akhir DD-MM-YYYY"};box.addView(a);box.addView(b)
        AlertDialog.Builder(this).setTitle("Rentang laporan").setView(box).setNegativeButton("Batal",null).setPositiveButton("Tampilkan"){_,_->
            try{val f=SimpleDateFormat("dd-MM-yyyy",Locale("id","ID"));val start=f.parse(a.text.toString())!!.time;val end=f.parse(b.text.toString())!!.time+86399999;showReport(start,end,"${a.text} – ${b.text}")}catch(_:Exception){toast("Format tanggal tidak valid")}
        }.show()
    }
    private fun showReport(from:Long,to:Long,label:String) {
        val rows=VehicleStore.entries(this,owner,from,to);val total=rows.sumOf{it.amount};val fuel=rows.filter{it.type.contains("BBM",true)};val service=rows.filter{it.type.contains("servis",true)}
        val msg="Periode: $label\nTotal pengeluaran: ${money(total)}\nServis: ${money(service.sumOf{it.amount})} (${service.size} transaksi)\nBBM: ${money(fuel.sumOf{it.amount})} (${fuel.size} transaksi)\n\n"+rows.joinToString("\n"){"${date(it.date)} · ${it.type} · ${money(it.amount)} · ${it.km} km"}
        AlertDialog.Builder(this).setTitle("Laporan ArsipKu").setMessage(msg.ifBlank{"Belum ada transaksi"}).setPositiveButton("Tutup",null).setNeutralButton("Bagikan"){_,_->val send=Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT,msg);startActivity(Intent.createChooser(send,"Bagikan laporan"))}.show()
    }
    private fun scanReceipt(done:(String)->Unit) {
        pendingOcr=done
        launch(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"),88)
    }
    @Deprecated("Legacy picker for API compatibility")
    override fun onActivityResult(requestCode:Int,resultCode:Int,data:Intent?) { super.onActivityResult(requestCode,resultCode,data);if(requestCode==88&&resultCode==RESULT_OK){val uri=data?.data?:return;try{
        val image=InputImage.fromFilePath(this,uri);val recognizer=TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image).addOnSuccessListener { result ->
            val raw=result.text
            // Conservative extraction: fill note text, suggest category and amount when confidently found.
            val amount=Regex("(?:Rp\\.?\\s*)?([0-9][0-9.]{2,})").findAll(raw).mapNotNull{it.groupValues[1].replace(".","").toLongOrNull()}.maxOrNull()
            val category=when { Regex("(?i)spbu|pertamina|liter|bensin|solar").containsMatchIn(raw)->"BBM"; Regex("(?i)servis|service|oli|bengkel|spare.?part").containsMatchIn(raw)->"Servis"; else->"Servis" }
            val enriched="Kategori terdeteksi: $category"+(amount?.let{"\nNominal terdeteksi: Rp $it"}?:"")+"\n\n$raw"
            pendingOcr?.invoke(enriched);pendingOcr=null
        }.addOnFailureListener{toast("OCR gagal membaca nota: ${it.localizedMessage}")}.addOnCompleteListener{recognizer.close()}
    }catch(e:Exception){toast("Tidak dapat membuka gambar")}} }
}
