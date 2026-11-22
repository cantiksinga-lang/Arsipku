package com.alhafidz.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews

/** Widget layar utama: hanya info dokumen dengan masa berlaku paling dekat (tanpa preview dokumen). */
class ExpiryWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val rv = build(context)
        for (id in appWidgetIds) appWidgetManager.updateAppWidget(id, rv)
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_DATE_CHANGED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED -> refresh(context)
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        private val RED = 0xFFE5484D.toInt()
        private val BLUE = 0xFF2F6BF0.toInt()
        private val GREEN = 0xFF2DB551.toInt()
        private val TXT = intArrayOf(0xFFD32F2F.toInt(), 0xFF1E5AD6.toInt(), 0xFF1B7F3B.toInt())

        fun refresh(c: Context) {
            try {
                val app = c.applicationContext
                val m = AppWidgetManager.getInstance(app)
                val ids = m.getAppWidgetIds(ComponentName(app, ExpiryWidget::class.java))
                if (ids.isEmpty()) return
                val rv = build(app)
                for (id in ids) m.updateAppWidget(id, rv)
            } catch (t: Throwable) { }
        }

        private fun ring(size: Int, progress: Float, color: Int, num: String, unit: String): Bitmap {
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val cv = Canvas(b)
            val s = size * 0.11f
            val r = RectF(s / 2, s / 2, size - s / 2, size - s / 2)
            val p = Paint(Paint.ANTI_ALIAS_FLAG)
            p.style = Paint.Style.STROKE
            p.strokeWidth = s
            p.strokeCap = Paint.Cap.ROUND
            p.color = (color and 0x00FFFFFF) or 0x33000000
            cv.drawArc(r, 0f, 360f, false, p)
            p.color = color
            cv.drawArc(r, -90f, 360f * progress.coerceIn(0.03f, 1f), false, p)
            val t = Paint(Paint.ANTI_ALIAS_FLAG)
            t.textAlign = Paint.Align.CENTER
            t.color = color
            t.typeface = Typeface.DEFAULT_BOLD
            if (unit.isEmpty()) {
                t.textSize = size * 0.40f
                cv.drawText(num, size / 2f, size / 2f + t.textSize * 0.35f, t)
            } else {
                t.textSize = if (num.length > 2) size * 0.26f else size * 0.34f
                cv.drawText(num, size / 2f, size * 0.50f, t)
                t.typeface = Typeface.DEFAULT
                t.textSize = size * 0.15f
                cv.drawText(unit, size / 2f, size * 0.50f + size * 0.17f, t)
            }
            return b
        }

        private fun build(c: Context): RemoteViews {
            val rv = RemoteViews(c.packageName, R.layout.widget_expiry)
            val open = PendingIntent.getActivity(
                c, 3,
                Intent(c, MainActivity::class.java).putExtra("tab", 2)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.w_root, open)

            val docs = try { Store.docs(c) } catch (t: Throwable) { emptyList() }
            val hide = Prefs.bool(c, K.WHIDE, false)
            val d = docs.firstOrNull { !it.lifetime }
            val size = (84 * c.resources.displayMetrics.density).toInt()

            val level: Int
            val title: String
            val status: String
            val msg: String
            val bmp: Bitmap
            if (d == null) {
                level = 2
                title = if (docs.isEmpty()) "Belum ada dokumen" else "Semua dokumen aman"
                status = if (docs.isEmpty()) "ArsipKu" else "Berlaku seumur hidup"
                msg = if (docs.isEmpty()) "Ketuk untuk membuka dan menambah dokumen pertama." else "Tidak ada masa berlaku yang perlu diperpanjang."
                bmp = ring(size, 1f, GREEN, if (docs.isEmpty()) "0" else "\u2713", "")
            } else {
                val st = statusOf(c, d)
                level = st.level
                title = if (hide) "Dokumen" else d.title
                val colors = intArrayOf(RED, BLUE, GREEN)
                val unitTxt = if (st.days < 0) "habis" else if (st.unit == "") "" else st.unit + " lagi"
                bmp = ring(size, st.progress, colors[level], st.num, unitTxt)
                status = when {
                    st.days < 0 -> "Masa berlaku sudah habis"
                    st.days == 0L -> "Habis hari ini"
                    else -> "Masa berlaku tinggal ${Fmt.human(st.days)} lagi"
                }
                msg = when {
                    st.days < 0 -> "Segera perpanjang. Habis sejak " + Fmt.full(d.expiry)
                    level == 0 -> "Segera perpanjang sebelum " + Fmt.full(d.expiry)
                    else -> "Berlaku sampai " + Fmt.full(d.expiry)
                }
            }
            rv.setImageViewBitmap(R.id.w_ring, bmp)
            rv.setTextViewText(R.id.w_title, title)
            rv.setTextViewText(R.id.w_status, status)
            rv.setTextColor(R.id.w_status, TXT[level])
            rv.setTextViewText(R.id.w_msg, msg)
            val bgs = intArrayOf(R.drawable.widget_bg_0, R.drawable.widget_bg_1, R.drawable.widget_bg_2)
            rv.setInt(R.id.w_root, "setBackgroundResource", bgs[level])
            return rv
        }
    }
}
