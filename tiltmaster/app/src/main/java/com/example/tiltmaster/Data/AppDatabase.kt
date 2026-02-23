package com.example.tiltmaster.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.tiltmaster.data.ScoreDao
import com.example.tiltmaster.data.ScoreEntity

@Database(
    entities = [SettingsEntity::class, ScoreEntity::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun settingsDao(): SettingsDao
    abstract fun scoreDao(): ScoreDao
}