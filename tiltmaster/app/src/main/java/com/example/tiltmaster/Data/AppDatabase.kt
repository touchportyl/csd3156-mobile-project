package com.example.tiltmaster.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SettingsEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
}