package com.hackathon.powerguard.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hackathon.powerguard.data.local.dao.DeviceInsightDao
import com.hackathon.powerguard.data.local.entity.Converters
import com.hackathon.powerguard.data.local.entity.DeviceInsightEntity

/**
 * Room database for PowerGuard app
 */
@Database(
    entities = [DeviceInsightEntity::class],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class PowerGuardDatabase : RoomDatabase() {

    /**
     * Get the DAO for device insights
     */
    abstract fun deviceInsightDao(): DeviceInsightDao

    companion object {
        private const val DATABASE_NAME = "powerguard_database"

        @Volatile
        private var INSTANCE: PowerGuardDatabase? = null

        /**
         * Get the database instance
         *
         * @param context The application context
         * @return The database instance
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