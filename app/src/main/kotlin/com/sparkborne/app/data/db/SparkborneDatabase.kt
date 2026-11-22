package com.sparkborne.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [DailySnapshotEntity::class, GameStateEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class SparkborneDatabase : RoomDatabase() {

    abstract fun dailySnapshotDao(): DailySnapshotDao
    abstract fun gameStateDao(): GameStateDao

    companion object {
        @Volatile
        private var INSTANCE: SparkborneDatabase? = null

        fun getInstance(context: Context): SparkborneDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SparkborneDatabase::class.java,
                    "sparkborne.db",
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
