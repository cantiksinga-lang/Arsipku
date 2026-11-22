package com.alhafidz.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.Locale

/** Snapshot data kendaraan yang ditulis oleh Flutter, dibaca oleh widget & pengingat. */
object Snap {
    const val PREF = "garasiku"

    fun save(c: Context, json: String) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("snapshot", json).apply()
    }

    fun root(c: Context): JSONObject {
        val raw = c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getString("snapshot", "{}") ?: "{}"
        return try { JSONObject(raw) } catch (e: Exception) { JSONObject() }
    }

    fun widgetVehicles(c: Context): List<JSONObject> {
        val arr = root(c).optJSONArray("vehicles") ?: JSONArray()
        val out = ArrayList<JSONObject>()
        for (i in 0 until arr.length()) {
            val v = arr.optJSONObject(i) ?: continue
            if (v.optBoolean("widget", true)) out.add(v)
        }
        return out
    }

    fun has(o: JSONObject, k: String) = o.has(k) && !o.isNull(k)

    fun fmt(n: Long): String = NumberFormat.getInstance(Locale("id", "ID")).format(n)
}
