package com.hackathon.powergaurd.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hackathon.powergaurd.data.local.dao.DeviceInsightDao
import com.hackathon.powergaurd.data.local.entity.DeviceInsightEntity
import com.hackathon.powergaurd.data.local.dao.DeviceActionableDao
import com.hackathon.powergaurd.data.local.entity.DeviceActionableEntity

/**
 * Room database for storing PowerGuard data
 */
@Database(
    entities = [DeviceInsightEntity::class, DeviceActionableEntity::class],
    version = 3,
    exportSchema = false
)
abstract class PowerGuardDatabase : RoomDatabase() {

    /**
     * Provides access to the DeviceInsightDao
     */
    abstract fun deviceInsightDao(): DeviceInsightDao
    
    /**
     * Provides access to the DeviceActionableDao
     */
    abstract fun deviceActionableDao(): DeviceActionableDao

    companion object {
        private const val DATABASE_NAME = "powerguard_database"

        @Volatile
        private var INSTANCE: PowerGuardDatabase? = null

        /**
         * Get the database instance, creating it if it doesn't exist
         *
         * @param context Application context
         * @return PowerGuardDatabase instance
         */
        fun getInstance(context: Context): PowerGuardDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    PowerGuardDatabase::class.java,
                    DATABASE_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}