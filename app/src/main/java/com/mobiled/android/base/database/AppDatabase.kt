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
  private fun buildDatabase(context:Context):AppDatabase?{var builder=Room.databaseBuilder(context.applicationContext,AppDatabase::class.java,"Mobile-D-Database").addMigrations(MIGRATION_1_2).fallbackToDestructiveMigration().allowMainThreadQueries().addCallback(object:RoomDatabase.Callback(){override fun onCreate(db:SupportSQLiteDatabase){super.onCreate(db);db.delete("HardwareGroup",null,null);db.execSQL("INSERT INTO HardwareGroup VALUES(0,'All Devices',1)")}});if(AppConfiguration.DEBUG)builder.setQueryCallback({sqlQuery,bindArgs->LogSystem.e("Database","SQL Query: $sqlQuery SQL Args: $bindArgs")},Executors.newSingleThreadExecutor());return builder.build()}
 }
 open class AutoMigration:AutoMigrationSpec
}
