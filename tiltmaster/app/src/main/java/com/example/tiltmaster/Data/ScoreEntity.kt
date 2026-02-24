package com.example.tiltmaster.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "level_scores")
data class ScoreEntity(
    @PrimaryKey val levelId: Int,           // one row per level
    val bestTimeMs: Long                    // lowest time wins
)