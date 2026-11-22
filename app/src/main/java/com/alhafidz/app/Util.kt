package com.alhafidz.app

import android.content.Context
import android.widget.Toast
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

val ID_LOCALE: Locale = Locale("id", "ID")

fun num(n: Number): String = NumberFormat.getInstance(ID_LOCALE).format(n)
fun rupiah(n: Long): String = "Rp " + num(n)
fun fmtDate(ms: Long): String = if (ms <= 0) "-" else SimpleDateFormat("dd MMM yyyy", ID_LOCALE).format(Date(ms))
fun LocalDate.ms(): Long = atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
fun Long.toLocal(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalDate()
fun daysLeft(expiry: Long): Int = ChronoUnit.DAYS.between(LocalDate.now(), expiry.toLocal()).toInt()

fun agoText(ms: Long): String {
    val d = ChronoUnit.DAYS.between(ms.toLocal(), LocalDate.now()).toInt()
    return when { d <= 0 -> "hari ini"; d == 1 -> "kemarin"; else -> "$d hari lalu" }
}

fun greeting(): String = when (LocalTime.now().hour) {
    in 4..10 -> "Selamat Pagi"
    in 11..14 -> "Selamat Siang"
    in 15..17 -> "Selamat Sore"
    else -> "Selamat Malam"
}

fun toast(ctx: Context, m: String) = Toast.makeText(ctx, m, Toast.LENGTH_SHORT).show()

enum class Lv { OK, WARN, BAD, NONE }

fun lvDays(d: Int): Lv = if (d <= 7) Lv.BAD else if (d <= Prefs.docWarn) Lv.WARN else Lv.OK
fun lvKm(left: Int, warn: Int): Lv = if (left <= 0) Lv.BAD else if (left <= warn) Lv.WARN else Lv.OK

fun latestDoc(docs: List<Doc>, vid: String, type: String): Doc? =
    docs.filter { it.vehicleId == vid && it.type == type && it.expiry > 0 }.maxByOrNull { it.expiry }

fun alertCount(vs: List<Vehicle>, ds: List<Doc>): Int =
    vs.count { lvKm(it.oilLeft, Prefs.oilWarn) != Lv.OK || lvKm(it.serviceLeft, Prefs.svcWarn) != Lv.OK } +
        ds.count { it.expiry > 0 && daysLeft(it.expiry) <= Prefs.docWarn }

fun daysText(d: Int, label: String): String = if (d < 0) "$label: terlambat ${-d} hari" else "$label: $d hari lagi"
fun kmText(left: Int, label: String): String = if (left < 0) "$label: lewat ${num(-left)} km" else "$label: ${num(left)} km lagi"
