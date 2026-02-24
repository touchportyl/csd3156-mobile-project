package com.example.tiltmaster.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tiltmaster.data.DatabaseProvider
import com.example.tiltmaster.data.ScoreRepository
import kotlinx.coroutines.launch

class GameViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = ScoreRepository(DatabaseProvider.get(app).scoreDao())

    fun submitBestTime(levelId: Int, timeMs: Long) {
        viewModelScope.launch {
            repo.submitTime(levelId, timeMs)
        }
    }
}