package com.alhafidz.app

import android.content.Context
import android.graphics.Paint
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.Locale

object Fmt {
    private val mon = arrayOf("Jan", "Feb", "Mar", "Apr", "Mei", "Jun", "Jul", "Agu", "Sep", "Okt", "Nov", "Des")
    fun today(): Long = LocalDate.now().toEpochDay()
    fun date(day: Long): String {
        val d = LocalDate.ofEpochDay(day)
        return "${d.dayOfMonth} ${mon[d.monthValue - 1]} ${d.year}"
    }
    fun stamp(ms: Long): String {
        val t = LocalDateTime.ofInstant(Instant.ofEpochMilli(ms), ZoneId.systemDefault())
        val hm = String.format(Locale.US, "%02d.%02d", t.hour, t.minute)
        return if (t.toLocalDate() == LocalDate.now()) "hari ini, $hm"
        else "${t.dayOfMonth} ${mon[t.monthValue - 1]} ${t.year}, $hm"
    }
    fun human(d: Long): String = when {
        d < 60 -> "$d hari"
        d < 730 -> "${d / 30} bulan"
        else -> "${d / 365} tahun"
    }
    private val dayName = arrayOf("Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu", "Minggu")
    private val monFull = arrayOf("Januari", "Februari", "Maret", "April", "Mei", "Juni", "Juli", "Agustus", "September", "Oktober", "November", "Desember")
    /** Contoh: "Senin, 08 Agustus 2027" */
    fun full(day: Long): String {
        val d = LocalDate.ofEpochDay(day)
        return String.format(Locale.US, "%s, %02d %s %d", dayName[d.dayOfWeek.value - 1], d.dayOfMonth, monFull[d.monthValue - 1], d.year)
    }
    fun hour(h: Int): String = String.format(Locale.US, "%02d.00", h)
}

object Cat {
    val names = arrayOf("Identitas", "Kendaraan", "Pendidikan", "Aset", "Keluarga", "Lainnya")
    private val icons = arrayOf("\uD83E\uDEAA", "\uD83D\uDE97", "\uD83C\uDF93", "\uD83C\uDFE0", "\uD83D\uDC6A", "\uD83D\uDCC1")
    fun icon(i: Int): String = if (i == 0 && !Paint().hasGlyph(icons[0])) "\uD83D\uDCB3" else icons[i]
    private val light = longArrayOf(0xFFEAF1FF, 0xFFE6F7EC, 0xFFF0EAFF, 0xFFFFEFE2, 0xFFFFE8EF, 0xFFFFF5D9)
    private val dark = longArrayOf(0xFF1B2A4A, 0xFF17352A, 0xFF2B2447, 0xFF3A2C20, 0xFF3A2230, 0xFF3A3420)
    fun tint(i: Int): Int = (if (P.night) dark[i] else light[i]).toInt()
}

/** level: 0 = merah (habis / segera), 1 = biru (mendatang), 2 = hijau (aman / seumur hidup) */
class Status(val level: Int, val label: String, val days: Long, val num: String, val unit: String, val progress: Float)

fun statusOf(c: Context, d: Doc): Status {
    if (d.lifetime) return Status(2, "Berlaku seumur hidup", Long.MAX_VALUE, "\u221E", "", 1f)
    val days = d.expiry - Fmt.today()
    val win = Prefs.int(c, K.DAYS, 30)
    val num: String
    val unit: String
    when {
        days < 0 -> { num = "!"; unit = "habis" }
        days < 60 -> { num = "$days"; unit = "hari" }
        days < 730 -> { num = "${days / 30}"; unit = "bulan" }
        else -> { num = "${days / 365}"; unit = "tahun" }
    }
    return when {
        days < 0 -> Status(0, "Kadaluarsa ${Fmt.human(-days)} lalu", days, num, unit, 0.06f)
        days == 0L -> Status(0, "Habis hari ini", days, "0", "hari", 0.05f)
        days <= win -> Status(0, "Habis $days hari lagi", days, num, unit, (days.toFloat() / win).coerceIn(0.08f, 1f))
        days <= 365 -> Status(1, "Berlaku s.d. ${Fmt.date(d.expiry)}", days, num, unit, (days / 365f).coerceIn(0.1f, 1f))
        else -> Status(2, "Berlaku s.d. ${Fmt.date(d.expiry)}", days, num, unit, 0.92f)
    }
}
