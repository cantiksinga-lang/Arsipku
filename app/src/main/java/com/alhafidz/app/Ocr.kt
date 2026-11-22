package com.alhafidz.app

import android.content.Context
import android.net.Uri
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

data class Receipt(
    val amount: Long?, val title: String?, val category: String?,
    val date: LocalDate?, val liters: Double?, val odo: Int?
)

data class DocRes(val name: String?, val number: String?, val expiry: LocalDate?, val owner: String?)

object Ocr {
    private val rec by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    suspend fun read(ctx: Context, name: String): String {
        return try {
            val f = File(Repo.filesDir, name)
            val img = withContext(Dispatchers.IO) {
                if (name.endsWith(".pdf")) Img.pdfPage(f, 0, 1600)?.let { InputImage.fromBitmap(it, 0) }
                else InputImage.fromFilePath(ctx, Uri.fromFile(f))
            } ?: return ""
            rec.process(img).await().text
        } catch (e: Exception) { "" }
    }

    private val numRe = Regex("""\d{1,3}(?:[.,]\d{3})+|\d{4,}""")
    private fun numbers(line: String): List<Long> =
        numRe.findAll(line).mapNotNull { it.value.replace(".", "").replace(",", "").toLongOrNull() }.toList()

    private val mon = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "mei" to 5, "may" to 5, "jun" to 6, "jul" to 7,
        "agu" to 8, "agt" to 8, "aug" to 8, "sep" to 9, "okt" to 10, "oct" to 10, "nov" to 11, "des" to 12, "dec" to 12
    )

    private fun mk(y0: Int, m: Int, d: Int): LocalDate? {
        val y = if (y0 < 100) 2000 + y0 else y0
        return try { LocalDate.of(y, m, d) } catch (e: Exception) { null }
    }

    fun dates(line: String): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        Regex("""(\d{1,2})\s*[/\-.]\s*(\d{1,2})\s*[/\-.]\s*(\d{2,4})""").findAll(line).forEach { m ->
            mk(m.groupValues[3].toInt(), m.groupValues[2].toInt(), m.groupValues[1].toInt())?.let { out.add(it) }
        }
        Regex("""(\d{1,2})\s+([A-Za-z]{3,9})\s+(\d{2,4})""").findAll(line).forEach { m ->
            val mm = mon[m.groupValues[2].lowercase().take(3)]
            if (mm != null) mk(m.groupValues[3].toInt(), mm, m.groupValues[1].toInt())?.let { out.add(it) }
        }
        return out
    }

    fun parseReceipt(text: String): Receipt {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val excl = Regex("(?i)(tunai|cash|kembali|change|diterima|telp|npwp|hp\\b)")
        val key = Regex("(?i)(grand\\s*total|total|jumlah|tagihan|amount|bayar)")
        val cands = mutableListOf<Long>()
        lines.forEachIndexed { i, l ->
            if (key.containsMatchIn(l) && !excl.containsMatchIn(l) && !l.lowercase().contains("subtotal")) {
                var n = numbers(l)
                if (n.isEmpty() && i + 1 < lines.size) n = numbers(lines[i + 1])
                cands.addAll(n)
            }
        }
        var amount: Long? = cands.filter { it in 1000..100_000_000 }.maxOrNull()
        if (amount == null) {
            amount = lines.filter { !excl.containsMatchIn(it) }.flatMap { numbers(it) }
                .filter { it in 1000..50_000_000 }.maxOrNull()
        }

        val skip = Regex("(?i)(total|tunai|kembali|cash|change|kasir|tanggal|date|alamat|telp|\\btel\\b|npwp|invoice|nota|struk|ppn|pajak|subtotal|bayar|terima kasih|thank|jl\\.|jalan|www|\\.com|diskon|qty|harga|faktur|customer|pelanggan|nopol|no\\.)")
        val parts = Regex("(?i)(oli|ban|kampas|filter|busi|aki|rantai|gear|kabel|lampu|shock|belt|roller|kopling|rem|servis|service|ganti|cvt|pelumas|minyak|radiator|coolant|wiper|knalpot|bearing|seal|handle|spion|cairan|tune ?up|jasa|bensin|pertalite|pertamax|solar|dexlite)")
        fun clean(s: String) = s.replace(Regex("[\\d.,]{3,}"), " ").replace(Regex("(?i)\\brp\\b\\.?"), " ")
            .replace(Regex("\\s+"), " ").trim(' ', '-', ':', 'x', 'X', '*')
        val cl = lines.filter { l -> l.count { it.isLetter() } >= 3 && !skip.containsMatchIn(l) }
        var items = cl.filter { parts.containsMatchIn(it) }.map { clean(it) }.filter { it.length >= 3 }.distinct().take(3)
        if (items.isEmpty()) items = cl.drop(1).map { clean(it) }.filter { it.length >= 3 }.take(1)
        val title = items.joinToString(", ").take(70).ifBlank { null }

        val low = text.lowercase()
        val category = when {
            Regex("(?i)(pertalite|pertamax|bensin|dexlite|spbu|pertamina|bbm|solar|liter)").containsMatchIn(text) -> "BBM"
            Regex("(?i)(servis|service|jasa|tune)").containsMatchIn(text) -> "SERVIS"
            low.contains("oli") || low.contains("pelumas") -> "OLI"
            parts.containsMatchIn(text) -> "SPAREPART"
            else -> null
        }
        val liters = Regex("(?i)(\\d{1,3}[.,]\\d{1,3})\\s*(?:l|lt|ltr|liter)\\b").find(text)
            ?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
        val odo = Regex("(?i)(?:km|odo|odometer)\\D{0,6}(\\d{1,3}(?:[.,]\\d{3})+|\\d{3,6})").find(text)
            ?.groupValues?.get(1)?.replace(".", "")?.replace(",", "")?.toIntOrNull()?.takeIf { it in 100..999_999 }
        val date = lines.flatMap { dates(it) }.firstOrNull()
        return Receipt(amount, title, category, date, if (category == "BBM") liters else null, odo)
    }

    fun parseDoc(text: String, type: String): DocRes {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val key = Regex("(?i)(berlaku|masa|hingga|sampai|s/d|exp|valid|pajak)")
        val cands = mutableListOf<LocalDate>()
        lines.forEachIndexed { i, l ->
            if (key.containsMatchIn(l) || (i > 0 && key.containsMatchIn(lines[i - 1]))) cands.addAll(dates(l))
        }
        val expiry = cands.maxOrNull() ?: lines.flatMap { dates(it) }.maxOrNull()

        var owner: String? = null
        val nameRe = Regex("(?i)nama\\s*(?:pemilik)?\\s*[:\\-]?\\s*(.*)")
        for ((i, l) in lines.withIndex()) {
            val m = nameRe.find(l) ?: continue
            var v = m.groupValues[1]
            if (v.isBlank() && i + 1 < lines.size) v = lines[i + 1]
            v = v.filter { it.isLetter() || it == ' ' || it == '.' }.trim()
            if (v.length >= 3) { owner = v.uppercase(); break }
        }

        val upper = text.uppercase()
        var number: String? = null
        var name: String? = null
        if (type == "SIM") {
            number = Regex("""\b(\d{4})[\s-]?(\d{4})[\s-]?(\d{4})\b""").find(text)?.groupValues?.drop(1)?.joinToString("")
                ?: Regex("""\b\d{12,16}\b""").find(text)?.value
            Regex("(?i)\\bSIM\\s*(B1|B2|BI|BII|A|B|C|D)\\b").find(text)?.let { name = "SIM " + it.groupValues[1].uppercase() }
        } else {
            number = Regex("""\b([A-Z]{1,2})\s?(\d{1,4})\s?([A-Z]{1,3})\b""").find(upper)?.let {
                "${it.groupValues[1]} ${it.groupValues[2]} ${it.groupValues[3]}"
            }
        }
        return DocRes(name, number, expiry, owner)
    }
}
