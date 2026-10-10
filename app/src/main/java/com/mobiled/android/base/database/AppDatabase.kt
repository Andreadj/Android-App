package com.mobiled.android.base.database

import android.content.Context
import com.mobiled.android.LogSystem
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobiled.android.base.AppConfiguration
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import java.util.concurrent.Executors

@Database(entities=[HardwareDevice::class,HardwareGroupItem::class,HardwareGroup::class],version=2,exportSchema=true,autoMigrations=[])
abstract class AppDatabase:RoomDatabase(){
 abstract fun getHardwareTable():HardwareTableDAO
 abstract fun getGroupTable():GroupTableDAO
 companion object{@Volatile private var INSTANCE:AppDatabase?=null
  private val MIGRATION_1_2 = object : Migration(1,2) { override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE HardwareGroupItem ADD COLUMN PixelID INTEGER NOT NULL DEFAULT 0") } }
  fun getDatabase():AppDatabase=synchronized(this){INSTANCE!!}
  fun getDatabase(context:Context):AppDatabase{if(INSTANCE==null)synchronized(this){if(INSTANCE==null)INSTANCE=buildDatabase(context)};return INSTANCE!!}
  private fun buildDatabase(context:Context):AppDatabase?{var builder=Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"Mobile-D-Database").addMigrations(MIGRATION_1_2).allowMainThreadQueries().addCallback(object:RoomDatabase.Callback(){
   override fun onCreate(db:SupportSQLiteDatabase){
    super.onCreate(db)
    ensureAllDevicesRow(db)
   }
   override fun onOpen(db:SupportSQLiteDatabase){
    super.onOpen(db)
    ensureAllDevicesRow(db)
   }
   private fun ensureAllDevicesRow(db:SupportSQLiteDatabase){
    // Preserve user groups if an old/broken database contains a non-system
    // group named All Devices. The reserved system group always owns rowId=0.
    db.execSQL("UPDATE HardwareGroup SET groupTitle='Recovered group ' || rowId WHERE rowId<>0 AND lower(groupTitle)='all devices' AND EXISTS(SELECT 1 FROM HardwareGroup WHERE rowId=0)")
    db.execSQL("UPDATE HardwareGroupItem SET groupId=0 WHERE groupId=(SELECT rowId FROM HardwareGroup WHERE rowId<>0 AND lower(groupTitle)='all devices' LIMIT 1) AND NOT EXISTS(SELECT 1 FROM HardwareGroup WHERE rowId=0)")
    db.execSQL("UPDATE HardwareGroup SET rowId=0, all_devices=1 WHERE rowId<>0 AND lower(groupTitle)='all devices' AND NOT EXISTS(SELECT 1 FROM HardwareGroup WHERE rowId=0)")
    db.execSQL("INSERT OR IGNORE INTO HardwareGroup (rowId, groupTitle, all_devices) VALUES (0, 'All Devices', 1)")
    db.execSQL("UPDATE HardwareGroup SET groupTitle='All Devices', all_devices=1 WHERE rowId=0")
   }
  });if(AppConfiguration.DEBUG)builder.setQueryCallback({sqlQuery,bindArgs->LogSystem.e("Database","SQL Query: $sqlQuery SQL Args: $bindArgs")},Executors.newSingleThreadExecutor());return builder.build()}
 }
 open class AutoMigration:AutoMigrationSpec
}
