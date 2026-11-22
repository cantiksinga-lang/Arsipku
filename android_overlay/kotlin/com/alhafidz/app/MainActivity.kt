package com.alhafidz.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
    private var pending: MethodChannel.Result? = null
    private var pendingContent: String? = null

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 501 && requestCode != 502) return
        val r = pending
        pending = null
        val uri = data?.data
        if (r == null) return
        if (resultCode != Activity.RESULT_OK || uri == null) {
            r.success(if (requestCode == 501) false else null)
            return
        }
        try {
            if (requestCode == 501) {
                contentResolver.openOutputStream(uri, "wt")?.use {
                    it.write((pendingContent ?: "").toByteArray(Charsets.UTF_8))
                }
                pendingContent = null
                r.success(true)
            } else {
                val text = contentResolver.openInputStream(uri)?.use { String(it.readBytes(), Charsets.UTF_8) }
                r.success(text)
            }
        } catch (e: Exception) {
            r.success(if (requestCode == 501) false else null)
        }
    }

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, "garasiku/native")
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "saveSnapshot" -> {
                        Snap.save(this, call.arguments as String)
                        GarasiWidgetProvider.refreshAll(this)
                        Reminder.schedule(this)
                        Reminder.check(this)
                        result.success(true)
                    }
                    "requestNotifPermission" -> {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                        ) {
                            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 77)
                        }
                        result.success(true)
                    }
                    "pinWidget" -> {
                        val mgr = AppWidgetManager.getInstance(this)
                        if (Build.VERSION.SDK_INT >= 26 && mgr.isRequestPinAppWidgetSupported) {
                            mgr.requestPinAppWidget(ComponentName(this, GarasiWidgetProvider::class.java), null, null)
                            result.success(true)
                        } else {
                            result.success(false)
                        }
                    }
                    "backupFile" -> {
                        pending = result
                        pendingContent = call.argument<String>("content")
                        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
                        i.addCategory(Intent.CATEGORY_OPENABLE)
                        i.type = "application/json"
                        i.putExtra(Intent.EXTRA_TITLE, call.argument<String>("name") ?: "GarasiKu.json")
                        @Suppress("DEPRECATION")
                        startActivityForResult(i, 501)
                    }
                    "restoreFile" -> {
                        pending = result
                        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        i.addCategory(Intent.CATEGORY_OPENABLE)
                        i.type = "*/*"
                        @Suppress("DEPRECATION")
                        startActivityForResult(i, 502)
                    }
                    "checkNow" -> {
                        Reminder.check(this, true)
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}
