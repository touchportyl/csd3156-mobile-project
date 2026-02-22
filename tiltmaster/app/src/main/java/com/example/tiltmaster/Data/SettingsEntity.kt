package com.example.tiltmaster.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "settings")
data class SettingsEntity(
    @PrimaryKey val id: Int = 1,            // always 1 row
    val sensitivity: Float = 1.0f,
    val vibrationEnabled: Boolean = true,
    val soundEnabled: Boolean = true
)