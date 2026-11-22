package com.alhafidz.app

import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)

private fun JSONObject.str(k: String): String? = if (has(k) && !isNull(k)) getString(k) else null

data class Vehicle(
    val id: String = newId(),
    val name: String = "",
    val plate: String = "",
    val type: String = "Motor",
    val odo: Int = 0,
    val odoAt: Long = System.currentTimeMillis(),
    val photo: String? = null,
    val oilInterval: Int = 2000,
    val lastOilKm: Int = 0,
    val serviceInterval: Int = 3000,
    val lastServiceKm: Int = 0,
    val taxEst: Long = 0
) {
    val oilLeft: Int get() = lastOilKm + oilInterval - odo
    val serviceLeft: Int get() = lastServiceKm + serviceInterval - odo

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("plate", plate).put("type", type)
        .put("odo", odo).put("odoAt", odoAt).put("photo", photo ?: JSONObject.NULL)
        .put("oilInterval", oilInterval).put("lastOilKm", lastOilKm)
        .put("serviceInterval", serviceInterval).put("lastServiceKm", lastServiceKm)
        .put("taxEst", taxEst)

    companion object {
        fun from(o: JSONObject) = Vehicle(
            id = o.getString("id"), name = o.optString("name"), plate = o.optString("plate"),
            type = o.optString("type", "Motor"), odo = o.optInt("odo"), odoAt = o.optLong("odoAt"),
            photo = o.str("photo"), oilInterval = o.optInt("oilInterval", 2000),
            lastOilKm = o.optInt("lastOilKm"), serviceInterval = o.optInt("serviceInterval", 3000),
            lastServiceKm = o.optInt("lastServiceKm"), taxEst = o.optLong("taxEst")
        )
    }
}

data class Expense(
    val id: String = newId(),
    val vehicleId: String,
    val date: Long,
    val category: String,
    val title: String,
    val amount: Long,
    val odo: Int = 0,
    val liters: Double = 0.0,
    val note: String = "",
    val receipt: String? = null
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("vehicleId", vehicleId).put("date", date).put("category", category)
        .put("title", title).put("amount", amount).put("odo", odo).put("liters", liters)
        .put("note", note).put("receipt", receipt ?: JSONObject.NULL)

    companion object {
        fun from(o: JSONObject) = Expense(
            id = o.getString("id"), vehicleId = o.getString("vehicleId"), date = o.optLong("date"),
            category = o.optString("category", "LAINNYA"), title = o.optString("title"),
            amount = o.optLong("amount"), odo = o.optInt("odo"), liters = o.optDouble("liters", 0.0),
            note = o.optString("note"), receipt = o.str("receipt")
        )
    }
}

data class Doc(
    val id: String = newId(),
    val vehicleId: String? = null,
    val type: String = "SIM",
    val name: String = "",
    val number: String = "",
    val expiry: Long = 0,
    val file: String? = null,
    val note: String = ""
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("vehicleId", vehicleId ?: JSONObject.NULL).put("type", type)
        .put("name", name).put("number", number).put("expiry", expiry)
        .put("file", file ?: JSONObject.NULL).put("note", note)

    companion object {
        fun from(o: JSONObject) = Doc(
            id = o.getString("id"), vehicleId = o.str("vehicleId"), type = o.optString("type", "SIM"),
            name = o.optString("name"), number = o.optString("number"), expiry = o.optLong("expiry"),
            file = o.str("file"), note = o.optString("note")
        )
    }
}

fun <T> JSONArray?.mapObj(f: (JSONObject) -> T): List<T> =
    if (this == null) emptyList() else List(length()) { f(getJSONObject(it)) }

object Cat {
    val all = listOf("SERVIS", "BBM", "OLI", "SPAREPART", "PAJAK", "LAINNYA")
    fun label(c: String) = when (c) {
        "SERVIS" -> "Servis"; "BBM" -> "BBM"; "OLI" -> "Ganti Oli"
        "SPAREPART" -> "Sparepart"; "PAJAK" -> "Pajak"; else -> "Lainnya"
    }
    fun color(c: String) = when (c) {
        "SERVIS" -> Color(0xFF0B79C2); "BBM" -> Color(0xFFE07B1A); "OLI" -> Color(0xFF8E44AD)
        "SPAREPART" -> Color(0xFF16A085); "PAJAK" -> Color(0xFFC0392B); else -> Color(0xFF7F8C8D)
    }
}

object DocT {
    val all = listOf("SIM", "STNK", "PLAT5")
    fun label(t: String) = when (t) {
        "SIM" -> "SIM"; "STNK" -> "STNK (Pajak Tahunan)"; else -> "Pajak Plat 5 Tahunan"
    }
    fun short(t: String) = when (t) { "SIM" -> "SIM"; "STNK" -> "Pajak Tahunan"; else -> "Plat 5 Tahun" }
    fun cycle(t: String) = if (t == "STNK") 365 else 1825
}
