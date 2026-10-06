package com.mobiled.android.base.database

import android.content.Context
import com.mobiled.android.LogSystem
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mobiled.android.base.AppConfiguration
import com.mobiled.android.base.model.HardwareDevice
import com.mobiled.android.base.model.HardwareGroup
import com.mobiled.android.base.model.HardwareGroupItem
import java.util.concurrent.Executors

@Database(
    entities = [HardwareDevice::class, HardwareGroupItem::class, HardwareGroup::class],
    version = 1,
    exportSchema = true,
    autoMigrations = [

    ]
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun getHardwareTable(): HardwareTableDAO
    abstract fun getGroupTable(): GroupTableDAO

    companion object {
        @Volatile      // used for updated value to all threats
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(): AppDatabase {
            synchronized(this)
            {
                return INSTANCE!!
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            if (INSTANCE == null) {
                //synchronized used for removing multiple instance when we used multiple thread
                synchronized(this) {
                    INSTANCE = buildDatabase(context)
                }
            }
            return INSTANCE!!
        }

        private fun buildDatabase(context: Context): AppDatabase? {
            var builder = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "Mobile-D-Database"
            )
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        db.delete("HardwareGroup",null,null)
                        db.execSQL("INSERT INTO HardwareGroup VALUES(0,'All Devices',1)")
                    }
                })

            if (AppConfiguration.DEBUG) {
                builder.setQueryCallback({ sqlQuery, bindArgs ->
                    LogSystem.e("Database", "SQL Query: $sqlQuery SQL Args: $bindArgs")
                }, Executors.newSingleThreadExecutor())
            }
            return builder.build()
        }

    }

    open class AutoMigration : AutoMigrationSpec

}