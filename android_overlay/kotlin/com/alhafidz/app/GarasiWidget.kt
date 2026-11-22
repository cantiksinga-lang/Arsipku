package com.alhafidz.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import org.json.JSONObject
import java.util.Locale

class GarasiWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, build(context, id))
        mgr.notifyAppWidgetViewDataChanged(ids, R.id.stack)
    }

    companion object {
        fun build(context: Context, id: Int): RemoteViews {
            val rv = RemoteViews(context.packageName, R.layout.widget_garasiku)
            val svc = Intent(context, GarasiWidgetService::class.java)
            svc.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            svc.data = Uri.parse(svc.toUri(Intent.URI_INTENT_SCHEME))
            rv.setRemoteAdapter(R.id.stack, svc)
            rv.setEmptyView(R.id.stack, R.id.empty)

            val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pi = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), flags)
            rv.setPendingIntentTemplate(R.id.stack, pi)
            rv.setOnClickPendingIntent(R.id.empty, pi)
            return rv
        }

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, GarasiWidgetProvider::class.java))
            if (ids.isEmpty()) return
            for (id in ids) mgr.updateAppWidget(id, build(context, id))
            mgr.notifyAppWidgetViewDataChanged(ids, R.id.stack)
        }
    }
}

class GarasiWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = Factory(applicationContext)

    class Factory(private val ctx: Context) : RemoteViewsFactory {
        private var items: List<JSONObject> = emptyList()
        private var oilWarn = 500L
        private var serviceWarn = 500L
        private var taxWarn = 14L

        private val good = Color.parseColor("#1E9E55")
        private val warn = Color.parseColor("#E08A00")
        private val bad = Color.parseColor("#D93636")
        private val gray = Color.parseColor("#5B6B80")

        override fun onCreate() {}
        override fun onDestroy() {}

        override fun onDataSetChanged() {
            items = Snap.widgetVehicles(ctx)
            val n = Snap.root(ctx).optJSONObject("notif")
            if (n != null) {
                oilWarn = n.optLong("oilWarnKm", 500)
                serviceWarn = n.optLong("serviceWarnKm", 500)
                taxWarn = n.optLong("taxWarnDays", 14)
            }
        }

        private fun level(left: Long, w: Long) = if (left <= 0) bad else if (left <= w) warn else good

        private fun line(rv: RemoteViews, id: Int, text: String, color: Int) {
            rv.setTextViewText(id, text)
            rv.setTextColor(id, color)
        }

        override fun getViewAt(position: Int): RemoteViews {
            val rv = RemoteViews(ctx.packageName, R.layout.widget_item)
            if (position < 0 || position >= items.size) return rv
            val v = items[position]

            rv.setTextViewText(R.id.w_name, v.optString("name"))
            rv.setTextViewText(R.id.w_page, "${position + 1}/${items.size}")
            rv.setViewVisibility(R.id.w_page, if (items.size > 1) android.view.View.VISIBLE else android.view.View.GONE)
            val plate = v.optString("plate")
            rv.setTextViewText(
                R.id.w_sub,
                (if (plate.isNotEmpty()) "$plate · " else "") + Snap.fmt(v.optLong("odo")) + " km"
            )

            if (Snap.has(v, "oilLeft")) {
                val left = v.optLong("oilLeft")
                line(
                    rv, R.id.w_oil,
                    if (left <= 0) "Oli: GANTI SEKARANG (lewat ${Snap.fmt(-left)} km)" else "Oli: ${Snap.fmt(left)} km lagi",
                    level(left, oilWarn)
                )
            } else line(rv, R.id.w_oil, "Oli: belum diatur", gray)

            if (Snap.has(v, "serviceLeft")) {
                val left = v.optLong("serviceLeft")
                line(
                    rv, R.id.w_service,
                    if (left <= 0) "Servis: waktunya servis (lewat ${Snap.fmt(-left)} km)" else "Servis: ${Snap.fmt(left)} km lagi",
                    level(left, serviceWarn)
                )
            } else line(rv, R.id.w_service, "Servis: belum diatur", gray)

            var bbm = "BBM bulan ini: Rp " + Snap.fmt(v.optLong("fuelMonth"))
            if (Snap.has(v, "fuelEff")) {
                bbm += " · " + String.format(Locale("id", "ID"), "%.1f", v.optDouble("fuelEff")) + " km/L"
            }
            line(rv, R.id.w_bbm, bbm, Color.parseColor("#0E7FC0"))

            if (Snap.has(v, "taxDays")) {
                val d = v.optLong("taxDays")
                line(
                    rv, R.id.w_tax,
                    if (d < 0) "Pajak: lewat ${-d} hari" else if (d == 0L) "Pajak: jatuh tempo hari ini" else "Pajak: $d hari lagi",
                    level(d, taxWarn)
                )
            } else line(rv, R.id.w_tax, "Pajak: belum diatur", gray)

            rv.setOnClickFillInIntent(R.id.item_root, Intent())
            return rv
        }

        override fun getLoadingView(): RemoteViews? = null
        override fun getViewTypeCount() = 1
        override fun getItemId(position: Int) = position.toLong()
        override fun hasStableIds() = false
        override fun getCount() = items.size
    }
}
