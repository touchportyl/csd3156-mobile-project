package com.example.tiltmaster.data

import kotlinx.coroutines.flow.Flow

class ScoreRepository(private val dao: ScoreDao) {

    val allBestTimes: Flow<List<ScoreEntity>> = dao.observeAllBestTimes()

    suspend fun submitTime(levelId: Int, timeMs: Long) {
        val currentBest = dao.getBestTimeMs(levelId)
        if (currentBest == null || timeMs < currentBest) {
            dao.upsert(ScoreEntity(levelId = levelId, bestTimeMs = timeMs))
        }
    }
}