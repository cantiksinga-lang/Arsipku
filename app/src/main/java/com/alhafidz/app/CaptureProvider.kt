package com.alhafidz.app

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import java.io.File

/** Provider kecil (tanpa library) agar aplikasi kamera bisa menyimpan foto sementara tanpa izin CAMERA/storage. */
class CaptureProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun file(u: Uri): File {
        val dir = File(context!!.cacheDir, "cap").also { it.mkdirs() }
        return File(dir, (u.lastPathSegment ?: "capture.jpg").replace("/", "_"))
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? =
        ParcelFileDescriptor.open(file(uri), ParcelFileDescriptor.parseMode(mode) or ParcelFileDescriptor.MODE_CREATE)

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?
    ): Cursor? {
        val f = file(uri)
        val c = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        c.addRow(arrayOf<Any>(f.name, f.length()))
        return c
    }

    override fun getType(uri: Uri): String? = "image/jpeg"
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
