package com.alhafidz.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {
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
                    "checkNow" -> {
                        Reminder.check(this, true)
                        result.success(true)
                    }
                    else -> result.notImplemented()
                }
            }
    }
}
