package com.alhafidz.app

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object Repo {
    private lateinit var app: Context
    val vehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val expenses = MutableStateFlow<List<Expense>>(emptyList())
    val docs = MutableStateFlow<List<Doc>>(emptyList())

    private val dataFile: File get() = File(app.filesDir, "data.json")
    val filesDir: File get() = File(app.filesDir, "files").also { it.mkdirs() }

    fun init(c: Context) {
        app = c.applicationContext
        load()
    }

    @Synchronized
    fun load() {
        val f = dataFile
        if (!f.exists()) return
        try {
            val o = JSONObject(f.readText())
            vehicles.value = o.optJSONArray("vehicles").mapObj { Vehicle.from(it) }
            expenses.value = o.optJSONArray("expenses").mapObj { Expense.from(it) }
            docs.value = o.optJSONArray("docs").mapObj { Doc.from(it) }
        } catch (e: Exception) { }
    }

    @Synchronized
    private fun persist() {
        val o = JSONObject()
            .put("vehicles", JSONArray(vehicles.value.map { it.toJson() }))
            .put("expenses", JSONArray(expenses.value.map { it.toJson() }))
            .put("docs", JSONArray(docs.value.map { it.toJson() }))
        val tmp = File(app.filesDir, "data.json.tmp")
        tmp.writeText(o.toString())
        tmp.renameTo(dataFile)
    }

    private fun commit() {
        persist()
        GarasiWidget.refresh(app)
        Notif.check(app)
    }

    fun vehicle(id: String?): Vehicle? = vehicles.value.firstOrNull { it.id == id }
    fun vehicleName(id: String?): String = vehicle(id)?.let { it.name } ?: "-"

    fun saveVehicle(v: Vehicle) {
        val list = vehicles.value
        vehicles.value = if (list.any { it.id == v.id }) list.map { if (it.id == v.id) v else it } else list + v
        commit()
    }

    fun deleteVehicle(id: String) {
        expenses.value.filter { it.vehicleId == id }.forEach { it.receipt?.let { r -> File(filesDir, r).delete() } }
        docs.value.filter { it.vehicleId == id }.forEach { it.file?.let { r -> File(filesDir, r).delete() } }
        vehicle(id)?.photo?.let { File(filesDir, it).delete() }
        vehicles.value = vehicles.value.filter { it.id != id }
        expenses.value = expenses.value.filter { it.vehicleId != id }
        docs.value = docs.value.filter { it.vehicleId != id }
        commit()
    }

    fun updateOdo(id: String, km: Int) {
        vehicles.value = vehicles.value.map { if (it.id == id) it.copy(odo = km, odoAt = System.currentTimeMillis()) else it }
        commit()
    }

    fun saveExpense(e: Expense, resetInterval: Boolean) {
        val list = expenses.value
        expenses.value = if (list.any { it.id == e.id }) list.map { if (it.id == e.id) e else it } else list + e
        val v = vehicle(e.vehicleId)
        if (v != null) {
            var nv = v
            if (e.odo > v.odo) nv = nv.copy(odo = e.odo, odoAt = System.currentTimeMillis())
            val km = if (e.odo > 0) e.odo else nv.odo
            if (resetInterval && e.category == "OLI") nv = nv.copy(lastOilKm = km)
            if (resetInterval && e.category == "SERVIS") nv = nv.copy(lastServiceKm = km)
            vehicles.value = vehicles.value.map { if (it.id == nv.id) nv else it }
        }
        commit()
    }

    fun deleteExpense(id: String) {
        expenses.value.firstOrNull { it.id == id }?.receipt?.let { File(filesDir, it).delete() }
        expenses.value = expenses.value.filter { it.id != id }
        commit()
    }

    fun saveDoc(d: Doc) {
        val list = docs.value
        docs.value = if (list.any { it.id == d.id }) list.map { if (it.id == d.id) d else it } else list + d
        commit()
    }

    fun deleteDoc(id: String) {
        docs.value.firstOrNull { it.id == id }?.file?.let { File(filesDir, it).delete() }
        docs.value = docs.value.filter { it.id != id }
        commit()
    }

    /** Salin gambar (dikompres JPG) atau PDF ke penyimpanan app. Mengembalikan nama file. */
    fun importUri(uri: Uri, prefix: String): String? {
        try {
            val cr = app.contentResolver
            val mime = cr.getType(uri) ?: ""
            val isPdf = mime.contains("pdf") || (uri.lastPathSegment ?: "").lowercase().endsWith(".pdf")
            val name = "${prefix}_${System.currentTimeMillis()}." + (if (isPdf) "pdf" else "jpg")
            val out = File(filesDir, name)
            if (isPdf) {
                cr.openInputStream(uri)!!.use { i -> out.outputStream().use { o -> i.copyTo(o) } }
            } else {
                val bmp = Img.decodeUri(app, uri, 2000) ?: return null
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            }
            return name
        } catch (e: Exception) {
            return null
        }
    }

    fun backup(uri: Uri): Boolean {
        return try {
            persist()
            app.contentResolver.openOutputStream(uri)!!.use { os ->
                ZipOutputStream(os).use { z ->
                    z.putNextEntry(ZipEntry("data.json")); z.write(dataFile.readBytes()); z.closeEntry()
                    z.putNextEntry(ZipEntry("meta.json"))
                    z.write(JSONObject().put("name", Prefs.name).toString().toByteArray()); z.closeEntry()
                    filesDir.listFiles()?.forEach { f ->
                        z.putNextEntry(ZipEntry("files/${f.name}"))
                        f.inputStream().use { it.copyTo(z) }
                        z.closeEntry()
                    }
                }
            }
            true
        } catch (e: Exception) { false }
    }

    fun restore(uri: Uri): Boolean {
        return try {
            val tmp = File(app.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
            var hasData = false
            var meta: String? = null
            app.contentResolver.openInputStream(uri)!!.use { ins ->
                ZipInputStream(ins).use { z ->
                    var e = z.nextEntry
                    while (e != null) {
                        val n = e.name
                        if (!e.isDirectory) {
                            if (n == "data.json") {
                                File(tmp, "data.json").outputStream().use { z.copyTo(it) }; hasData = true
                            } else if (n == "meta.json") {
                                meta = z.readBytes().toString(Charsets.UTF_8)
                            } else if (n.startsWith("files/")) {
                                File(tmp, "f_" + n.substringAfterLast('/')).outputStream().use { z.copyTo(it) }
                            }
                        }
                        e = z.nextEntry
                    }
                }
            }
            if (!hasData) {
                false
            } else {
                filesDir.listFiles()?.forEach { it.delete() }
                tmp.listFiles()?.filter { it.name.startsWith("f_") }?.forEach {
                    it.copyTo(File(filesDir, it.name.removePrefix("f_")), true)
                }
                File(tmp, "data.json").copyTo(dataFile, true)
                val m = meta
                if (m != null) runCatching { Prefs.name = JSONObject(m).optString("name", Prefs.name); Prefs.persist() }
                load()
                GarasiWidget.refresh(app)
                true
            }
        } catch (e: Exception) { false }
    }
}
