package com.example.tiltmaster.ViewModel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiltmaster.data.DatabaseProvider
import com.example.tiltmaster.data.ScoreRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class LevelSelectViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ScoreRepository(DatabaseProvider.get(app).scoreDao())

    // Map list -> Map(levelId -> bestTimeMs) for easy UI lookup
    val bestTimesByLevel = repo.allBestTimes
        .map { list -> list.associate { it.levelId to it.bestTimeMs } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}