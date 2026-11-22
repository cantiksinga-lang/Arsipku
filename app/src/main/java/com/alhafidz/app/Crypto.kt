package com.alhafidz.app

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.PushbackInputStream
import java.security.GeneralSecurityException
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

const val CHUNK = 64 * 1024

/**
 * Kunci utama (AES-256) acak -> dibungkus oleh kunci Android Keystore (hardware-backed bila ada).
 * Semua berkas dokumen, thumbnail, dan metadata dienkripsi dengan kunci ini (AES-GCM).
 */
object Crypto {
    private const val ALIAS = "alhafidz_wrap_v1"
    private val rnd = SecureRandom()
    @Volatile private var mk: SecretKey? = null

    fun random(n: Int): ByteArray = ByteArray(n).also { rnd.nextBytes(it) }
    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    fun iv(prefix: ByteArray, ctr: Int): ByteArray {
        val iv = ByteArray(12)
        System.arraycopy(prefix, 0, iv, 0, 8)
        iv[8] = (ctr ushr 24).toByte(); iv[9] = (ctr ushr 16).toByte()
        iv[10] = (ctr ushr 8).toByte(); iv[11] = ctr.toByte()
        return iv
    }

    @Synchronized
    fun master(c: Context): SecretKey {
        mk?.let { return it }
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        if (!ks.containsAlias(ALIAS)) {
            val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            kg.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            kg.generateKey()
        }
        val wrap = ks.getKey(ALIAS, null) as SecretKey
        val sp = Prefs.sp(c)
        val stored = sp.getString(K.MK, null)
        val key: SecretKey
        if (stored == null) {
            val raw = random(32)
            val cph = Cipher.getInstance("AES/GCM/NoPadding")
            cph.init(Cipher.ENCRYPT_MODE, wrap)
            val ct = cph.doFinal(raw)
            sp.edit().putString(K.MK, b64(cph.iv) + ":" + b64(ct)).commit()
            key = SecretKeySpec(raw, "AES")
        } else {
            val parts = stored.split(":")
            val cph = Cipher.getInstance("AES/GCM/NoPadding")
            cph.init(Cipher.DECRYPT_MODE, wrap, GCMParameterSpec(128, unb64(parts[0])))
            key = SecretKeySpec(cph.doFinal(unb64(parts[1])), "AES")
        }
        mk = key
        return key
    }

    fun pbkdf(pw: CharArray, salt: ByteArray, iter: Int): ByteArray {
        val spec = PBEKeySpec(pw, salt, iter, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    fun hashPin(pin: String, salt: ByteArray): ByteArray = pbkdf(pin.toCharArray(), salt, 120_000)

    fun encStr(c: Context, s: String): String {
        val iv = random(12)
        val cph = Cipher.getInstance("AES/GCM/NoPadding")
        cph.init(Cipher.ENCRYPT_MODE, master(c), GCMParameterSpec(128, iv))
        return b64(iv + cph.doFinal(s.toByteArray()))
    }

    fun decStr(c: Context, s: String?): String {
        if (s.isNullOrEmpty()) return ""
        return try {
            val all = unb64(s)
            val cph = Cipher.getInstance("AES/GCM/NoPadding")
            cph.init(Cipher.DECRYPT_MODE, master(c), GCMParameterSpec(128, all, 0, 12))
            String(cph.doFinal(all, 12, all.size - 12))
        } catch (e: Exception) {
            ""
        }
    }
}

/** Enkripsi streaming berantai: blok 64 KB, AES-256-GCM, penanda blok terakhir di AAD (anti pemotongan). */
class EncOut(private val o: OutputStream, private val key: SecretKey) : OutputStream() {
    private val prefix = Crypto.random(8)
    private val buf = ByteArray(CHUNK)
    private var n = 0
    private var ctr = 0
    private var done = false

    init { o.write(prefix) }

    override fun write(b: Int) { write(byteArrayOf(b.toByte()), 0, 1) }

    override fun write(b: ByteArray, off: Int, len: Int) {
        var p = off
        var l = len
        while (l > 0) {
            if (n == CHUNK) flushChunk(false)
            val c = minOf(l, CHUNK - n)
            System.arraycopy(b, p, buf, n, c)
            n += c; p += c; l -= c
        }
    }

    private fun flushChunk(last: Boolean) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, Crypto.iv(prefix, ctr)))
        c.updateAAD(byteArrayOf(if (last) 1 else 0))
        o.write(c.doFinal(buf, 0, n))
        n = 0
        ctr++
    }

    override fun flush() { o.flush() }

    override fun close() {
        if (done) return
        done = true
        flushChunk(true)
        o.flush()
        o.close()
    }
}

class DecIn(src: InputStream, private val key: SecretKey) : InputStream() {
    private val inp = PushbackInputStream(src, 1)
    private val prefix = ByteArray(8)
    private var ctr = 0
    private var buf = ByteArray(0)
    private var pos = 0
    private var done = false

    init { if (readFull(prefix) < 8) throw IOException("DECRYPT_FAIL") }

    private fun readFull(b: ByteArray): Int {
        var g = 0
        while (g < b.size) {
            val r = inp.read(b, g, b.size - g)
            if (r < 0) break
            g += r
        }
        return g
    }

    private fun fill(): Boolean {
        if (done) return false
        val blk = ByteArray(CHUNK + 16)
        val g = readFull(blk)
        if (g < 16) throw IOException("DECRYPT_FAIL")
        var last = g < blk.size
        if (!last) {
            val x = inp.read()
            if (x < 0) last = true else inp.unread(x)
        }
        try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, Crypto.iv(prefix, ctr)))
            c.updateAAD(byteArrayOf(if (last) 1 else 0))
            buf = c.doFinal(blk, 0, g)
        } catch (e: GeneralSecurityException) {
            throw IOException("DECRYPT_FAIL", e)
        }
        pos = 0
        ctr++
        if (last) done = true
        return true
    }

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        while (pos >= buf.size) {
            if (!fill()) return -1
        }
        val c = minOf(len, buf.size - pos)
        System.arraycopy(buf, pos, b, off, c)
        pos += c
        return c
    }

    override fun close() { inp.close() }
}
