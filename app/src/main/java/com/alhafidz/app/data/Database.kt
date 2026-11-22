package com.alhafidz.app.data

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName="vehicles")
data class Vehicle(
 @PrimaryKey(autoGenerate=true) val id:Long=0,
 val name:String,
 val plate:String,
 val type:String="Motor",
 val photo:String="",
 val currentKm:Long=0,
 val oilInterval:Long=2500,
 @ColumnInfo(defaultValue="0") val lastOilKm:Long=0,
 @ColumnInfo(defaultValue="5000") val serviceInterval:Long=5000,
 @ColumnInfo(defaultValue="0") val lastServiceKm:Long=0,
 val taxDate:String="",
 val notes:String=""
)
@Entity(tableName="entries")
data class EntryRecord(
 @PrimaryKey(autoGenerate=true) val id:Long=0,
 val vehicleId:Long,
 val date:String,
 val category:String,
 val itemName:String,
 val amount:Long,
 val km:Long=0,
 val liters:Double=0.0,
 val station:String="",
 val note:String="",
 @ColumnInfo(defaultValue="''") val receiptPhoto:String=""
)
@Dao interface GarageDao {
 @Query("SELECT * FROM vehicles ORDER BY id DESC") fun vehicles():Flow<List<Vehicle>>
 @Query("SELECT * FROM vehicles ORDER BY id DESC") suspend fun allVehicles():List<Vehicle>
 @Insert suspend fun addVehicle(v:Vehicle):Long
 @Update suspend fun updateVehicle(v:Vehicle)
 @Delete suspend fun deleteVehicle(v:Vehicle)
 @Query("SELECT * FROM entries ORDER BY date DESC,id DESC") fun entries():Flow<List<EntryRecord>>
 @Query("SELECT * FROM entries ORDER BY date DESC,id DESC") suspend fun allEntries():List<EntryRecord>
 @Insert suspend fun addEntry(e:EntryRecord):Long
 @Update suspend fun updateEntry(e:EntryRecord)
 @Delete suspend fun deleteEntry(e:EntryRecord)
 @Query("DELETE FROM entries") suspend fun clearEntries()
 @Query("DELETE FROM vehicles") suspend fun clearVehicles()
}
@Database(entities=[Vehicle::class,EntryRecord::class],version=2,exportSchema=false)
abstract class GarageDb:RoomDatabase(){ abstract fun dao():GarageDao
 companion object { val MIGRATION_1_2=object:Migration(1,2){override fun migrate(db:SupportSQLiteDatabase){db.execSQL("ALTER TABLE vehicles ADD COLUMN lastOilKm INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE vehicles ADD COLUMN serviceInterval INTEGER NOT NULL DEFAULT 5000");db.execSQL("ALTER TABLE vehicles ADD COLUMN lastServiceKm INTEGER NOT NULL DEFAULT 0");db.execSQL("ALTER TABLE entries ADD COLUMN receiptPhoto TEXT NOT NULL DEFAULT ''")}}; @Volatile private var instance:GarageDb?=null; fun get(ctx:Context)=instance?: synchronized(this){instance?:Room.databaseBuilder(ctx.applicationContext,GarageDb::class.java,"garasiku.db").addMigrations(MIGRATION_1_2).build().also{instance=it}} }
}
