package com.example.tiltmaster.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {

    // For LevelSelect: show all best times
    @Query("SELECT * FROM level_scores")
    fun observeAllBestTimes(): Flow<List<ScoreEntity>>

    // Get a single level’s best time
    @Query("SELECT bestTimeMs FROM level_scores WHERE levelId = :levelId LIMIT 1")
    suspend fun getBestTimeMs(levelId: Int): Long?

    // Insert/replace row (we will only replace when the new time is better)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(score: ScoreEntity)
}