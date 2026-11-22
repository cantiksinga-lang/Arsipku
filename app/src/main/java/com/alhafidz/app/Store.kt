package com.alhafidz.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Member(val id: Long, val name: String, val avatar: String?)

data class Doc(
    val id: Long, val title: String, val cat: Int, val member: Long,
    val num: String, val notes: String, val expiry: Long,
    val file: String, val mime: String, val created: Long
) {
    val isPdf: Boolean get() = mime == "application/pdf"
    val lifetime: Boolean get() = expiry < 0
}

private class DbH(c: Context) : SQLiteOpenHelper(c, "alh.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE members(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, avatar TEXT)")
        db.execSQL(
            "CREATE TABLE docs(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT NOT NULL, cat INTEGER NOT NULL, " +
                "member INTEGER NOT NULL DEFAULT 0, num TEXT, notes TEXT, expiry INTEGER NOT NULL, " +
                "file TEXT NOT NULL, mime TEXT NOT NULL, created INTEGER NOT NULL)"
        )
    }
    override fun onUpgrade(db: SQLiteDatabase, o: Int, n: Int) {}
}

/** Judul, nomor, catatan, dan nama anggota disimpan TERENKRIPSI (AES-GCM) di dalam database. */
object Store {
    private var h: DbH? = null

    @Synchronized
    private fun db(c: Context): SQLiteDatabase {
        if (h == null) h = DbH(c.applicationContext)
        return h!!.writableDatabase
    }

    private fun mvals(c: Context, m: Member, withId: Boolean): ContentValues = ContentValues().apply {
        if (withId) put("id", m.id)
        put("name", Crypto.encStr(c, m.name))
        put("avatar", m.avatar)
    }

    private fun dvals(c: Context, d: Doc, withId: Boolean): ContentValues = ContentValues().apply {
        if (withId) put("id", d.id)
        put("title", Crypto.encStr(c, d.title))
        put("cat", d.cat)
        put("member", d.member)
        put("num", Crypto.encStr(c, d.num))
        put("notes", Crypto.encStr(c, d.notes))
        put("expiry", d.expiry)
        put("file", d.file)
        put("mime", d.mime)
        put("created", d.created)
    }

    fun members(c: Context): List<Member> {
        val out = ArrayList<Member>()
        db(c).query("members", null, null, null, null, null, "id").use { q ->
            while (q.moveToNext()) out.add(Member(q.getLong(0), Crypto.decStr(c, q.getString(1)), q.getString(2)))
        }
        return out
    }

    fun saveMember(c: Context, m: Member): Long {
        return if (m.id == 0L) db(c).insert("members", null, mvals(c, m, false))
        else { db(c).update("members", mvals(c, m, false), "id=?", arrayOf(m.id.toString())); m.id }
    }

    fun deleteMember(c: Context, m: Member) {
        m.avatar?.let { Vault.delete(c, it) }
        val d = db(c)
        d.execSQL("UPDATE docs SET member=0 WHERE member=" + m.id)
        d.delete("members", "id=?", arrayOf(m.id.toString()))
    }

    private fun readDoc(c: Context, q: android.database.Cursor) = Doc(
        q.getLong(0), Crypto.decStr(c, q.getString(1)), q.getInt(2), q.getLong(3),
        Crypto.decStr(c, q.getString(4)), Crypto.decStr(c, q.getString(5)),
        q.getLong(6), q.getString(7), q.getString(8), q.getLong(9)
    )

    fun docs(c: Context): List<Doc> {
        val out = ArrayList<Doc>()
        db(c).query("docs", null, null, null, null, null, null).use { q ->
            while (q.moveToNext()) out.add(readDoc(c, q))
        }
        return out.sortedBy { if (it.lifetime) Long.MAX_VALUE else it.expiry }
    }

    fun doc(c: Context, id: Long): Doc? {
        db(c).query("docs", null, "id=?", arrayOf(id.toString()), null, null, null).use { q ->
            return if (q.moveToFirst()) readDoc(c, q) else null
        }
    }

    fun saveDoc(c: Context, d: Doc): Long {
        val id = if (d.id == 0L) db(c).insert("docs", null, dvals(c, d, false))
        else { db(c).update("docs", dvals(c, d, false), "id=?", arrayOf(d.id.toString())); d.id }
        ExpiryWidget.refresh(c)
        return id
    }

    fun deleteDoc(c: Context, d: Doc) {
        db(c).delete("docs", "id=?", arrayOf(d.id.toString()))
        Vault.delete(c, d.file)
        ExpiryWidget.refresh(c)
    }

    fun replaceAll(c: Context, ms: List<Member>, ds: List<Doc>) {
        val d = db(c)
        d.beginTransaction()
        try {
            d.delete("docs", null, null)
            d.delete("members", null, null)
            ms.forEach { d.insert("members", null, mvals(c, it, true)) }
            ds.forEach { d.insert("docs", null, dvals(c, it, true)) }
            d.setTransactionSuccessful()
        } finally {
            d.endTransaction()
        }
        ExpiryWidget.refresh(c)
    }

    fun wipe(c: Context) {
        val d = db(c)
        d.delete("docs", null, null)
        d.delete("members", null, null)
        Vault.dir(c).listFiles()?.forEach { it.delete() }
        Vault.clearTemp(c)
        ExpiryWidget.refresh(c)
    }
}
