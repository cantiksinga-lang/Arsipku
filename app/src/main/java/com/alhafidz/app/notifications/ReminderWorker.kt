package com.alhafidz.app.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.*
import com.alhafidz.app.data.GarageDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ReminderWorker(ctx:Context,params:WorkerParameters):CoroutineWorker(ctx,params){
 override suspend fun doWork():Result=withContext(Dispatchers.IO){
  val vehicles=GarageDb.get(applicationContext).dao().allVehicles()
  if(Build.VERSION.SDK_INT>=26){val nm=applicationContext.getSystemService(NotificationManager::class.java);nm.createNotificationChannel(NotificationChannel("garage_reminders","Pengingat GarasiKu",NotificationManager.IMPORTANCE_DEFAULT).apply{description="Pengingat oli dan servis kendaraan"})}
  vehicles.forEach { v ->
   val oilDue=v.lastOilKm+v.oilInterval; val serviceDue=v.lastServiceKm+v.serviceInterval
   val oilLeft=oilDue-v.currentKm; val serviceLeft=serviceDue-v.currentKm
   if(oilLeft<=500){notify("oil_${v.id}","${v.name}: ${if(oilLeft<=0)"Ganti oli sudah waktunya" else "Ganti oli $oilLeft km lagi"}","Odometer ${v.currentKm} km · target $oilDue km",(v.id*2).toInt())}
   if(serviceLeft<=500){notify("service_${v.id}","${v.name}: ${if(serviceLeft<=0)"Servis sudah waktunya" else "Servis $serviceLeft km lagi"}","Odometer ${v.currentKm} km · target $serviceDue km",(v.id*2+1).toInt())}
  }
  Result.success()
 }
 private fun notify(title:String,text:String,tag:String,id:Int){
  if(Build.VERSION.SDK_INT>=33&&applicationContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return
  val n=NotificationCompat.Builder(applicationContext,"garage_reminders").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text)).setPriority(NotificationCompat.PRIORITY_DEFAULT).setAutoCancel(true).build()
  NotificationManagerCompat.from(applicationContext).notify(tag,id,n)
 }
 companion object { fun schedule(context:Context){WorkManager.getInstance(context).enqueueUniquePeriodicWork("GarasiKuReminder",ExistingPeriodicWorkPolicy.UPDATE,PeriodicWorkRequestBuilder<ReminderWorker>(12,TimeUnit.HOURS).build())} }
}
