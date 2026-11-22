package com.alhafidz.app.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.Button
import androidx.glance.text.Text
import androidx.compose.ui.unit.dp
import com.alhafidz.app.data.GarageDb

class VehicleWidget:GlanceAppWidget(){
 override suspend fun provideGlance(context:Context,id:GlanceId){
  val vehicles=GarageDb.get(context).dao().allVehicles()
  val prefs=context.getSharedPreferences("widget",Context.MODE_PRIVATE)
  val index=if(vehicles.isEmpty())0 else prefs.getInt("index",0).coerceAtLeast(0)%vehicles.size
  val v=vehicles.getOrNull(index)
  provideContent { Column(GlanceModifier.fillMaxSize().padding(12.dp)) {
   Text("GarasiKu · ${if(vehicles.isEmpty())0 else index+1}/${vehicles.size}")
   if(v==null)Text("Belum ada kendaraan. Tambahkan melalui aplikasi.") else {
    Text(v.name);Text(v.plate);Text("Odometer ${v.currentKm} km")
    Text("Oli berikut ${v.lastOilKm+v.oilInterval} km · ${(v.lastOilKm+v.oilInterval-v.currentKm).coerceAtLeast(0)} km lagi")
    Text("Servis berikut ${v.lastServiceKm+v.serviceInterval} km · ${(v.lastServiceKm+v.serviceInterval-v.currentKm).coerceAtLeast(0)} km lagi")
    Row { Button(text="‹",onClick=actionRunCallback<PreviousVehicle>());Button(text="›",onClick=actionRunCallback<NextVehicle>()) }
   }
  } }
 }
}
class NextVehicle:ActionCallback{override suspend fun onAction(context:Context,glanceId:GlanceId,parameters:ActionParameters){val list=GarageDb.get(context).dao().allVehicles();if(list.isNotEmpty()){val p=context.getSharedPreferences("widget",0);p.edit().putInt("index",(p.getInt("index",0)+1)%list.size).apply()};VehicleWidget().update(context,glanceId)} }
class PreviousVehicle:ActionCallback{override suspend fun onAction(context:Context,glanceId:GlanceId,parameters:ActionParameters){val list=GarageDb.get(context).dao().allVehicles();if(list.isNotEmpty()){val p=context.getSharedPreferences("widget",0);p.edit().putInt("index",(p.getInt("index",0)-1+list.size)%list.size).apply()};VehicleWidget().update(context,glanceId)} }
class VehicleWidgetReceiver:GlanceAppWidgetReceiver(){override val glanceAppWidget=VehicleWidget()}
