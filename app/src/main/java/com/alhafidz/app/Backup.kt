package com.alhafidz.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.crypto.spec.SecretKeySpec

/**
 * Format backup (.alhbak): "ALHBAK01" + salt(16) + [AES-256-GCM berantai, kunci dari kata sandi via PBKDF2-SHA256 200k]
 * isi: ZIP berisi meta.json + berkas dokumen. Tanpa kata sandi, berkas tidak bisa dibuka.
 */
object Backup {
    private val MAGIC = "ALHBAK01".toByteArray()
    private const val ITER = 200_000

    class WrongPassword : IOException("Kata sandi salah atau berkas rusak")

    fun export(c: Context, out: OutputStream, pw: CharArray) {
        val salt = Crypto.random(16)
        out.write(MAGIC)
        out.write(salt)
        val key = SecretKeySpec(Crypto.pbkdf(pw, salt, ITER), "AES")
        val z = ZipOutputStream(BufferedOutputStream(EncOut(out, key), 65536))
        z.setLevel(1)
        val members = Store.members(c)
        val docs = Store.docs(c)

        val ma = JSONArray()
        members.forEach { ma.put(JSONObject().put("id", it.id).put("name", it.name).put("avatar", it.avatar ?: "")) }
        val da = JSONArray()
        docs.forEach {
            da.put(
                JSONObject().put("id", it.id).put("title", it.title).put("cat", it.cat).put("member", it.member)
                    .put("num", it.num).put("notes", it.notes).put("expiry", it.expiry)
                    .put("file", it.file).put("mime", it.mime).put("created", it.created)
            )
        }
        val meta = JSONObject().put("v", 1).put("t", System.currentTimeMillis()).put("members", ma).put("docs", da)

        z.putNextEntry(ZipEntry("meta.json"))
        z.write(meta.toString().toByteArray())
        z.closeEntry()
        for (d in docs) {
            if (!Vault.enc(c, d.file).exists()) continue
            z.putNextEntry(ZipEntry("f/" + d.file))
            Vault.open(c, d.file).use { it.copyTo(z) }
            z.closeEntry()
        }
        for (m in members) {
            val a = m.avatar ?: continue
            if (!Vault.enc(c, a).exists()) continue
            z.putNextEntry(ZipEntry("a/$a"))
            Vault.open(c, a).use { it.copyTo(z) }
            z.closeEntry()
        }
        z.finish()
        z.close()
    }

    /** Mengganti SELURUH data dengan isi backup. Return jumlah dokumen. */
    fun restore(c: Context, inp: InputStream, pw: CharArray): Int {
        val head = ByteArray(MAGIC.size + 16)
        var g = 0
        while (g < head.size) {
            val r = inp.read(head, g, head.size - g)
            if (r < 0) break
            g += r
        }
        if (g < head.size || !head.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw IOException("Berkas ini bukan backup Alhafidz")
        }
        val salt = head.copyOfRange(MAGIC.size, head.size)
        val key = SecretKeySpec(Crypto.pbkdf(pw, salt, ITER), "AES")

        val (z, meta) = try {
            val zz = ZipInputStream(BufferedInputStream(DecIn(inp, key)))
            val e = zz.nextEntry
            if (e == null || e.name != "meta.json") throw IOException()
            zz to JSONObject(String(zz.readBytes()))
        } catch (e: Exception) {
            throw WrongPassword()
        }

        val created = ArrayList<String>()
        val map = HashMap<String, String>()
        try {
            while (true) {
                val e = z.nextEntry ?: break
                val n = e.name
                if (!(n.startsWith("f/") || n.startsWith("a/"))) continue
                val old = n.substring(2)
                if (old.isEmpty() || old.contains("/") || old.contains("..")) continue
                val nn = Vault.newName()
                created.add(nn)
                Vault.output(c, Vault.enc(c, nn)).use { z.copyTo(it) }
                map[old] = nn
            }

            val ms = ArrayList<Member>()
            val ja = meta.getJSONArray("members")
            for (i in 0 until ja.length()) {
                val o = ja.getJSONObject(i)
                val av = o.optString("avatar", "")
                ms.add(Member(o.getLong("id"), o.getString("name"), if (av.isEmpty()) null else map[av]))
            }
            val ds = ArrayList<Doc>()
            val jd = meta.getJSONArray("docs")
            for (i in 0 until jd.length()) {
                val o = jd.getJSONObject(i)
                val nf = map[o.getString("file")] ?: continue
                val mime = o.getString("mime")
                ds.add(
                    Doc(
                        o.getLong("id"), o.getString("title"), o.getInt("cat"), o.getLong("member"),
                        o.optString("num", ""), o.optString("notes", ""), o.getLong("expiry"),
                        nf, mime, o.getLong("created")
                    )
                )
                try { Vault.makeThumb(c, nf, mime == "application/pdf") } catch (ex: Exception) { }
            }
            val oldFiles = Store.docs(c).map { it.file } + Store.members(c).mapNotNull { it.avatar }
            Store.replaceAll(c, ms, ds)
            oldFiles.forEach { Vault.delete(c, it) }
            return ds.size
        } catch (e: Exception) {
            created.forEach { Vault.delete(c, it) }
            throw e
        }
    }
}
